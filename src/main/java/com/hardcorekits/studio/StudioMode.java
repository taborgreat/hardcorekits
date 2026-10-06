package com.hardcorekits.studio;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Studio mode: the plugin running on the film studio's private server instead of the live one.
 *
 * <p>The studio (mcmovie) replays a recorded match with one bot per player and films it. In
 * this mode there is no match at all (no lobby, countdown, world rotation, border, feast,
 * stats, status server or recorder); only the kits and their listeners are loaded, with every
 * ability gate open, and {@code /studio} makes a bot's kit fire at the moment the recording
 * says it did. See {@link StudioCommand} and {@link StudioGuard}.
 *
 * <p>Off unless the data folder's own config.yml says {@code studio.enabled: true} AND the
 * server is in offline mode. The live server is neither: its config.yml is the jar's copy
 * (false), and it authenticates its players. Both flags here stay false there for the life of
 * the process, so the handful of {@code StudioMode} checks in the game code are dead branches.
 */
public final class StudioMode {

    private static volatile boolean enabled;
    /** True only while {@code /studio fire} is running a kit, on the main thread. */
    private static boolean firing;

    private StudioMode() {
    }

    /** Whether this server booted as a studio. Never true on the live server. */
    public static boolean enabled() {
        return enabled;
    }

    /**
     * Whether {@code /studio fire} is triggering an ability right now. Kit cooldowns stand
     * aside for it: the studio films scenes out of order, so a cooldown left over from another
     * scene must not swallow a use the recording says happened.
     */
    public static boolean firing() {
        return firing;
    }

    static void firing(boolean now) {
        firing = now;
    }

    /**
     * Decides, before anything else at boot, whether this is a studio.
     *
     * <p>Read straight from the file on disk, because the normal boot overwrites that file
     * with the jar's copy as its first act. A studio keeps its own file instead.
     */
    public static boolean requested(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) {
            return false;
        }
        boolean asked;
        try {
            asked = YamlConfiguration.loadConfiguration(file).getBoolean("studio.enabled", false);
        } catch (RuntimeException e) {
            return false;
        }
        if (!asked) {
            return false;
        }
        if (Bukkit.getOnlineMode()) {
            plugin.getLogger().severe("config.yml says studio.enabled: true, but this server is "
                    + "in online mode. Studio mode is for the offline film studio only; ignoring "
                    + "it and starting the normal game.");
            return false;
        }
        return true;
    }

    /** Called once by the plugin when it boots as a studio. */
    public static void activate() {
        enabled = true;
    }
}
