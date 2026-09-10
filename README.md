# Hardcore Games

The Paper plugin behind [hardcorekits.com](https://hardcorekits.com) — classic MCPVP-style
Hardcore Games. One natural world, no lobby, no GUI. Pick a kit with `/kit`, drop near the
middle of a 1000x1000 map, last one standing wins.

Combat is pre-1.8 and mushroom soup is the heal, so fights come down to how fast you click
through a stack of bowls. Kits are real abilities, not loadouts. A feast drops mid-match
with the only diamond gear on the map. No Nether, no End.

## Running it

Needs JDK 25 (Paper 26.2 won't run on older). Gradle comes with the wrapper.

```bash
./gradlew build       # -> build/libs/hardcoregames-0.1.0.jar
./gradlew runServer   # boots a test server on localhost:25565
```

A match ends by shutting the server down: everyone is kicked, the map is retired, and the
next game gets a fresh world. `tools/run-loop.sh` cycles that forever — it reinstalls the
built jar between games and stops when you `touch run/stop-loop.txt`.

For a real server, drop the jar into `plugins/`. Everything tunable is in `config.yml`.
`/hg start`, `/hg state` and `/hg fake` let you test a match alone; `tools/bots` connects
headless clients when you need bodies to hit. The site in `http/` is a dependency-free Node
app that polls the plugin's local stats API.

## Hosting

Ports: 25565/tcp Java, 19132/udp Bedrock (Geyser), 8080/tcp website (reverse-proxy 80/443
onto it). The plugin's stats API on 8085 binds loopback and should stay that way.

Bedrock needs Geyser + Floodgate jars in the server's `plugins/` — they're gitignored, so
grab them from download.geysermc.org on a new machine and set `auth-type: floodgate` in
Geyser's config. Owners are ops: `/op` from the console, everything else is delegated
in-game through `/mods`.

Two gotchas: don't rebuild while `runServer` is up (it loads the jar straight out of
`build/libs`, and swapping it mid-flight throws `NoClassDefFoundError`), and `runServer`
opens a JDWP debug port on 5005, so firewall it anywhere public.
