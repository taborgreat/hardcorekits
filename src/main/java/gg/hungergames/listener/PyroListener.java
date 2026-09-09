package gg.hungergames.listener;

import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.PyroKit;
import gg.hungergames.util.Interact;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
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

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != PyroKit.CHARGE) {
            return;
        }
        Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.hasKit(player, PyroKit.ID)) {
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
        if (!(shooter instanceof Player pyro) || !kits.hasKit(pyro, PyroKit.ID)) {
            return; // a blaze's fireball is still just a blaze's fireball
        }
        if (!game.state().isLive()) {
            return;
        }

        Location impact = fireball.getLocation();
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
