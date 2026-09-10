package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.TankKit;
import com.hardcorekits.util.Damage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tank's detonations.
 *
 * <p>The awkward part is the loot. A blast at the body would destroy the very items the kill
 * just earned, so the drops are taken off the death event, held, and spawned once the explosion
 * has resolved — which is exactly the "items appear after the explosion" the kit describes.
 *
 * <p>Immunity is blanket: any explosion, from any source, including another Tank's kill or a
 * Demoman's mine.
 */
public final class TankListener implements Listener {

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    public TankListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Blasts do nothing to a Tank, whoever set them off. LOWEST: refused before anything
     * else — a Demoman mine, a Tank kill, a Pyro charge — gets a say in it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (shrugOff(event)) {
            // The shove still happens, and a blast that flings a Tank off a cliff was killing
            // them "by their own explosion" anyway — so the landing after a blast is free too.
            blastShoved.put(event.getEntity().getUniqueId(),
                    System.currentTimeMillis() + BLAST_FALL_GRACE_MILLIS);
        }
    }

    /**
     * The same refusal again at HIGHEST, after every other listener has spoken.
     *
     * <p>"A Tank never takes explosion damage" is an absolute, and the field found ways
     * around a single cancel — so it is asserted at both ends of the event pipeline, and the
     * damage itself is zeroed besides. Even a handler that un-cancels the event between these
     * two has nothing left to deal.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageFinal(EntityDamageEvent event) {
        shrugOff(event);
    }

    /**
     * Tanks whose own kill-explosion is going off this very tick. While a Tank is in here,
     * NO damage of any cause reaches them — the shield spans one tick around their blast, so
     * however Paper decides to attribute that damage, it cannot be the thing that kills them.
     * Swords, arrows and lava work normally the rest of the time.
     */
    private final java.util.Set<java.util.UUID> detonating =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Cancels and zeroes explosion damage to a Tank. True if this event was one. */
    private boolean shrugOff(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !game.state().isLive()) {
            return false;
        }
        if (!kits.hasKit(player, TankKit.ID)) {
            return false;
        }
        boolean explosion = Damage.isExplosion(event);
        if (!explosion && !detonating.contains(player.getUniqueId())) {
            return false;
        }
        event.setDamage(0.0D);
        event.setCancelled(true);
        return explosion;
    }

    /** Milliseconds after a shrugged-off blast in which the landing is also free. */
    private static final long BLAST_FALL_GRACE_MILLIS = 5000L;

    /** Tanks recently flung by an explosion they did not feel. */
    private final java.util.Map<java.util.UUID, Long> blastShoved = new java.util.HashMap<>();

    /** The fall a blast caused is part of the blast. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlastFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = blastShoved.get(player.getUniqueId());
        if (until == null) {
            return;
        }
        blastShoved.remove(player.getUniqueId());
        if (System.currentTimeMillis() < until && kits.hasKit(player, TankKit.ID)) {
            event.setCancelled(true);
        }
    }

    /**
     * Runs at HIGH so the drops are captured after any kit that wants to read the death, but
     * still before the items would otherwise hit the ground.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!game.state().isLive()) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || !kits.hasKit(killer, TankKit.ID)) {
            return;
        }

        Location at = victim.getLocation();
        // Take the loot out of the death event so the blast cannot destroy it.
        List<ItemStack> loot = new ArrayList<>(event.getDrops());
        event.getDrops().clear();

        // Next tick: let the death finish resolving before reshaping the world under it.
        java.util.UUID tank = killer.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> detonate(tank, at, loot));
    }

    private void detonate(java.util.UUID tank, Location at, List<ItemStack> loot) {
        // Shield up before the blast, down a tick after it — the window in which their own
        // explosion could possibly touch them, and no longer.
        detonating.add(tank);
        Bukkit.getScheduler().runTask(plugin, () -> detonating.remove(tank));

        World world = at.getWorld();
        world.createExplosion(at,
                (float) game.config().tankExplosionPower(),
                false,
                game.config().tankBreaksBlocks());

        // Only now, with the blast resolved, is it safe to put the loot on the ground.
        for (ItemStack item : loot) {
            if (item != null && !item.getType().isAir()) {
                world.dropItemNaturally(at, item);
            }
        }
    }
}
