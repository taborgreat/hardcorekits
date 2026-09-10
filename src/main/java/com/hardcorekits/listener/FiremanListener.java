package com.hardcorekits.listener;

import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.FiremanKit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Set;

/**
 * The Fireman's immunity.
 *
 * <p>Everything on the heat side is cancelled outright — burning, lava, magma blocks and
 * lightning. Drowning is deliberately <em>not</em> in that list: it is what stops lava being a
 * permanent hiding place, since a Fireman holds their breath in it like anyone else. A long
 * swim still kills, just not by burning.
 */
public final class FiremanListener implements Listener {

    /** Damage a Fireman shrugs off outright. Note the absence of DROWNING. */
    private static final Set<EntityDamageEvent.DamageCause> IMMUNE_TO = Set.of(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.LIGHTNING);

    /**
     * Blocks that burn on contact.
     *
     * <p>{@code HOT_FLOOR} and {@code CAMPFIRE} were folded into {@code CONTACT} in 26.2, and
     * CONTACT also covers cactus and berry bushes — which a Fireman has no business surviving.
     * So contact damage is judged by the block that dealt it rather than the cause alone.
     */
    private static final Set<Material> HOT_BLOCKS = Set.of(
            Material.MAGMA_BLOCK,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.LAVA);

    private final GameManager game;
    private final KitRegistry kits;

    public FiremanListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !game.state().isLive()) {
            return;
        }
        if (!isHeat(event) || !kits.hasKit(player, FiremanKit.ID)) {
            return;
        }

        event.setCancelled(true);
        // Stop the cosmetic burning too, so a Fireman is not permanently on fire after a dip.
        player.setFireTicks(0);
    }

    private boolean isHeat(EntityDamageEvent event) {
        if (IMMUNE_TO.contains(event.getCause())) {
            return true;
        }
        return event.getCause() == EntityDamageEvent.DamageCause.CONTACT
                && event instanceof EntityDamageByBlockEvent byBlock
                && byBlock.getDamager() != null
                && HOT_BLOCKS.contains(byBlock.getDamager().getType());
    }
}
