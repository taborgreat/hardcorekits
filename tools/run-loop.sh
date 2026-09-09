#!/bin/sh
#
# Keeps one Hunger Games server cycling: boot, play, close, boot again on a fresh map.
#
# The plugin ends every match by shutting the server down. That is deliberate -- it kicks
# everyone, so getting into the next game is a race rather than a thing the last winner is
# already inside. Nothing within a JVM can relaunch that JVM, so this script is the piece that
# closes the loop: run the server, wait for it to exit, run it again.
#
# Three things happen between runs, all of them only safe while the server is down:
#
#   * The freshly built plugin jar is installed. Building on top of a running server swaps the
#     jar out from under it, and the next class it loads lazily dies with NoClassDefFoundError;
#     between boots is the one safe moment.
#   * A crashed run is cleaned up after. On a clean shutdown the plugin retires its own map and
#     leaves a marker naming the world it walked away from. No marker means the last run never
#     got that far, so the map it was playing is still current and would be played again --
#     that world is deleted here and the seed blanked, so the next boot generates new terrain.
#     Skipped when fresh-world-on-restart is off, since then the map is being kept on purpose.
#   * A stop file is honoured, so the loop can be ended between games rather than by killing a
#     server mid-match.
#
# POSIX sh: runs on Linux, macOS, and on Windows under Git Bash.
#
# Usage:
#   tools/run-loop.sh [--server-dir DIR] [--jar paper.jar] [--plugin-jar PATH]
#                     [--java java] [--jvm-args "-Xmx2G"] [--restart-delay 3] [--max-cycles 0]
#
#   # end the loop after the current game finishes
#   touch run/stop-loop.txt
#
# Do not point this at a directory that already has a server running in it: it deletes world
# folders between boots, which is only safe when nothing is holding them open.

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

server_dir="$script_dir/../run"
jar="paper.jar"
plugin_jar="$script_dir/../build/libs/hungergames-0.1.0.jar"
plugin_name="HungerGames.jar"
java_bin="${JAVA:-java}"
jvm_args="-Xmx2G"
restart_delay=3
max_cycles=0

while [ $# -gt 0 ]; do
    case "$1" in
        --server-dir)    server_dir=$2;    shift 2 ;;
        --jar)           jar=$2;           shift 2 ;;
        --plugin-jar)    plugin_jar=$2;    shift 2 ;;
        --plugin-name)   plugin_name=$2;   shift 2 ;;
        --java)          java_bin=$2;      shift 2 ;;
        --jvm-args)      jvm_args=$2;      shift 2 ;;
        --restart-delay) restart_delay=$2; shift 2 ;;
        --max-cycles)    max_cycles=$2;    shift 2 ;;
        -h|--help)       sed -n '2,32p' "$0"; exit 0 ;;
        *) echo "run-loop: unknown option '$1'" >&2; exit 2 ;;
    esac
done

log() {
    printf '[run-loop] %s  %s\n' "$(date '+%H:%M:%S')" "$1"
}

server_dir=$(CDPATH= cd -- "$server_dir" && pwd)
properties="$server_dir/server.properties"
plugin_config="$server_dir/plugins/HungerGames/config.yml"
marker="$server_dir/plugins/HungerGames/retired-world.txt"
stop_file="$server_dir/stop-loop.txt"

[ -f "$server_dir/$jar" ] || {
    echo "run-loop: no $jar in $server_dir -- install Paper there first." >&2
    exit 1
}

# Read fresh every cycle: the plugin rewrites level-name as it shuts down.
level_name() {
    if [ -f "$properties" ]; then
        name=$(sed -n 's/^level-name=//p' "$properties" | tr -d '\r' | head -n 1)
        [ -n "$name" ] && { printf '%s\n' "$name"; return; }
    fi
    printf 'world\n'
}

# Blank the seed so a regenerated world of the same name is not the same terrain.
clear_level_seed() {
    [ -f "$properties" ] || return 0
    tmp="$properties.run-loop.tmp"
    sed 's/^level-seed=.*/level-seed=/' "$properties" > "$tmp" && mv "$tmp" "$properties"
}

# If the plugin is not retiring maps, neither do we -- the map is being kept on purpose.
fresh_world_wanted() {
    [ -f "$plugin_config" ] || return 0
    value=$(sed -n 's/^[[:space:]]*fresh-world-on-restart[[:space:]]*:[[:space:]]*//p' "$plugin_config" \
        | tr -d '\r' | head -n 1)
    [ -z "$value" ] && return 0
    [ "$value" = "true" ]
}

remove_world() {
    for suffix in "" "_nether" "_the_end"; do
        folder="$server_dir/$1$suffix"
        if [ -d "$folder" ]; then
            rm -rf "$folder"
            log "deleted orphaned world '$1$suffix'"
        fi
    done
}

cycle=0
log "server: $server_dir"
log "stop with: touch '$stop_file'"

while :; do
    if [ -f "$stop_file" ]; then
        rm -f "$stop_file"
        log "stop file found - loop finished."
        break
    fi
    if [ "$max_cycles" -gt 0 ] && [ "$cycle" -ge "$max_cycles" ]; then
        log "reached max-cycles ($max_cycles) - loop finished."
        break
    fi

    if [ -f "$plugin_jar" ]; then
        cp -f "$plugin_jar" "$server_dir/plugins/$plugin_name"
        log "installed $(basename "$plugin_jar")"
    fi

    # A marker means the last shutdown was clean and the plugin retired its own map. Its
    # absence means the run died before it could, and the world it was on is still current.
    if fresh_world_wanted && [ ! -f "$marker" ]; then
        level=$(level_name)
        if [ -d "$server_dir/$level" ]; then
            log "last run did not shut down cleanly - retiring '$level' by hand"
            remove_world "$level"
            clear_level_seed
        fi
    fi

    cycle=$((cycle + 1))
    log "starting server (cycle $cycle) on map '$(level_name)'"

    # The server owns the terminal while it runs, so its console reads from this stdin.
    exit_code=0
    ( cd "$server_dir" && "$java_bin" $jvm_args -jar "$jar" nogui ) || exit_code=$?

    log "server exited (code $exit_code) - restarting in ${restart_delay}s"
    sleep "$restart_delay"
done
