package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.TankKit;
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

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    public TankListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Blasts do nothing to a Tank, whoever set them off. LOWEST: refused before anything
     * else — a Demoman mine, a Tank kill, a Pyro charge — gets a say in it. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !game.state().isLive()) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        if (kits.hasKit(player, TankKit.ID)) {
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
        Bukkit.getScheduler().runTask(plugin, () -> detonate(at, loot));
    }

    private void detonate(Location at, List<ItemStack> loot) {
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
