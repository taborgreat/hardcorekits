package com.hardcorekits.listener;

import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.PyroKit;
import com.hardcorekits.util.Interact;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/**
 * The Pyro's thrown charge.
 *
 * <p>Vanilla's fire charge is a lighter: right-clicking one lights the block you clicked and
 * nothing else. Here it is ammunition — the click is cancelled outright and the charge is
 * thrown as a fireball instead, so the two are never confused.
 *
 * <p>What lands is a blaze's fireball, which already carries a direct hit and lights the block
 * it strikes. The kit's own addition is the spread: everything standing near the impact
 * catches, which is what makes a charge worth throwing into a crowd rather than at one person.
 *
 * <p>The thrower is left out of that spread. A charge thrown at a wall two blocks away is a
 * mistake, not a suicide, and the direct hit still lands on them if they earn it.
 */
public final class PyroListener implements Listener {

    private final GameManager game;
    private final KitRegistry kits;

    public PyroListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    // ---------------------------------------------------------------- the throw

    /**
     * Deliberately not {@code ignoreCancelled}.
     *
     * <p>{@link PlayerInteractEvent#isCancelled()} is true whenever <em>either</em> result is
     * DENY, and a right-click on air always carries {@code useInteractedBlock == DENY} because
     * there is no block to interact with. So an air click arrives at the first listener already
     * reporting cancelled, through nobody's doing, and {@code ignoreCancelled = true} silently
     * skips every throw that is not aimed at a block — which is most of them.
     *
     * <p>What a genuine veto looks like is the item result, so that is what gets checked.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY) {
            return; // something really did deny the item, rather than there being no block
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != PyroKit.CHARGE) {
            return;
        }
        Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(player, PyroKit.ID)) {
            return; // anyone else gets an ordinary fire charge
        }

        // With charges in both hands the off-hand pass would spend a second one on one click.
        if (event.getHand() == EquipmentSlot.OFF_HAND
                && player.getInventory().getItemInMainHand().getType() == PyroKit.CHARGE) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && Interact.opensBlock(event)) {
            return;
        }

        // Always cancelled: the charge is thrown, never used as a lighter.
        event.setCancelled(true);

        Vector aim = player.getEyeLocation().getDirection()
                .multiply(game.config().pyroFireballSpeed());
        player.launchProjectile(SmallFireball.class, aim);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0F, 1.0F);

        consume(player, event.getHand(), item);
    }

    // ---------------------------------------------------------------- the landing

    @EventHandler(ignoreCancelled = true)
    public void onLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof SmallFireball fireball)) {
            return;
        }
        ProjectileSource shooter = fireball.getShooter();
        if (!(shooter instanceof Player pyro) || !kits.canUseAbility(pyro, PyroKit.ID)) {
            return; // a blaze's fireball is still just a blaze's fireball
        }
        if (!game.state().isLive()) {
            return;
        }

        Location impact = fireball.getLocation();

        // The bang. A real explosion with fire, TNT-shaped — the direct hit opens the crater,
        // and the spread below sets everyone around it alight. Dropped loot survives it, the
        // same game-wide rule that protects a Demoman's or a Tank's.
        impact.getWorld().createExplosion(impact,
                (float) game.config().pyroExplosionPower(),
                true,
                game.config().pyroBreaksBlocks());

        double radius = game.config().pyroIgniteRadius();
        int ticks = game.config().pyroBurnSeconds() * 20;

        for (Entity nearby : impact.getWorld().getNearbyEntities(impact, radius, radius, radius)) {
            if (nearby.equals(pyro)) {
                continue;
            }
            // Never shorten a fire already burning longer than this one would.
            if (nearby.getFireTicks() < ticks) {
                nearby.setFireTicks(ticks);
            }
        }
    }

    /** Takes the one charge that was thrown, leaving the rest of the stack alone. */
    private void consume(Player player, EquipmentSlot hand, ItemStack charges) {
        PlayerInventory inv = player.getInventory();
        ItemStack left = charges.clone();
        left.setAmount(left.getAmount() - 1);
        ItemStack remaining = left.getAmount() <= 0 ? null : left;

        if (hand == EquipmentSlot.OFF_HAND) {
            inv.setItemInOffHand(remaining);
        } else {
            inv.setItemInMainHand(remaining);
        }
    }
}
