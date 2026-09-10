# Hardcore Games

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

### Production layout (this box)

Nothing is a systemd service. `tools/start.sh` opens one tmux window named `hg` in your
default session, website in the left pane and Minecraft console in the right; the user
crontab's `@reboot` line runs it at boot, so after a restart `tmux attach` is all it takes. `tools/stop.sh` shuts both
down cleanly (world saved, loop ended). `deploy/` holds the root half, nginx + cert +
firewall, applied once with `sudo deploy/install.sh` (safe to re-run).

- Game server lives in `~/hgserver` (Paper + Geyser + Floodgate + the built plugin), cycled by
  `tools/run-loop.sh`, which reinstalls `build/libs/*.jar` between maps, so `./gradlew build`
  ships at the next game with no restart. Don't `./gradlew runServer` while it's up: both
  want 25565.
- Website is `http/server.js` on 127.0.0.1:8080 reading stats from `~/hgserver`, fronted by
  nginx (`deploy/nginx-hardcorekits.com.conf`) with a certbot-managed cert.
- JDK 25 is a user-level Temurin tarball in `~/.jdks`; `JAVA_HOME` is set in `~/.bashrc` and
  `~/.gradle/gradle.properties` points the toolchain at it. Nothing Java was apt-installed.
- DNS: `A hardcorekits.com` and `A www` both to this machine's public IP. 25565 is the
  default Java port so no SRV record is needed; Bedrock players enter port 19132 by hand.
  The router must forward 80/tcp, 443/tcp, 25565/tcp and 19132/udp here.

Two gotchas: don't rebuild while `runServer` is up (it loads the jar straight out of
`build/libs`, and swapping it mid-flight throws `NoClassDefFoundError`), and `runServer`
opens a JDWP debug port on 5005, so firewall it anywhere public.
