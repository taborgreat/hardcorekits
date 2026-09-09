package gg.hungergames.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.AnchorKit;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Anchor's weight.
 *
 * <p>Melee knockback is cancelled in both directions — whether the Anchor is being hit or is
 * doing the hitting — while anything thrown still shoves, which is the kit's counter-play.
 *
 * <p>Only {@code ENTITY_ATTACK} and {@code SWEEP_ATTACK} are touched. Projectile knockback
 * arrives as {@code DAMAGE}, so leaving that cause alone is what keeps arrows working, and
 * explosions are left alone too so a Demoman mine still launches an Anchor.
 *
 * <p>Identifying the attacker takes a little work: the non-deprecated knockback event carries
 * no source entity, and every subclass that does is deprecated for removal. So the damage event
 * — which fires immediately before — records who hit whom for the current tick, and the
 * knockback handler reads that back.
 */
public final class AnchorListener implements Listener {

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Victim -> server tick on which an Anchor last melee'd them. */
    private final Map<UUID, Integer> struckByAnchorOnTick = new HashMap<>();
    /** Horizontal distance walked since the last footstep, per player. */
    private final Map<UUID, Double> walked = new HashMap<>();

    public AnchorListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset. */
    public void clearState() {
        struckByAnchorOnTick.clear();
        walked.clear();
    }

    private boolean isAnchor(Player player) {
        return kits.hasKit(player, AnchorKit.ID);
    }

    // ---------------------------------------------------------------- knockback

    /** Records an Anchor's melee hit so the knockback that follows can be recognised. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!game.state().isLive()) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile) {
            return; // arrows are exempt; never mark them
        }
        if (damager instanceof Player attacker && isAnchor(attacker)) {
            struckByAnchorOnTick.put(event.getEntity().getUniqueId(), Bukkit.getCurrentTick());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !game.state().isLive()) {
            return;
        }
        EntityKnockbackEvent.Cause cause = event.getCause();
        if (cause != EntityKnockbackEvent.Cause.ENTITY_ATTACK
                && cause != EntityKnockbackEvent.Cause.SWEEP_ATTACK) {
            return; // projectiles (DAMAGE) and explosions still move an Anchor
        }

        if (isAnchor(victim) || struckByAnchorThisTick(victim)) {
            event.setCancelled(true);
        }
    }

    private boolean struckByAnchorThisTick(Player victim) {
        Integer tick = struckByAnchorOnTick.get(victim.getUniqueId());
        return tick != null && tick == Bukkit.getCurrentTick();
    }

    // ---------------------------------------------------------------- no boots

    /**
     * {@link PlayerArmorChangeEvent} is not cancellable, so the boots come back off a tick
     * later rather than being prevented outright. This catches every route in — clicking them
     * into the slot, right-clicking to auto-equip, or a dispenser doing it.
     */
    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.FEET) {
            return;
        }
        ItemStack boots = event.getNewItem();
        if (boots == null || boots.getType() == Material.AIR) {
            return;
        }
        Player player = event.getPlayer();
        if (!game.state().isLive() || !isAnchor(player)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack worn = player.getInventory().getBoots();
            if (worn == null || worn.getType() == Material.AIR) {
                return;
            }
            player.getInventory().setBoots(null);
            // Give them back rather than destroying them; drop whatever will not fit.
            for (ItemStack leftover : player.getInventory().addItem(worn).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            player.sendMessage(Component.text("An Anchor cannot wear boots.",
                    NamedTextColor.GRAY));
        });
    }

    // ---------------------------------------------------------------- iron footsteps

    /**
     * Rings like iron every few blocks, so an Anchor can be heard coming — and so a player
     * legitimately running the kit is distinguishable from someone running anti-knockback.
     *
     * <p>Distance-based rather than time-based, so it tracks real movement instead of firing
     * while someone stands still turning their head.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!game.state().isLive() || !isAnchor(player)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double moved = Math.sqrt((dx * dx) + (dz * dz));
        if (moved <= 0.0D) {
            return;
        }

        UUID uuid = player.getUniqueId();
        double total = walked.getOrDefault(uuid, 0.0D) + moved;
        if (total < game.config().anchorStepDistance()) {
            walked.put(uuid, total);
            return;
        }
        walked.put(uuid, 0.0D);

        // Played into the world, not at the player, so everyone nearby hears it.
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP,
                (float) game.config().anchorStepVolume(), 1.0F);
    }
}
