#!/bin/sh
# Root-level half of hosting setup: nginx sites, TLS certs, firewall. Safe to re-run.
#
#   sudo deploy/install.sh
#
# The site and the game server themselves are NOT services: tools/start.sh runs both in a
# tmux window ("hg") and the user crontab's @reboot line starts it at boot. Nothing here
# touches that.
#
# Domains: hardcorepvp.com is the main site. hardcorekits.com is kept and 301s to it. Both
# must already point at this machine's public IP (A records for the apex and www), or the
# certbot step for that domain fails; re-run the script once they do.
set -eu
cd "$(dirname "$0")/.."
[ "$(id -u)" -eq 0 ] || { echo "run with sudo" >&2; exit 1; }

MAIN=hardcorepvp.com
OLD=hardcorekits.com

echo "== nginx: $MAIN =="
if [ ! -f /etc/nginx/sites-available/$MAIN ]; then
    cp deploy/nginx-$MAIN.conf /etc/nginx/sites-available/$MAIN
fi
ln -sf /etc/nginx/sites-available/$MAIN /etc/nginx/sites-enabled/$MAIN
if [ ! -f /etc/nginx/sites-available/$OLD ]; then
    cp deploy/nginx-$OLD.conf /etc/nginx/sites-available/$OLD
fi
ln -sf /etc/nginx/sites-available/$OLD /etc/nginx/sites-enabled/$OLD
nginx -t && systemctl reload nginx

echo "== firewall =="
if command -v ufw >/dev/null && ufw status | grep -q '^Status: active'; then
    ufw allow 25565/tcp comment 'Minecraft Java'
    ufw allow 19132/udp comment 'Minecraft Bedrock (Geyser)'
else
    echo "ufw not active; nothing to open"
fi

echo "== TLS =="
for d in $MAIN $OLD; do
    if [ -d /etc/letsencrypt/live/$d ]; then
        echo "$d: cert already present; renewal is handled by certbot's timer"
    else
        certbot --nginx -d $d -d www.$d --redirect \
            --non-interactive --agree-tos --register-unsafely-without-email \
        || echo "$d: certbot failed. DNS probably not pointing here yet; re-run later."
    fi
done

echo "== $OLD -> $MAIN redirect =="
# Turn the old domain's TLS server block into a 301 to the main domain. certbot manages
# that file, so this edits it in place: the proxy location becomes a return. Idempotent.
OLDCONF=/etc/nginx/sites-available/$OLD
if [ -d /etc/letsencrypt/live/$OLD ] && grep -q 'proxy_pass http://127.0.0.1:8080;' $OLDCONF; then
    python3 - "$OLDCONF" "$MAIN" <<'PY'
import re, sys
path, main = sys.argv[1], sys.argv[2]
s = open(path).read()
s = re.sub(r'    location / \{.*?\n    \}\n',
           f'    location / {{\n        return 301 https://{main}$request_uri;\n    }}\n',
           s, count=1, flags=re.S)
open(path, 'w').write(s)
PY
    nginx -t && systemctl reload nginx
    echo "redirect installed"
else
    echo "already a redirect, or no cert for $OLD yet"
fi

echo
echo "done. checks:"
echo "  curl -sI https://$MAIN | head -1          # 200"
echo "  curl -sI https://$OLD | head -3           # 301 -> https://$MAIN"
echo "  tmux attach            # website (left) + game console (right)"
