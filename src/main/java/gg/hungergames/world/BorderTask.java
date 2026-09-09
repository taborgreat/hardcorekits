package gg.hungergames.world;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The map boundary. There is no wall and no vanilla world border — step past the edge and you
 * start taking damage until you turn around or die.
 *
 * <p>The damage is graduated on purpose. The first stretch outside is a warning you can walk
 * back from: half a heart a second, enough to notice and turn around without having lost the
 * fight you were in. Past that band it climbs steeply with every further block, so the edge
 * still ends anyone who treats it as somewhere to live.
 *
 * <p>The vanilla {@code WorldBorder} is deliberately left at its maximum size so the client
 * never renders the red barrier; the boundary is enforced entirely here.
 */
public final class BorderTask implements Runnable {

    private final HungerGames plugin;
    private final GameManager game;

    public BorderTask(HungerGames plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    public void start() {
        long interval = game.config().borderDamageIntervalTicks();
        Bukkit.getScheduler().runTaskTimer(plugin, this, interval, interval);
    }

    @Override
    public void run() {
        if (!game.state().isLive()) {
            return; // pre-game players are free to roam the whole map
        }
        double limit = game.config().borderSize() / 2.0D;
        int centerX = game.config().centerX();
        int centerZ = game.config().centerZ();
        // This runs on an interval, so per-second rates have to be scaled to one application.
        double secondsPerTick = game.config().borderDamageIntervalTicks() / 20.0D;

        for (Player player : game.alivePlayers()) {
            Location location = player.getLocation();
            double outsideX = Math.abs(location.getX() - centerX) - limit;
            double outsideZ = Math.abs(location.getZ() - centerZ) - limit;
            double outside = Math.max(outsideX, outsideZ);

            if (outside <= 0.0D) {
                continue;
            }

            double perSecond = damagePerSecond(outside);
            boolean inGrace = outside <= game.config().borderGraceDistance();

            // Action bar rather than chat — this fires twice a second and would flood it.
            player.sendActionBar(Component.text(
                    (inGrace ? "⚠ Turn back, outside the map (" : "☠ TOO FAR OUT (")
                            + (int) outside + " blocks)",
                    inGrace ? NamedTextColor.GOLD : NamedTextColor.RED));
            player.damage(perSecond * secondsPerTick);
        }
    }

    /**
     * Flat inside the grace band, then climbing with every block past it.
     *
     * <p>Linear rather than a cliff, so there is no single step where survivable becomes
     * instantly fatal — you feel it getting worse as you go, which is the point of a warning.
     */
    private double damagePerSecond(double outside) {
        double grace = game.config().borderGraceDistance();
        double gentle = game.config().borderGraceDamagePerSecond();
        if (outside <= grace) {
            return gentle;
        }
        return gentle + ((outside - grace) * game.config().borderRampPerBlock());
    }
}
