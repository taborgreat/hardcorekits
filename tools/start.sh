#!/bin/sh
# Boots the whole thing as one tmux window called "hg" in your default tmux session:
#
#   left pane   the website (http/server.js on 127.0.0.1:8080, restarted if it dies)
#   right pane  the Minecraft console (tools/run-loop.sh cycling Paper in ~/hgserver)
#
# If a tmux session already exists the window is added to it (the attached one, else the
# first), so it shows up next to whatever else you have open. If tmux is not running yet
# (fresh boot) a session is created for it -- then a plain `tmux attach` lands on it.
#
#   tools/start.sh        # start (no-op if the window exists)
#   tmux attach           # look at it; Ctrl-b d to detach, Ctrl-b arrow to switch panes
#   tools/stop.sh         # clean shutdown of both, closes the window
#
# A user crontab @reboot line runs this at boot. Panes stay open after their process exits
# (remain-on-exit) so a crash is readable; tools/stop.sh then tools/start.sh brings it back.
set -eu

repo=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
server_dir="${HG_SERVER_DIR:-$HOME/hgserver}"
java_bin="${JAVA:-$HOME/.jdks/jdk-25.0.4.1+1/bin/java}"
node_bin="${NODE:-$(ls -d "$HOME"/.nvm/versions/node/*/bin/node 2>/dev/null | tail -n 1)}"
tmux=/usr/bin/tmux
window=hg

[ -x "$java_bin" ] || { echo "start: no java at $java_bin" >&2; exit 1; }
[ -x "$node_bin" ] || { echo "start: no node found (nvm)" >&2; exit 1; }
[ -f "$server_dir/paper.jar" ] || { echo "start: no paper.jar in $server_dir" >&2; exit 1; }

# Window "hg" anywhere on the tmux server -> already running.
find_window() {
    $tmux list-windows -a -F '#{session_name}:#{window_index} #{window_name}' 2>/dev/null \
        | awk -v w="$window" '$2 == w { print $1; exit }'
}

if existing=$(find_window) && [ -n "$existing" ]; then
    echo "already running in tmux window $existing"
    exit 0
fi

web="while :; do PORT=8080 MC_API=http://127.0.0.1:8085 \
STATS_JSON='$server_dir/plugins/HardcoreGames/stats.json' '$node_bin' server.js; \
echo '[hg-web] exited, restarting in 3s'; sleep 3; done"

game="'$repo/tools/run-loop.sh' --server-dir '$server_dir' --java '$java_bin' --jvm-args '-Xms2G -Xmx4G'"

if $tmux list-sessions >/dev/null 2>&1; then
    # Prefer the session you are looking at; otherwise the first one.
    session=$($tmux list-sessions -F '#{session_attached} #{session_name}' \
        | sort -rn | head -n 1 | cut -d' ' -f2-)
    $tmux new-window -d -t "$session:" -n "$window" -c "$repo/http" "$web"
else
    $tmux new-session -d -n "$window" -x 220 -y 50 -c "$repo/http" "$web"
fi

target=$(find_window)
$tmux set-option -w -t "$target" remain-on-exit on
$tmux split-window -h -l 65% -t "$target" -c "$server_dir" "$game"
$tmux select-pane -t "$target.1"
echo "started in tmux window $target (tmux attach)"
