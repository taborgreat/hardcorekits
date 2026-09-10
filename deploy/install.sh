#!/bin/sh
# Root-level half of hosting setup: nginx site, TLS cert, firewall. Safe to re-run.
#
#   sudo deploy/install.sh
#
# The site and the game server themselves are NOT services: tools/start.sh runs both in a
# tmux session ("hg") and the user crontab's @reboot line starts it at boot. Nothing here
# touches that.
#
# Prerequisite: DNS for hardcorekits.com and www.hardcorekits.com must already point at
# this machine's public IP, or the certbot step fails (re-run the script once it does).
set -eu
cd "$(dirname "$0")/.."
[ "$(id -u)" -eq 0 ] || { echo "run with sudo" >&2; exit 1; }

echo "== nginx =="
if [ ! -f /etc/nginx/sites-available/hardcorekits.com ]; then
    cp deploy/nginx-hardcorekits.com.conf /etc/nginx/sites-available/hardcorekits.com
fi
ln -sf /etc/nginx/sites-available/hardcorekits.com /etc/nginx/sites-enabled/hardcorekits.com
nginx -t && systemctl reload nginx

echo "== firewall =="
if command -v ufw >/dev/null && ufw status | grep -q '^Status: active'; then
    ufw allow 25565/tcp comment 'Minecraft Java'
    ufw allow 19132/udp comment 'Minecraft Bedrock (Geyser)'
else
    echo "ufw not active; nothing to open"
fi

echo "== TLS =="
if [ -d /etc/letsencrypt/live/hardcorekits.com ]; then
    echo "cert already present; renewal is handled by certbot's timer"
else
    certbot --nginx -d hardcorekits.com -d www.hardcorekits.com --redirect \
        --non-interactive --agree-tos --register-unsafely-without-email \
    || echo "certbot failed: DNS probably not pointing here yet. Re-run this script later."
fi

echo
echo "done. checks:"
echo "  curl -sI https://hardcorekits.com | head -1"
echo "  tmux attach            # website (left) + game console (right)"
