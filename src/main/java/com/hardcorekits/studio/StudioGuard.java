package com.hardcorekits.studio;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps real kit code from derailing a replay. Registered in studio mode only.
 *
 * <p>In the studio every player is a puppet: where it stands, and when it dies, come from the
 * recording. Kit abilities run for what they show (lightning, particles, sounds, blocks,
 * creatures, potion swirls), so everything they would do to a puppet's position or life is
 * turned aside here, in one place, rather than in forty listeners:
 * <ul>
 *   <li>damage never kills; only {@code /kill} (the recording's deaths) does;</li>
 *   <li>a kit's teleport does not move anyone; the recording makes the same jump itself;</li>
 *   <li>nothing can hold a puppet still or push its step somewhere else;</li>
 *   <li>explosions keep their flash, bang and shove but break no blocks (the recording
 *       replays the blocks that really broke);</li>
 *   <li>a player carried by another (the Hulk) is put down after a few seconds if the kit
 *       never lets go;</li>
 *   <li>fire lit by a fireball, a bolt or a blast goes out after a few seconds.</li>
 * </ul>
 */
public final class StudioGuard implements Listener {

    /** How long one player may ride another before being set down regardless. */
    private static final long MAX_CARRY_TICKS = 100L;
    /** How long a fire started by a fireball, a bolt or a blast burns before it is put out. */
    private static final long FLAME_TICKS = 120L;

    private final HardcoreGames plugin;
    private final GameManager game;
    /** Where each puppet said it was stepping to, before any kit listener saw the move. */
    private final Map<UUID, Location> steps = new HashMap<>();

    public StudioGuard(HardcoreGames plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    // ---------------------------------------------------------------- life

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.KILL
                || cause == EntityDamageEvent.DamageCause.VOID
                || cause == EntityDamageEvent.DamageCause.SUICIDE) {
            return; // the director's own /kill: the death the recording asked for
        }
        // Leave a heart: the hit still lands (the flinch, the sound), it just cannot finish.
        if (event.getFinalDamage() >= player.getHealth() - 2.0D) {
            event.setDamage(0.0D);
        }
    }

    // ---------------------------------------------------------------- position

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN
                || !game.isAlive(event.getPlayer()) || event.getTo() == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() == to.getWorld() && from.distanceSquared(to) < 0.01D) {
            return; // a refresh in place (a skin being applied), not a move
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStepFirst(PlayerMoveEvent event) {
        if (game.isAlive(event.getPlayer())) {
            steps.put(event.getPlayer().getUniqueId(), event.getTo().clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStepLast(PlayerMoveEvent event) {
        Location asked = steps.remove(event.getPlayer().getUniqueId());
        if (asked == null) {
            return;
        }
        if (event.isCancelled()) {
            event.setCancelled(false);
        }
        if (!asked.equals(event.getTo())) {
            event.setTo(asked);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        steps.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- the world

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
    }

    /**
     * A fireball, a bolt or a blast may light the ground, as in a match, but the studio world
     * has fire frozen (nothing spreads, nothing burns out) and is filmed out of order. So the
     * flames a kit starts go out by themselves after a few seconds instead of standing for
     * the rest of the film.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block.getType() == Material.FIRE) {
                block.setType(Material.AIR);
            }
        }, FLAME_TICKS);
    }

    // ---------------------------------------------------------------- carrying

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player rider) || !(event.getMount() instanceof Player)) {
            return;
        }
        Entity mount = event.getMount();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (rider.isOnline() && mount.equals(rider.getVehicle())) {
                rider.leaveVehicle();
            }
        }, MAX_CARRY_TICKS);
    }
}
