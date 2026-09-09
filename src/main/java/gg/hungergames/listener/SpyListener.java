package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.Kit;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.SpyKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the Spy knows.
 *
 * <p>Three separate reads, all private to the Spy — nothing here is visible to anyone else, so
 * the kit never gives its owner away:
 * <ul>
 *   <li>The compass reports exact coordinates, distance and the target's kit, on top of the
 *       ordinary lock-on. It deliberately reuses {@link CompassListener}'s own targeting, so
 *       the intel always describes whoever the compass actually locked on to.</li>
 *   <li>Anyone entering the alert radius is announced once, with their kit and a bearing.
 *       Once announced they stay quiet until they have properly left again, so a neighbour
 *       loitering at the edge of the radius cannot rattle the alarm.</li>
 *   <li>Whoever the Spy is looking at is named on the action bar, with their kit. Line of
 *       sight is required — the kit reads people, not walls.</li>
 * </ul>
 *
 * <p>The last two need to work while the Spy is standing still — the whole point is being
 * warned while mining — so they run on a sweep rather than off player movement.
 */
public final class SpyListener implements Listener {

    /**
     * How far past the alert radius someone has to get before they can set it off again.
     *
     * <p>Without it, anyone hovering at exactly the radius would be announced over and over as
     * they drift a block in and out.
     */
    private static final double ALERT_RELEASE = 1.25D;

    /** Generous aim: a ray this wide picks people out without demanding pixel accuracy. */
    private static final double LOOK_RAY_SIZE = 0.5D;

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Per Spy, everyone already announced — cleared per person as they leave the radius. */
    private final Map<UUID, Set<UUID>> announced = new HashMap<>();

    /**
     * Spy -> whoever their compass is locked on to.
     *
     * <p>This is what separates the Spy's compass from everyone else's. An ordinary compass is
     * a <em>snapshot</em>: it points at where the target stood when you clicked, and following
     * it to the end tells you where they were, not where they are. A Spy's stays pinned — the
     * sweep re-aims it every tick or so, so the needle follows a moving target until they die,
     * disconnect, or the Spy locks on to somebody else.
     */
    private final Map<UUID, UUID> pinned = new HashMap<>();

    public SpyListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Starts the radar sweep. Called once, at enable, like the border. */
    public void start() {
        long interval = game.config().spySweepTicks();
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, interval, interval);
    }

    /** Dropped on reset, like any other per-match state. */
    public void clearState() {
        announced.clear();
        pinned.clear();
    }

    // ---------------------------------------------------------------- the compass

    /**
     * MONITOR so the ordinary "Compass pointing at X" line lands first and this reads as the
     * detail under it. Nothing here changes the event.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompass(PlayerInteractEvent event) {
        Player spy = event.getPlayer();
        if (!CompassListener.isCompassClick(event, game) || !kits.hasKit(spy, SpyKit.ID)) {
            return;
        }
        Player target = CompassListener.nearestOpponent(game, spy);
        if (target == null) {
            return; // the compass has already said there is nobody to point at
        }

        // Lock on. From here the sweep keeps the needle on them as they move.
        pinned.put(spy.getUniqueId(), target.getUniqueId());

        Location at = target.getLocation();
        spy.sendMessage(Component.text(label(target) + " is at "
                + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ()
                + ". " + blocks(at.distance(spy.getLocation())) + " away.",
                NamedTextColor.YELLOW));
        spy.sendMessage(Component.text("Your compass is now tracking them.",
                NamedTextColor.YELLOW));
    }

    /**
     * Re-aims a Spy's compass at whoever they locked on to.
     *
     * <p>Runs on the sweep rather than a task of its own, so the needle updates as often as the
     * rest of the Spy's intel does.
     */
    private void followPinnedTarget(Player spy) {
        UUID targetId = pinned.get(spy.getUniqueId());
        if (targetId == null) {
            return;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline() || !game.isAlive(target)
                || !target.getWorld().equals(spy.getWorld())) {
            pinned.remove(spy.getUniqueId());
            spy.sendMessage(Component.text("You have lost the trail.", NamedTextColor.YELLOW));
            return;
        }

        spy.setCompassTarget(target.getLocation());
    }

    // ---------------------------------------------------------------- the sweep

    private void sweep() {
        if (!game.state().isLive()) {
            return;
        }
        for (Player spy : game.alivePlayers()) {
            if (!kits.hasKit(spy, SpyKit.ID)) {
                continue;
            }
            followPinnedTarget(spy);
            announceNewcomers(spy);
            identifyWhoeverIsInSight(spy);
        }
    }

    /** Warns about anyone who has just come inside the alert radius. */
    private void announceNewcomers(Player spy) {
        double radius = game.config().spyAlertRadius();
        double release = radius * ALERT_RELEASE;
        Set<UUID> known = announced.computeIfAbsent(spy.getUniqueId(), uuid -> new HashSet<>());
        Location here = spy.getLocation();

        for (Player other : game.alivePlayers()) {
            if (other.equals(spy) || !other.getWorld().equals(here.getWorld())) {
                continue;
            }
            double distance = other.getLocation().distance(here);

            if (distance > release) {
                known.remove(other.getUniqueId());
                continue;
            }
            if (distance > radius || !known.add(other.getUniqueId())) {
                continue; // out of range, or already announced and not yet gone
            }

            spy.sendMessage(Component.text(label(other) + " is nearby, "
                    + blocks(distance) + " to the " + bearing(here, other.getLocation()) + ".",
                    NamedTextColor.GOLD));
            spy.playSound(here, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 1.6F);
        }
    }

    /** Names whoever the Spy is looking at, on the action bar. */
    private void identifyWhoeverIsInSight(Player spy) {
        Location eye = spy.getEyeLocation();
        RayTraceResult hit = spy.getWorld().rayTraceEntities(eye, eye.getDirection(),
                game.config().spyLookRange(), LOOK_RAY_SIZE,
                entity -> entity instanceof Player other
                        && !other.equals(spy)
                        && game.isAlive(other));

        if (hit == null || !(hit.getHitEntity() instanceof Player seen) || !spy.hasLineOfSight(seen)) {
            return;
        }
        spy.sendActionBar(Component.text(label(seen) + ", "
                + blocks(seen.getLocation().distance(spy.getLocation())),
                NamedTextColor.GRAY));
    }

    // ---------------------------------------------------------------- formatting

    /** "Username(Kit)", the same shape the kill lines use. */
    private String label(Player player) {
        Kit kit = game.kits().selectedFor(player.getUniqueId());
        return player.getName() + "(" + (kit == null ? "None" : kit.displayName()) + ")";
    }

    private static String blocks(double distance) {
        int rounded = (int) Math.round(distance);
        return rounded + (rounded == 1 ? " block" : " blocks");
    }

    /** Compass bearing from one point to another. In Minecraft +X is east and +Z is south. */
    private static String bearing(Location from, Location to) {
        String[] points = {"east", "south-east", "south", "south-west",
                "west", "north-west", "north", "north-east"};
        double degrees = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
        int index = (int) Math.round(((degrees % 360.0D) + 360.0D) % 360.0D / 45.0D) % points.length;
        return points[index];
    }
}
