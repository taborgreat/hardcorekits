package gg.hungergames.world;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The no-oceans datapack.
 *
 * <p>Terrain height comes from the noise router, so no plugin setting and no biome trick can
 * remove ocean at runtime — the world has to <em>generate</em> without it. Since 26.x the
 * overworld's {@code continents} density function is data-driven, which gives us one small,
 * surgical override: clamp its floor to just above the coastline band, and terrain that would
 * have been sea floor comes out as ordinary land instead. Everything else — swamps, taigas,
 * mountains, forests, jungles, deserts, meadows — is untouched vanilla, which now has the whole
 * map to happen on instead of the third of it that used to poke out of the water.
 *
 * <p>A world only reads {@code datapacks/} as it is created, so installation happens in
 * {@link WorldRotator#rotate} — the pack is placed into the <em>next</em> world's folder before
 * that world exists as anything but a name. Installing into a live world's folder does nothing
 * until the world is regenerated, which is why this class is not called at enable: with
 * rotation on, every world after the first is born with the pack, and the first is disposable.
 */
public final class WorldgenPack {

    /** Folder name under {@code <world>/datapacks/}. */
    private static final String PACK_NAME = "hg_worldgen";

    /** Bundled resource -> where it lives inside the pack. */
    private static final String[][] FILES = {
            {"worldgen/pack.mcmeta", "pack.mcmeta"},
            {"worldgen/continents.json",
                    "data/minecraft/worldgen/density_function/overworld/continents.json"},
    };

    private WorldgenPack() {
    }

    /**
     * Copies the pack into {@code <worldFolder>/datapacks/}, creating the folders on the way.
     * Idempotent, and always overwrites — the pack's contents belong to this build of the
     * plugin, not to whichever build first created the world folder.
     *
     * @return true if the pack is fully in place
     */
    public static boolean install(Plugin plugin, Path worldFolder) {
        Path packRoot = worldFolder.resolve("datapacks").resolve(PACK_NAME);
        try {
            for (String[] file : FILES) {
                Path target = packRoot.resolve(file[1]);
                Files.createDirectories(target.getParent());
                try (InputStream in = plugin.getResource(file[0])) {
                    if (in == null) {
                        plugin.getLogger().warning("Worldgen pack resource missing from the jar: "
                                + file[0]);
                        return false;
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not install the worldgen pack into "
                    + worldFolder + ": " + e.getMessage());
            return false;
        }
    }
}
