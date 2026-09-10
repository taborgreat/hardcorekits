package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameConfig;
import com.hardcorekits.game.GameManager;
import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combat rolled back to 1.8, as far as the server side can take it.
 *
 * <p>The attack-speed cap is already gone — {@code GameManager#applyCombatAttributes} makes the
 * recharge instant, which is the big one. This class handles the rest of what 1.9 changed and
 * players still fight around:
 *
 * <ul>
 *   <li><b>Sweep attacks.</b> Cancelled, so a swing hits the thing you aimed at and nothing
 *       else. With no attack cooldown a sweep would otherwise land on nearly every swing.</li>
 *   <li><b>Sprint crits.</b> 1.9 added {@code !isSprinting()} to the critical-hit test, which
 *       killed the sprint-jump crit. The other vanilla conditions are kept exactly.</li>
 *   <li><b>Ender pearl cooldown.</b> 1.9 invention; cleared the moment it is applied, so pearls
 *       chain the way they used to.</li>
 *   <li><b>Shields.</b> Did not exist. The recipe is dropped and a shield cannot be raised.</li>
 *   <li><b>Knockback.</b> Recomputed with the 1.8 formula. The visible difference is vertical:
 *       1.9 only lifts a victim who is on the ground, so combos keep people pinned, where 1.8
 *       lifted them every hit.</li>
 *   <li><b>Regeneration.</b> 1.9 heals roughly a heart a second off saturation, which quietly
 *       makes food better than soup. Throttled back to the 1.8 rate.</li>
 * </ul>
 *
 * <p>What is not here: sword blocking, which needs the client to be holding an item it cannot
 * be given, and true 1.8 hit-delay behaviour, which lives below the API.
 *
 * <p>The knockback rewrite is on by default and its direction is measured, not assumed:
 * a test victim parked beside an attacker flies away from the swing, ~2 blocks a bare
 * hit. {@code combat.knockback.enabled} falls back to vanilla if it ever needs to.
 */
public final class LegacyCombatListener implements Listener {

    private static final double CRIT_MULTIPLIER = 1.5D;
    /** Below this the two entities are stacked and there is no direction to push in. */
    private static final double MIN_SEPARATION = 1.0E-4D;

    private final HardcoreGames plugin;
    private final GameManager game;

    /** Last natural heal per player, for the 1.8 regeneration rate. */
    private final Map<UUID, Long> lastHeal = new HashMap<>();

    public LegacyCombatListener(HardcoreGames plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    /** Per-match state, dropped on reset. */
    public void clearHealTimers() {
        lastHeal.clear();
    }

    /** Shields are a 1.9 item; without the recipe the only way to hold one is not to. */
    public static void removeShieldRecipe(Plugin plugin) {
        if (Bukkit.removeRecipe(NamespacedKey.minecraft("shield"))) {
            plugin.getLogger().info("Shield recipe removed — 1.8 combat.");
        }
    }

    // ---------------------------------------------------------------- swings

    @EventHandler(ignoreCancelled = true)
    public void onSweep(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                && game.config().disableSweep()) {
            event.setCancelled(true);
        }
    }

    /**
     * Restores the 1.8 critical hit.
     *
     * <p>Vanilla still runs its own crit check first; this only adds back the case 1.9 took
     * away, which is the sprinting one — so a hit is never boosted twice.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrit(EntityDamageByEntityEvent event) {
        if (!game.config().sprintCrits()
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity)
                || event.isCritical()) {
            return;
        }
        if (!attacker.isSprinting() || !isFalling(attacker)) {
            return;
        }

        event.setDamage(event.getDamage() * CRIT_MULTIPLIER);
        LivingEntity victim = (LivingEntity) event.getEntity();
        victim.getWorld().spawnParticle(Particle.CRIT,
                victim.getLocation().add(0.0D, victim.getHeight() / 2.0D, 0.0D),
                10, 0.3D, 0.3D, 0.3D, 0.2D);
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 1.0F);
    }

    /**
     * The vanilla crit conditions, minus the 1.9 sprinting clause.
     *
     * <p>Vanilla also tests the ground flag, which is deprecated here because it is whatever
     * the client claims. It is redundant anyway: fall distance is server-side, accumulates
     * only while descending, and resets on landing — so above zero already means airborne.
     */
    private boolean isFalling(Player attacker) {
        return attacker.getFallDistance() > 0.0F
                && !attacker.isClimbing()
                && !attacker.isInWater()
                && attacker.getVehicle() == null
                && !attacker.hasPotionEffect(PotionEffectType.BLINDNESS);
    }

    // ---------------------------------------------------------------- items

    /** Pearls had no cooldown in 1.8. The server applies one on launch; take it straight back. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(PlayerLaunchProjectileEvent event) {
        if (event.getItemStack().getType() != Material.ENDER_PEARL
                || !game.config().noPearlCooldown()) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> player.setCooldown(Material.ENDER_PEARL, 0));
    }

    /**
     * A shield that reached the map some other way still cannot be raised.
     *
     * <p>Not {@code ignoreCancelled}: raising a shield is a click on air, and an air click
     * always reports cancelled because {@code useInteractedBlock} is DENY when there is no
     * block. Ignoring cancelled events would skip exactly the case this exists for.
     */
    @EventHandler
    public void onRaiseShield(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.SHIELD && game.config().disableShields()) {
            // Deny only the item, so a chest behind the shield still opens.
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    // ---------------------------------------------------------------- knockback

    /**
     * Replaces melee knockback with the 1.8 calculation.
     *
     * <p>The event's vector is replaced rather than the event being cancelled. That distinction
     * matters: the server applies this knockback as part of resolving the hit, so cancelling and
     * calling {@code setVelocity} instead writes a velocity that the rest of the hit immediately
     * overwrites — which reads in game as knockback having been switched off entirely.
     *
     * <p>What is set here is the <em>push</em>, pointing away from the attacker, not the
     * resulting velocity: the server still halves the victim's existing motion and clamps the
     * lift around it. On top of the base push goes the sprint/Knockback-enchant bonus, which
     * follows the attacker's facing rather than the line between the two players.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent event) {
        GameConfig config = game.config();
        if (!config.legacyKnockback()
                || event.getCause() != EntityKnockbackEvent.Cause.ENTITY_ATTACK
                || !(event.getHitBy() instanceof Player attacker)) {
            return;
        }
        LivingEntity victim = event.getEntity();

        // Away from the attacker. Vanilla passes (attacker - victim) into knockBack and then
        // *subtracts* it, so the sign here has to be the other way round; getting that backwards
        // drags the victim towards the swing instead of away from it.
        double dx = victim.getLocation().getX() - attacker.getLocation().getX();
        double dz = victim.getLocation().getZ() - attacker.getLocation().getZ();
        double separation = Math.sqrt(dx * dx + dz * dz);
        if (separation < MIN_SEPARATION) {
            return; // standing inside each other: no direction to push, leave vanilla to it
        }

        double resisted = 1.0D - knockbackResistance(victim);
        if (resisted <= 0.0D) {
            return; // fully resistant, so vanilla's own handling is already correct
        }

        // The event's vector is the final applied knockback (measured: a cow parked east of
        // the attacker flew further east, ~1.9 blocks per bare hit). 1.8 folded half the
        // victim's existing motion into it, which is what lets consecutive hits chain into a
        // combo instead of each one starting from rest — so that half is folded in here too,
        // with the lift capped the way 1.8 capped it.
        Vector carried = victim.getVelocity().multiply(0.5D);
        double horizontal = config.knockbackHorizontal() * resisted;
        double x = carried.getX() + dx / separation * horizontal;
        double z = carried.getZ() + dz / separation * horizontal;
        double y = Math.min(config.knockbackVerticalLimit(),
                carried.getY() + config.knockbackVertical() * resisted);

        int level = knockbackLevel(attacker);
        if (level > 0) {
            // Along the attacker's facing, which from a yaw is (-sin, cos) — the bonus pushes
            // the victim the way the attacker is looking, not back past their own shoulder.
            double yaw = Math.toRadians(attacker.getLocation().getYaw());
            double bonus = level * config.knockbackExtraHorizontal() * resisted;
            x += -Math.sin(yaw) * bonus;
            z += Math.cos(yaw) * bonus;
            y = Math.min(config.knockbackVerticalLimit() + config.knockbackExtraVertical(),
                    y + config.knockbackExtraVertical() * resisted);
        }

        event.setKnockback(new Vector(x, y, z));
    }

    /** Knockback enchant on the swung item, with sprinting worth a level of its own. */
    private int knockbackLevel(Player attacker) {
        int level = attacker.getInventory().getItemInMainHand()
                .getEnchantmentLevel(Enchantment.KNOCKBACK);
        return attacker.isSprinting() ? level + 1 : level;
    }

    private double knockbackResistance(LivingEntity victim) {
        AttributeInstance resistance = victim.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        return resistance == null ? 0.0D : resistance.getValue();
    }

    // ---------------------------------------------------------------- healing

    /**
     * 1.8 regeneration: a heart every four seconds, not the modern saturation drip.
     *
     * <p>Potion regeneration and golden apples are untouched — only the natural, food-driven
     * heal is throttled, which is what keeps soup the fastest way to get health back.
     */
    @EventHandler(ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        GameConfig config = game.config();
        if (config.regenIntervalSeconds() <= 0) {
            return; // modern regeneration left alone
        }

        long now = System.currentTimeMillis();
        Long last = lastHeal.get(player.getUniqueId());
        if (last != null && now - last < config.regenIntervalSeconds() * 1000L) {
            event.setCancelled(true);
            return;
        }
        lastHeal.put(player.getUniqueId(), now);
        event.setAmount(1.0D);
    }
}
