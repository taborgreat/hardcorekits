#!/bin/sh
# Clean shutdown of the "hg" tmux window started by tools/start.sh: tells the run-loop not
# to relaunch, sends /stop to the Minecraft console so the world saves, waits for the JVM,
# then closes the window (which ends the website too). The rest of your tmux is untouched.
set -eu
server_dir="${HG_SERVER_DIR:-$HOME/hgserver}"
tmux=/usr/bin/tmux
window=hg

target=$($tmux list-windows -a -F '#{session_name}:#{window_index} #{window_name}' 2>/dev/null \
    | awk -v w="$window" '$2 == w { print $1; exit }')
[ -n "$target" ] || { echo "not running"; exit 0; }

touch "$server_dir/stop-loop.txt"
$tmux send-keys -t "$target.1" stop Enter
i=0
while pgrep -u "$(id -u)" -f 'paper\.jar nogui' >/dev/null && [ $i -lt 120 ]; do
    sleep 1; i=$((i + 1))
done
rm -f "$server_dir/stop-loop.txt"
$tmux kill-window -t "$target"
echo "stopped"
