package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.SwitcherKit;
import com.hardcorekits.util.CooldownBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Switcher's trade.
 *
 * <p>The tag travels: it starts on the Switcher Ball item, is copied onto the snowball entity
 * as it launches, and is what the landing checks — so an ordinary snowball, thrown by anyone,
 * does ordinary nothing, and a Switcher Ball that was somehow looted does nothing in hands
 * that did not pick the kit.
 *
 * <p>The swap itself is deliberately blunt: both parties are put exactly where the other was,
 * fall distances wiped, with a couple of seconds of immunity — the Endermage rule, for the
 * same reason: nobody should die mid-teleport before they can turn around.
 */
public final class SwitcherListener implements Listener {

    /** Seconds of immunity for both ends of the swap. */
    private static final int SWAP_IMMUNITY_SECONDS = 3;

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** When each Switcher may throw again. */
    private final Map<UUID, Long> nextThrow = new HashMap<>();

    public SwitcherListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset like every other kit's. */
    public void clearCooldowns() {
        nextThrow.clear();
    }

    // ---------------------------------------------------------------- the throw

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball ball)
                || !(ball.getShooter() instanceof Player player)) {
            return;
        }
        if (!isSwitcherBall(player.getInventory().getItemInMainHand())
                && !isSwitcherBall(player.getInventory().getItemInOffHand())) {
            return; // an ordinary snowball, which stays ordinary
        }
        if (!game.state().isLive() || !kits.hasKit(player, SwitcherKit.ID)) {
            return; // looted Switcher Balls are just snow in the wrong hands
        }
        // Refused, not ignored: ignoring the throw during the grace period let vanilla spend
        // the ball as plain snow — a tenth of the kit gone for nothing.
        if (!kits.canUseAbility(player, SwitcherKit.ID)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Switcher balls unlock when invincibility ends.",
                    NamedTextColor.RED));
            return;
        }

        long now = System.currentTimeMillis();
        Long until = nextThrow.get(player.getUniqueId());
        if (until != null && now < until) {
            // Cancelled before the ball is spent — a cooldown must not eat the budget.
            event.setCancelled(true);
            player.sendActionBar(Component.text("Switcher ball has "
                    + ((until - now) / 1000L + 1) + "s cooldown left!", NamedTextColor.RED));
            return;
        }

        ball.getPersistentDataContainer().set(SwitcherKit.BALL_KEY, PersistentDataType.BYTE,
                (byte) 1);
        int cooldown = game.config().switcherCooldownSeconds();
        nextThrow.put(player.getUniqueId(), now + cooldown * 1000L);
        CooldownBar.show(plugin, game, player, cooldown);
    }

    // ---------------------------------------------------------------- the landing

    @EventHandler(ignoreCancelled = true)
    public void onLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball ball)
                || !ball.getPersistentDataContainer().has(SwitcherKit.BALL_KEY,
                        PersistentDataType.BYTE)) {
            return;
        }
        if (!(event.getHitEntity() instanceof LivingEntity target)
                || !(ball.getShooter() instanceof Player thrower)
                || !thrower.isOnline() || target.equals(thrower)) {
            return; // hitting a block is simply a miss — the ball is already spent
        }

        Location mine = thrower.getLocation().clone();
        Location theirs = target.getLocation().clone();

        // Wiped first: arriving where someone was must not inherit the fall they were in.
        thrower.setFallDistance(0.0F);
        target.setFallDistance(0.0F);
        thrower.teleport(theirs);
        target.teleport(mine);

        game.grantImmunity(thrower, SWAP_IMMUNITY_SECONDS);
        if (target instanceof Player swapped) {
            game.grantImmunity(swapped, SWAP_IMMUNITY_SECONDS);
            swapped.sendMessage(Component.text("A Switcher traded places with you!",
                    NamedTextColor.RED));
        }
        thrower.getWorld().playSound(theirs, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        thrower.getWorld().playSound(mine, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
    }

    private boolean isSwitcherBall(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(SwitcherKit.BALL_KEY,
                PersistentDataType.BYTE);
    }
}
