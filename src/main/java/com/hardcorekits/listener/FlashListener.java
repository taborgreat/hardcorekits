package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.FlashKit;
import com.hardcorekits.util.CooldownBar;
import com.hardcorekits.util.Interact;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Flash's arrival.
 *
 * <p>The destination is a ray trace to the block being looked at — landing on top of the face
 * that was hit, so aiming at a tower's rim puts you on the rim, not inside the wall. Aiming
 * at open sky flashes the full range and leaves gravity to finish the trip; the kit teleports
 * you to where you looked, not to safety.
 *
 * <p>Arrival is loud on purpose, and priced on distance: cosmetic lightning at the landing, a
 * sparkline along the path travelled, and one second of Weakness per two blocks — so the
 * across-the-map escape arrives too weak to swing, and the short tactical hop barely pays.
 * The cooldown is drawn on the torch itself and on the XP bar.
 */
public final class FlashListener implements Listener {

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** When each Flash may flash again. */
    private final Map<UUID, Long> nextFlash = new HashMap<>();

    /**
     * One click can arrive as two events (a use and a swing); this collapses them into one
     * attempt so the second never spends anything or nags about a cooldown it just started.
     */
    private final Map<UUID, Long> lastAttempt = new HashMap<>();

    public FlashListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset like every other kit's. */
    public void clearCooldowns() {
        nextFlash.clear();
        lastAttempt.clear();
    }

    /**
     * The primary trigger: the swing. A left-click reaches the server from any range —
     * unlike a right-click, which the client only sends for a block item when an actual
     * block is in reach, and which therefore went silent aimed at a far tower or the sky.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != FlashKit.TORCH) {
            return;
        }
        if (!game.state().isLive() || !kits.canUseAbility(player, FlashKit.ID)) {
            return;
        }
        attemptFlash(player);
    }

    /**
     * Left-click is the real trigger, and works at any range: a swing at empty air fires
     * LEFT_CLICK_AIR even when what you are aiming at is fifty blocks off. Right-click is kept
     * for convenience, but a right-click on a block item only reaches the server when a block
     * is within placing reach — which is why aiming at a distant tower did nothing.
     */
    @EventHandler(ignoreCancelled = true)
    public void onFlash(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != FlashKit.TORCH) {
            return;
        }
        Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(player, FlashKit.ID)) {
            return; // anyone else places an ordinary torch
        }
        if (action == Action.RIGHT_CLICK_BLOCK && Interact.opensBlock(event)) {
            return;
        }

        // Always cancelled: the torch is the trigger, never a placed block.
        event.setCancelled(true);
        attemptFlash(player);
    }

    private void attemptFlash(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastAttempt.put(player.getUniqueId(), now);
        if (last != null && now - last < 500L) {
            return; // the same click, wearing its other hat
        }
        Long until = nextFlash.get(player.getUniqueId());
        if (until != null && now < until) {
            player.sendActionBar(Component.text("Your flash has "
                    + ((until - now) / 1000L + 1) + "s cooldown left!", NamedTextColor.RED));
            return;
        }

        Location origin = player.getLocation().clone();
        Location dest = destination(player);
        double distance = origin.distance(dest);
        if (distance < 2.0D) {
            player.sendActionBar(Component.text("Too close to flash.", NamedTextColor.GRAY));
            return; // not worth the cooldown, not spent
        }

        // Keep the player's own facing — arriving mid-fight staring at a wall helps nobody.
        dest.setYaw(origin.getYaw());
        dest.setPitch(origin.getPitch());
        player.setFallDistance(0.0F);
        player.teleport(dest);

        // The bill: one second of Weakness per two blocks travelled.
        int weakness = (int) Math.round(distance / 2.0D);
        if (weakness > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                    weakness * 20, 0, true, false));
        }

        // The announcement: cosmetic lightning, and a sparkline along the path.
        World world = dest.getWorld();
        world.strikeLightningEffect(dest);
        drawTrail(world, origin, dest);

        int cooldown = game.config().flashCooldownSeconds();
        nextFlash.put(player.getUniqueId(), now + cooldown * 1000L);
        player.setCooldown(FlashKit.TORCH, cooldown * 20);
        CooldownBar.show(plugin, game, player, cooldown);
        player.sendActionBar(Component.text("Flashed " + (int) distance + " blocks. Weakness for "
                + weakness + "s.", NamedTextColor.LIGHT_PURPLE));
    }

    /** Top of the block face being looked at, or the full range into open air. */
    private Location destination(Player player) {
        double range = game.config().flashMaxDistance();
        RayTraceResult ray = player.rayTraceBlocks(range);
        if (ray == null || ray.getHitBlock() == null || ray.getHitBlockFace() == null) {
            return player.getEyeLocation().add(player.getEyeLocation().getDirection()
                    .normalize().multiply(range));
        }

        org.bukkit.block.Block spot = ray.getHitBlock().getRelative(ray.getHitBlockFace());
        // A side-face hit — the wall of a tower, the rim seen from below — walks upward a few
        // blocks looking for somewhere with headroom, so the flash lands ON the thing rather
        // than hovering beside it. Straight-down and straight-up hits are already right.
        for (int lift = 0; lift < 3; lift++) {
            if (spot.getType().isAir() && spot.getRelative(0, 1, 0).getType().isAir()) {
                break;
            }
            spot = spot.getRelative(0, 1, 0);
        }
        return spot.getLocation().add(0.5D, 0.0D, 0.5D);
    }

    private void drawTrail(World world, Location from, Location to) {
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 1.0D) {
            return;
        }
        step.normalize();
        Location cursor = from.clone().add(0.0D, 1.0D, 0.0D);
        for (double travelled = 0.0D; travelled < length; travelled += 1.0D) {
            world.spawnParticle(Particle.PORTAL, cursor, 8, 0.15D, 0.15D, 0.15D, 0.02D);
            cursor.add(step);
        }
    }
}
