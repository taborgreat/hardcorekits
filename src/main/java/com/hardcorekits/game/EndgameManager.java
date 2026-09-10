package com.hardcorekits.game;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.util.Msg;
import com.hardcorekits.util.Phases;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * The End Game: a hard stop on the clock.
 *
 * <p>An hour in, the match ends whether the players have settled it or not. Everyone still
 * standing is sealed into a bedrock box in the sky and lava is poured in from the ceiling, a
 * layer at a time, until there is nowhere left to stand. There is no hiding from it — the only
 * way out of the box is through whoever else is in there.
 *
 * <p>The point is the server rather than the drama. A match that has stopped progressing, two
 * players avoiding each other or nobody at the keyboard at all, otherwise holds the world open
 * forever and blocks the next game. This guarantees the reset arrives.
 *
 * <p>Guarantee is the operative word, so the last act is unconditional: once the final layer of
 * lava has landed, whatever is still alive in there dies regardless of what it is immune to.
 * Without that, a Fireman could tread lava indefinitely and the stalemate would carry on in a
 * smaller room.
 *
 * <p>Leaving is not an escape route. Anyone who is not online to be teleported in is
 * eliminated as the box opens, and from that moment a disconnect is an immediate elimination
 * with no reconnect window — otherwise logging out and waiting for the lava to clear the room
 * would beat fighting.
 *
 * <p>Warnings run on the shared countdown ladder ({@link Phases#isMilestone}): every whole
 * minute, then 30 / 15 / 10, then every second from five down.
 */
public final class EndgameManager {

    private static final Material SHELL = Material.BEDROCK;
    /** Torches every this many blocks around the inside of the wall. */
    private static final int TORCH_SPACING = 4;
    /** Headroom the world must have above the ceiling for the box to sit where configured. */
    private static final int CEILING_MARGIN = 2;

    private final HardcoreGames plugin;
    private final GameManager game;
    private final GameConfig config;

    private BukkitTask warningTask;
    private BukkitTask countdownTask;
    private BukkitTask pourTask;

    /** Centre of the box floor, once it has been built. */
    private Location box;
    /** Set the moment the box opens. From here on, leaving the match is leaving for good. */
    private boolean begun;

    EndgameManager(HardcoreGames plugin, GameManager game, GameConfig config) {
        this.plugin = plugin;
        this.game = game;
        this.config = config;
    }

    /** Where the box is, or null before it has been built. */
    public Location box() {
        return box == null ? null : box.clone();
    }

    /**
     * Whether the End Game has opened.
     *
     * <p>{@link GameManager} reads this to decide how a disconnect is treated: once the box is
     * up there is no reconnect window, because waiting out the lava somewhere else would be a
     * better move than fighting.
     */
    public boolean hasBegun() {
        return begun;
    }

    void schedule() {
        cancel();
        warningTask = Phases.delayed(plugin, config.endgameWarningMinutes() * 60L, this::warn);
    }

    void cancel() {
        Phases.cancel(warningTask);
        Phases.cancel(countdownTask);
        Phases.cancel(pourTask);
        warningTask = null;
        countdownTask = null;
        pourTask = null;
    }

    void clear() {
        cancel();
        box = null;
        begun = false;
    }

    // ---------------------------------------------------------------- the warning

    private void warn() {
        if (!game.state().isLive()) {
            return;
        }
        int seconds = (config.endgameMinutes() - config.endgameWarningMinutes()) * 60;
        if (seconds <= 0) {
            begin();
            return;
        }

        countdownTask = Phases.countdown(plugin, seconds,
                remaining -> {
                    if (Phases.isMilestone(remaining)) {
                        Msg.timer("End Game in " + Msg.duration(remaining)
                                + ". Everyone still alive goes in the box.");
                    }
                },
                this::begin);
    }

    // ---------------------------------------------------------------- the box

    /**
     * Admin override: drop the timers and put everyone in the box now.
     *
     * <p>Skips the warning countdown entirely, so the box lands without the five minutes of
     * notice a real match gets.
     *
     * @return false if the End Game is already running, or the match is not live
     */
    public boolean forceBegin() {
        if (begun || !game.state().isLive()) {
            return false;
        }
        cancel();
        begin();
        return true;
    }

    private void begin() {
        if (!game.state().isLive()) {
            return;
        }

        // Anyone not online to be teleported in is out here, with no reconnect window. That may
        // settle the match on its own, in which case there is nothing left to build.
        game.eliminateAbsent();
        if (!game.state().isLive()) {
            return;
        }

        begun = true;
        List<Player> fighters = game.alivePlayers();
        box = buildBox();

        Msg.timer("End Game! Fight it out. The lava is coming.");
        placeInCircle(fighters);
        for (Player player : fighters) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 0.7F);
        }

        pourTask = Phases.delayed(plugin, config.endgamePourDelaySeconds(),
                () -> pourLayer(ceilingY() - 1, true));
    }

    /**
     * Builds the sealed box and returns the centre of its floor.
     *
     * <p>Everything inside the shell is cleared, so it does not matter what the box is built
     * through — at this height that is normally nothing but air anyway.
     */
    private Location buildBox() {
        World world = config.world();
        int radius = config.endgameRadius();
        int floorY = floorY();
        int ceilingY = ceilingY();
        int cx = config.centerX();
        int cz = config.centerZ();

        for (int dx = -(radius + 1); dx <= radius + 1; dx++) {
            for (int dz = -(radius + 1); dz <= radius + 1; dz++) {
                boolean wall = Math.abs(dx) > radius || Math.abs(dz) > radius;
                for (int y = floorY; y <= ceilingY; y++) {
                    boolean shell = wall || y == floorY || y == ceilingY;
                    world.getBlockAt(cx + dx, y, cz + dz).setType(shell ? SHELL : Material.AIR);
                }
            }
        }

        lightIt(world, cx, cz, floorY, radius);
        return new Location(world, cx + 0.5D, floorY + 1, cz + 0.5D);
    }

    /** Torches around the inside of the wall. The box is sealed, so it is otherwise pitch dark. */
    private void lightIt(World world, int cx, int cz, int floorY, int radius) {
        for (int offset = -radius; offset <= radius; offset += TORCH_SPACING) {
            world.getBlockAt(cx + offset, floorY + 1, cz - radius).setType(Material.TORCH);
            world.getBlockAt(cx + offset, floorY + 1, cz + radius).setType(Material.TORCH);
            world.getBlockAt(cx - radius, floorY + 1, cz + offset).setType(Material.TORCH);
            world.getBlockAt(cx + radius, floorY + 1, cz + offset).setType(Material.TORCH);
        }
    }

    /** Everyone evenly spaced around a ring on the floor, facing the middle. */
    private void placeInCircle(List<Player> fighters) {
        if (fighters.isEmpty()) {
            return;
        }
        // One ring in from the torches, so nobody arrives standing on one.
        double radius = Math.max(1.0D, config.endgameRadius() - 2.0D);
        int immunity = config.endgameArrivalImmunitySeconds();

        for (int i = 0; i < fighters.size(); i++) {
            double angle = 2.0D * Math.PI * i / fighters.size();
            Location spot = box.clone().add(radius * Math.cos(angle), 0.0D, radius * Math.sin(angle));
            spot.setDirection(new Vector(-Math.cos(angle), 0.0D, -Math.sin(angle)));

            Player player = fighters.get(i);
            player.setFallDistance(0.0F);
            player.teleport(spot);
            if (immunity > 0) {
                game.grantImmunity(player, immunity);
            }
        }
    }

    // ---------------------------------------------------------------- the lava

    /**
     * Floods one layer and schedules the next, working down from the ceiling.
     *
     * <p>Each layer is laid as source blocks rather than left to flow, so the fill runs on the
     * clock instead of at the mercy of lava physics — the time the box becomes unsurvivable can
     * be read straight off the config.
     */
    private void pourLayer(int y, boolean first) {
        if (box == null) {
            return;
        }
        if (y < box.getBlockY()) {
            finish();
            return;
        }

        World world = box.getWorld();
        int radius = config.endgameRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.getBlockAt(box.getBlockX() + dx, y, box.getBlockZ() + dz).setType(Material.LAVA);
            }
        }

        if (first) {
            Msg.timer("The lava is falling.");
        }
        pourTask = Phases.delayed(plugin, config.endgamePourIntervalSeconds(),
                () -> pourLayer(y - 1, false));
    }

    /**
     * The backstop.
     *
     * <p>The box is full to the floor by now, so anything still standing in it is standing in
     * lava that cannot hurt it. Killing outright rather than dealing damage is deliberate: the
     * End Game exists to end the match, and a kit that ignores fire must not be able to outlast
     * it.
     */
    private void finish() {
        pourTask = null;
        Msg.timer("The box is full. Nothing survives the End Game.");

        // Anyone still in the match dies here, including anyone who was offline when the box
        // was built and so never arrived in it. The End Game ends the match, not just the fight.
        for (Player player : game.alivePlayers()) {
            player.setHealth(0.0D);
        }

        // Stand-in tributes from /hg fake were never teleported anywhere and would otherwise hold
        // the match open on their own. Dropped a beat later so the real deaths resolve first.
        if (game.fakeCount() > 0) {
            Phases.delayed(plugin, 1L, () -> game.killFakes(game.fakeCount()));
        }
    }

    // ---------------------------------------------------------------- geometry

    /** Y of the box floor, dropped if the world is not tall enough to hold the ceiling above it. */
    private int floorY() {
        World world = config.world();
        int highest = world.getMaxHeight() - config.endgameWallHeight() - CEILING_MARGIN;
        return Math.max(world.getMinHeight() + 1, Math.min(config.endgameHeight(), highest));
    }

    private int ceilingY() {
        return floorY() + config.endgameWallHeight() + 1;
    }
}
