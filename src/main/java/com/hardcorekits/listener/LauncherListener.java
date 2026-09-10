package com.hardcorekits.listener;

import com.hardcorekits.game.GameConfig;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.LauncherKit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Launcher's pads.
 *
 * <p>Live sponges are a registry of placements rather than a block change, like a Demoman's
 * mines, so a sponge nobody armed is scenery. Stepping on one throws you, and the throw is read
 * off the pad itself:
 *
 * <ul>
 *   <li><b>Height</b> comes from the sponges stacked underneath — every one adds its own kick,
 *       up to the configured cap.</li>
 *   <li><b>Direction</b> comes from the pad being lopsided. The neighbouring sponges are
 *       averaged, and you are thrown away from that weight: an even pad cancels out and throws
 *       straight up, a diagonal line throws along itself.</li>
 * </ul>
 *
 * <p>Whoever is thrown is owed the landing, so their next fall is free.
 */
public final class LauncherListener implements Listener {

    /** Sponges soak, and a soaked pad still throws. */
    private static final Set<Material> SPONGES = Set.of(Material.SPONGE, Material.WET_SPONGE);

    /** Stops a single step registering on several consecutive movement packets. */
    private static final long RETRIGGER_MILLIS = 400L;

    private final GameManager game;
    private final KitRegistry kits;

    /** Every sponge a Launcher put down this match. */
    private final Set<Location> armed = new HashSet<>();
    /** Last launch per player, against re-triggering. */
    private final Map<UUID, Long> lastLaunch = new HashMap<>();
    /** Players who are owed a free landing. */
    private final Map<UUID, Long> softLandingUntil = new HashMap<>();

    public LauncherListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset. */
    public void clearPads() {
        armed.clear();
        lastLaunch.clear();
        softLandingUntil.clear();
    }

    public int armedCount() {
        return armed.size();
    }

    // ---------------------------------------------------------------- the pad

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!SPONGES.contains(placed.getType()) || !game.state().isLive()) {
            return;
        }
        if (kits.hasKit(event.getPlayer(), LauncherKit.ID)) {
            armed.add(placed.getLocation());
        }
    }

    /** Digging a pad up disarms it rather than leaving a ghost entry behind. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        armed.remove(event.getBlock().getLocation());
    }

    // ---------------------------------------------------------------- the launch

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Pads lie dormant through the grace period along with every other kit ability, so the
        // opening minutes cannot be spent flinging people off the map.
        if (!game.state().isPvpEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        Block under = player.getLocation().subtract(0.0D, 0.1D, 0.0D).getBlock();
        if (!SPONGES.contains(under.getType()) || !armed.contains(under.getLocation())) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastLaunch.get(player.getUniqueId());
        if (last != null && now - last < RETRIGGER_MILLIS) {
            return;
        }
        lastLaunch.put(player.getUniqueId(), now);

        launch(player, under);
    }

    /**
     * Hard ceiling on launch speed, whatever the stack or the fall behind it. ~3.0 blocks a
     * tick peaks around 60 blocks up — the height a full five-high pad is meant to reach, and
     * the most anything should.
     */
    private static final double MAX_BOUNCE = 3.0D;

    private void launch(Player player, Block pad) {
        GameConfig config = game.config();

        // A trampoline, not a spring-loaded floor: the stack sets the guaranteed hop, and
        // falling speed is returned with interest on top of it — so a drop onto a pad bounces
        // higher than a step, and a pad placed below a fall turns the fall into height.
        Vector current = player.getVelocity();
        double stackKick = config.launcherPower() * stackUnder(pad);
        double returned = -current.getY() * config.launcherRestitution();
        double up = Math.min(MAX_BOUNCE, Math.max(stackKick, returned));

        // Horizontal momentum is the player's own and survives the bounce — running across a
        // pad carries your run. The lean only adds on top, and only if it is configured on.
        Vector throwing = new Vector(current.getX(), 0.0D, current.getZ())
                .add(lean(pad).multiply(config.launcherSidewaysPower()));
        throwing.setY(up);
        player.setVelocity(throwing);
        player.setFallDistance(0.0F);

        softLandingUntil.put(player.getUniqueId(),
                System.currentTimeMillis() + config.launcherFallImmunitySeconds() * 1000L);
        pad.getWorld().playSound(pad.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0F, 1.2F);
    }

    /**
     * How many sponges are holding this one up, counting itself, capped.
     *
     * <p>This is the whole of "stacking increases the power": build the tower, get the height.
     */
    private int stackUnder(Block pad) {
        int stack = 1;
        int cap = Math.max(1, game.config().launcherMaxStack());
        Block below = pad.getRelative(BlockFace.DOWN);
        while (stack < cap && SPONGES.contains(below.getType())
                && armed.contains(below.getLocation())) {
            stack++;
            below = below.getRelative(BlockFace.DOWN);
        }
        return stack;
    }

    /**
     * Which way the pad is lopsided, as a horizontal unit vector pointing away from its mass.
     *
     * <p>Sponges around this one are averaged and the result inverted: a pad with sponges on
     * every side cancels to nothing and throws straight up, while a line or an edge throws you
     * off the open side. Diagonals count, which is what makes a diagonal run throw along
     * itself.
     */
    private Vector lean(Block pad) {
        Vector weight = new Vector();
        int neighbours = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block neighbour = pad.getRelative(dx, 0, dz);
                if (!SPONGES.contains(neighbour.getType())
                        || !armed.contains(neighbour.getLocation())) {
                    continue;
                }
                weight.add(new Vector(dx, 0, dz));
                neighbours++;
            }
        }

        if (neighbours == 0 || weight.lengthSquared() < 0.0001D) {
            return new Vector(); // a lone sponge, or an even pad: straight up
        }
        return weight.normalize().multiply(-1.0D);
    }

    // ---------------------------------------------------------------- the landing

    /** The throw is not a trap: whoever went up does not pay for coming down. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = softLandingUntil.get(player.getUniqueId());
        if (until == null) {
            return;
        }
        if (System.currentTimeMillis() >= until) {
            softLandingUntil.remove(player.getUniqueId());
            return;
        }
        // Spent on the landing it paid for, so the next fall is the player's own business.
        softLandingUntil.remove(player.getUniqueId());
        event.setCancelled(true);
    }
}
