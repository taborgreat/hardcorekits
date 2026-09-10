package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.GrapplerKit;
import com.hardcorekits.util.CooldownBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Grappler's pull.
 *
 * <p>Built on the fishing rod's own states: the cast is vanilla's, and the pull fires on the
 * reel — hook bitten into ground, snagged on a mob, or hooked into a player. The launch is a
 * flat pull towards the hook plus a fixed hop of lift, scaled up a little with distance so a
 * long line still clears the ledge it was thrown over.
 *
 * <p>Reeling wipes fall distance. That is the advertised save — hook anything mid-fall and
 * the landing forgives you — and it costs nothing extra to grant because the pull itself is
 * already the skill.
 *
 * <p>The Fisherman is this kit's mirror (it reels <em>others</em> in), and the two listeners
 * never collide: each checks its own kit before touching the event.
 *
 * <p>Reeling from open air pulls too — a short lunge towards wherever the bobber hangs. That
 * is kept deliberately (it is half the kit's movement game), and the burst is what pays for
 * it: a few pulls back to back, then the arm rests, Thor-style, with the rest on the XP bar.
 */
public final class GrapplerListener implements Listener {

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Pulls spent in the current burst. */
    private final Map<UUID, Integer> spent = new HashMap<>();
    /** Arms currently resting. */
    private final Map<UUID, Long> restingUntil = new HashMap<>();

    public GrapplerListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset like every other kit's. */
    public void clearCooldowns() {
        spent.clear();
        restingUntil.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onReel(PlayerFishEvent event) {
        PlayerFishEvent.State state = event.getState();
        if (state != PlayerFishEvent.State.IN_GROUND
                && state != PlayerFishEvent.State.REEL_IN
                && state != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }
        Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(player, GrapplerKit.ID)) {
            return;
        }

        Location hook = event.getHook().getLocation();
        Location from = player.getLocation();
        if (!hook.getWorld().equals(from.getWorld())) {
            return;
        }
        double distance = from.distance(hook);
        if (distance < 2.0D) {
            return; // reeling in at your feet is just fishing
        }

        long now = System.currentTimeMillis();
        Long until = restingUntil.get(player.getUniqueId());
        if (until != null && now < until) {
            player.sendActionBar(Component.text("Your arm needs "
                    + ((until - now) / 1000L + 1) + "s more rest!", NamedTextColor.RED));
            return;
        }
        int used = spent.merge(player.getUniqueId(), 1, Integer::sum);
        if (used >= game.config().grapplerBurst()) {
            int cooldown = game.config().grapplerCooldownSeconds();
            spent.remove(player.getUniqueId());
            restingUntil.put(player.getUniqueId(), now + cooldown * 1000L);
            CooldownBar.show(plugin, game, player, cooldown);
        }

        // Flat pull towards the hook, plus lift that grows gently with the length of the
        // line — enough to clear the ledge the hook is lying on, not enough to be a rocket.
        Vector pull = hook.toVector().subtract(from.toVector()).normalize()
                .multiply(game.config().grapplerPower());
        pull.setY(Math.min(1.4D, 0.4D + distance * 0.04D + Math.max(0.0D, pull.getY())));

        player.setFallDistance(0.0F);
        player.setVelocity(pull);
        player.playSound(from, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0F, 0.7F);
        wearRod(player, event);
    }

    /**
     * Every pull costs the rod. Sixteen or so and it snaps — the Grappler's mobility is a
     * resource that runs out, and a new rod is the refill.
     */
    private void wearRod(Player player, PlayerFishEvent event) {
        ItemStack rod = player.getInventory().getItem(event.getHand() == null
                ? org.bukkit.inventory.EquipmentSlot.HAND : event.getHand());
        if (rod == null || rod.getType() != GrapplerKit.HOOK
                || !(rod.getItemMeta() instanceof Damageable meta)) {
            return;
        }
        int damage = meta.getDamage() + game.config().grapplerDurabilityPerPull();
        if (damage >= rod.getType().getMaxDurability()) {
            player.getInventory().setItem(event.getHand(), null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 0.9F);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Your hook snapped!", NamedTextColor.RED));
            return;
        }
        meta.setDamage(damage);
        rod.setItemMeta(meta);
    }
}
