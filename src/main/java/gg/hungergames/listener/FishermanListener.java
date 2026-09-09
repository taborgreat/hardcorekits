package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.FishermanKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Fisherman's reel.
 *
 * <p>Vanilla nudges a hooked player a little way towards the rod. This drags them the whole
 * distance and lands them on the Fisherman's own block, which is what makes the kit's tricks
 * work: pulling someone off the tower they are building, off the ledge you are both standing
 * on, or straight down into the 3x1 hole you dug and jumped across.
 *
 * <p>It is a launch, not a teleport. The victim flies an arc, keeps travelling if something is
 * in the way, and takes whatever the landing is worth — so a long pull hurts on arrival, and
 * terrain between the two of you is the counter-play.
 *
 * <p>The arc is solved rather than guessed. Minecraft applies a fixed gravity and a fixed drag
 * to a flying player every tick, so given a flight time there is exactly one launch velocity
 * that lands on the target; {@link #solveHorizontal} and {@link #solveVertical} invert those
 * two series. The flight time itself comes from the arc — long enough to peak above both ends
 * and drop out of it — so the victim always arrives falling, rather than sailing over the
 * Fisherman's head still climbing. The client simulates its own player, so the landing is close
 * rather than exact, and a victim who fights it can shave it. Both are fine.
 */
public final class FishermanListener implements Listener {

    /** Blocks per tick squared, pulled off a flying player every tick. */
    private static final double GRAVITY = 0.08D;
    /** What is left of vertical speed after a tick. */
    private static final double VERTICAL_DRAG = 0.98D;
    /** What is left of horizontal speed after a tick, in air. */
    private static final double HORIZONTAL_DRAG = 0.91D;
    /** Fall speed the two above converge on: g*drag/(1-drag). */
    private static final double TERMINAL_FALL = GRAVITY * VERTICAL_DRAG / (1.0D - VERTICAL_DRAG);
    /** Floor on flight time, so a point-blank reel is still a hop and not a catapult. */
    private static final double MIN_FLIGHT_TICKS = 5.0D;
    /**
     * Blocks of arc per block of distance, used in place of the configured arc height once the
     * pull is long enough to need it.
     *
     * <p>A long reel has to arc higher or the launch speed becomes absurd. That is the shape of
     * the trajectory rather than a tuning value, so it lives here with the rest of the physics.
     */
    private static final double ARC_PER_BLOCK = 0.15D;
    /** Safety rail on the solved velocity — nothing sane needs more than this. */
    private static final double MAX_SPEED = 4.0D;

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** When each victim may be reeled again. Stops one Fisherman juggling someone forever. */
    private final Map<UUID, Long> reelableAt = new HashMap<>();

    public FishermanListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Dropped on reset, like any other per-match state. */
    public void clearCooldowns() {
        reelableAt.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY
                || !(event.getCaught() instanceof Player victim)) {
            return;
        }
        Player fisher = event.getPlayer();
        if (!kits.hasKit(fisher, FishermanKit.ID) || victim.equals(fisher)) {
            return;
        }

        // Hooking is not damage, but hauling someone across the map during the grace period is
        // still a fight — so the reel only works once PvP does.
        if (!game.state().isPvpEnabled()) {
            fisher.sendMessage(Component.text("The line goes slack, nobody can be reeled in yet.",
                    NamedTextColor.AQUA));
            return;
        }
        if (!game.isAlive(victim)) {
            return;
        }

        Location from = victim.getLocation();
        Location to = fisher.getLocation();
        double distance = from.distance(to);
        if (distance > game.config().fishermanMaxDistance()) {
            fisher.sendMessage(Component.text("The line snaps, they are too far out.",
                    NamedTextColor.AQUA));
            return;
        }

        long now = System.currentTimeMillis();
        Long ready = reelableAt.get(victim.getUniqueId());
        if (ready != null && now < ready) {
            fisher.sendMessage(Component.text("Your line will not hold them again yet.",
                    NamedTextColor.AQUA));
            return;
        }
        reelableAt.put(victim.getUniqueId(),
                now + game.config().fishermanCooldownSeconds() * 1000L);

        // Vanilla applies its own small tug as this event finishes resolving; land the real
        // pull on the tick after, so ours is the velocity that survives.
        plugin.getServer().getScheduler().runTask(plugin, () -> reel(fisher, victim));
    }

    private void reel(Player fisher, Player victim) {
        if (!victim.isOnline() || !fisher.isOnline() || !game.isAlive(victim)) {
            return;
        }
        Location from = victim.getLocation();
        Location to = fisher.getLocation();
        if (!from.getWorld().equals(to.getWorld())) {
            return;
        }

        victim.setVelocity(reelVelocity(from, to));
        victim.sendMessage(Component.text("You were reeled in by " + fisher.getName() + "!",
                NamedTextColor.AQUA));
        from.getWorld().playSound(from, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0F, 0.8F);
    }

    /** The launch that carries a player from {@code from} onto {@code to}. */
    private Vector reelVelocity(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();

        double ticks = flightTicks(Math.hypot(dx, dz), dy);

        Vector velocity = new Vector(
                solveHorizontal(dx, ticks),
                solveVertical(dy, ticks),
                solveHorizontal(dz, ticks));

        if (velocity.length() > MAX_SPEED) {
            velocity.normalize().multiply(MAX_SPEED);
        }
        return velocity;
    }

    /**
     * How long the victim is in the air.
     *
     * <p>Taken from the arc rather than the distance: the reel peaks {@code arc-height} above
     * the victim — more, the further it has to travel, and more again if the Fisherman is
     * above them — and the flight is the climb to that peak plus the drop out of it. Solving
     * for that time is what guarantees the victim arrives on the way down and lands on the
     * Fisherman, instead of sailing over their head still climbing.
     */
    private double flightTicks(double horizontal, double rise) {
        double apex = Math.max(game.config().fishermanArcHeight(), horizontal * ARC_PER_BLOCK)
                + Math.max(0.0D, rise);
        double climb = Math.sqrt(2.0D * apex / GRAVITY);
        double drop = Math.sqrt(2.0D * Math.max(apex - rise, 0.1D) / GRAVITY);
        return Math.max(MIN_FLIGHT_TICKS, climb + drop);
    }

    /**
     * Opening speed whose drag-decayed steps sum to {@code distance} over {@code ticks}.
     *
     * <p>Each tick moves the player {@code v * drag^i}, so the total is a geometric series.
     */
    private static double solveHorizontal(double distance, double ticks) {
        return distance * (1.0D - HORIZONTAL_DRAG) / (1.0D - Math.pow(HORIZONTAL_DRAG, ticks));
    }

    /**
     * Opening climb that leaves the player {@code rise} blocks higher after {@code ticks}.
     *
     * <p>Vertical speed decays towards the terminal fall rather than towards zero, which is the
     * only difference from the horizontal case.
     */
    private static double solveVertical(double rise, double ticks) {
        return (rise + TERMINAL_FALL * ticks) * (1.0D - VERTICAL_DRAG)
                / (1.0D - Math.pow(VERTICAL_DRAG, ticks)) - TERMINAL_FALL;
    }
}
