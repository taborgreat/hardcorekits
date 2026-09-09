package gg.hungergames.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Gives every restart a brand-new map.
 *
 * <p>A running server cannot delete the world it is standing in, and a {@code plugin.yml}
 * plugin only wakes up long after the world has loaded — so the work is split across the
 * restart. On the way down {@link #rotate(Plugin)} points {@code server.properties} at a fresh
 * world name and blanks the seed, so the next boot generates new terrain rather than reloading
 * the old save. On the way up {@link #purgePrevious(Plugin)} deletes the world that was left
 * behind, which is safe precisely because the server is now living somewhere else.
 *
 * <p>Nothing here touches the world the server is currently using.
 */
public final class WorldRotator {

    /** Always in the working directory, next to the server jar. */
    private static final String PROPERTIES_FILE = "server.properties";
    /** Written on the way down, read and obeyed on the way up. */
    private static final String MARKER_FILE = "retired-world.txt";

    private static final String LEVEL_NAME = "level-name=";
    private static final String LEVEL_SEED = "level-seed=";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** A world folder brings its dimensions along. */
    private static final String[] DIMENSIONS = {"", "_nether", "_the_end"};

    private WorldRotator() {
    }

    /** Deletes the world the last shutdown rotated away from. No-op on a normal boot. */
    public static void purgePrevious(Plugin plugin) {
        File marker = new File(plugin.getDataFolder(), MARKER_FILE);
        if (!marker.isFile()) {
            return;
        }

        String retired = read(plugin, marker);
        marker.delete();
        if (retired == null || retired.isBlank()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equals(retired)) {
                plugin.getLogger().warning("Not deleting '" + retired
                        + "' — the server booted straight back into it.");
                return;
            }
        }

        File container = Bukkit.getWorldContainer();
        for (String dimension : DIMENSIONS) {
            File directory = new File(container, retired + dimension);
            if (!directory.isDirectory()) {
                continue;
            }
            if (deleteRecursively(directory)) {
                plugin.getLogger().info("Deleted last game's world '" + directory.getName() + "'.");
            } else {
                plugin.getLogger().warning("Could not fully delete '" + directory.getName()
                        + "' — remove it by hand.");
            }
        }
    }

    /**
     * Renames the world the next boot will use and blanks the seed, so the map is new terrain
     * and the current one becomes disposable.
     */
    public static void rotate(Plugin plugin, boolean noOceans) {
        Path properties = new File(PROPERTIES_FILE).toPath();
        if (!Files.isRegularFile(properties)) {
            plugin.getLogger().warning("No " + PROPERTIES_FILE
                    + " in the working directory — the next boot will reuse this world.");
            return;
        }

        String fresh = "hg-" + LocalDateTime.now().format(STAMP);
        try {
            List<String> lines = Files.readAllLines(properties, StandardCharsets.UTF_8);
            boolean named = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith(LEVEL_NAME)) {
                    lines.set(i, LEVEL_NAME + fresh);
                    named = true;
                } else if (line.startsWith(LEVEL_SEED)) {
                    lines.set(i, LEVEL_SEED); // blank -> a new random seed on generation
                }
            }
            if (!named) {
                lines.add(LEVEL_NAME + fresh);
            }
            Files.write(properties, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not rewrite " + PROPERTIES_FILE
                    + " — the next boot will reuse this world: " + e.getMessage());
            return;
        }

        // The next world does not exist yet, but its datapacks folder can — and a world reads
        // datapacks as it is created, so this is the one moment the pack can reach worldgen.
        if (noOceans) {
            WorldgenPack.install(plugin, Bukkit.getWorldContainer().toPath().resolve(fresh));
        }

        String retiring = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getName();
        remember(plugin, retiring);
        plugin.getLogger().info("Next boot generates '" + fresh + "'"
                + (noOceans ? " (no-oceans worldgen)" : "")
                + (retiring == null ? "." : " and deletes '" + retiring + "'."));
    }

    // ---------------------------------------------------------------- the marker file

    private static void remember(Plugin plugin, String worldName) {
        if (worldName == null) {
            return;
        }
        File folder = plugin.getDataFolder();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + folder + " — the old world will stay.");
            return;
        }
        try {
            Files.writeString(new File(folder, MARKER_FILE).toPath(), worldName, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not record the retired world: " + e.getMessage());
        }
    }

    private static String read(Plugin plugin, File marker) {
        try {
            return Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read " + MARKER_FILE + ": " + e.getMessage());
            return null;
        }
    }

    private static boolean deleteRecursively(File file) {
        File[] children = file.listFiles();
        boolean deleted = true;
        if (children != null) {
            for (File child : children) {
                deleted &= deleteRecursively(child);
            }
        }
        return file.delete() && deleted;
    }
}
