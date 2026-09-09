# mc-hungergames

A Minecraft Server built off the Paper plugin that runs classic MCPVP-style Hunger Games: one natural world, no lobby, no
GUI, everything driven by commands. Players pick a kit with `/kit`, get dropped near the
middle of a 1000x1000 map, and the last one standing wins.

It's built after the old mc-hg.com servers, so combat is pre-1.8 and mushroom soup is the heal
fights come down to how fast you click through a stack of bowls, not how well you time a
cooldown. There's a long list of kits with real abilities rather than just loadouts, and a
feast drops in mid-match holding the only diamond gear on the map, which pulls whoever is
left back into one place. Bedrock at y=0. No Nether or The End. The way it should be.

Needs JDK 25 as Paper 26.2 won't run on anything older. Gradle comes with the wrapper, so
that's the only thing to install.

```bash
./gradlew build       # -> build/libs/hungergames-0.1.0.jar
./gradlew runServer   # downloads Paper, boots a test server on localhost:25565
```

To run it for real, drop the jar into a Paper server's `plugins/`. Everything tunable lives in
`config.yml`; `/hgstart`, `/hgstate` and `/hgfake` exist for testing a match on your own, and
`tools/bots` connects headless clients when you need bodies to hit.

Two things to know. Don't rebuild while `runServer` is up — it loads the jar straight out of
`build/libs`, and swapping it mid-flight throws `NoClassDefFoundError` that looks like a plugin
bug but isn't. And `runServer` opens a JDWP debug port on 5005 on every interface, so firewall
it or bind it to localhost before running this anywhere public.
