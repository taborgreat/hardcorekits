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
     * A hit does not break the sprint.
     *
     * <p>Vanilla's attack code ends with {@code setSprinting(false)} on the attacker whenever
     * the swing carried a knockback bonus, i.e. every sprint hit. The damage event is raised
     * from inside that code, before the flag is cleared, so "was sprinting" is read here and
     * put back a tick later, once vanilla has finished taking it away. The server's sprint flag
     * reaches the client as entity data, so the client sees itself sprinting again without a
     * fresh key press — the classic-server combo feel.
     *
     * <p>MONITOR: a cancelled hit (a Turtle's crouch, pre-game protection) was not a hit and
     * changes nothing.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprintHit(EntityDamageByEntityEvent event) {
        if (!game.config().keepSprint()
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof Player attacker)
                || !attacker.isSprinting()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (attacker.isOnline() && !attacker.isSprinting()) {
                attacker.setSprinting(true);
            }
        });
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
     * <p>How Paper applies the event matters more than anything else here. The vector on the
     * event is a <em>delta</em>: the server adds it to the victim's current velocity, and the
     * default vector it arrives with is "vanilla's final velocity minus the current one" — the
     * halving of existing motion is baked into it. So the vector set here has to be computed the
     * same way, as final-minus-current. Setting a bare push instead keeps the victim's whole
     * current speed and adds the shove on top, which is a running player being launched.
     *
     * <p>A sprinting or Knockback-enchanted swing raises this event <em>twice</em>, with the
     * same cause, attacker and victim, in the same tick: once from inside the hurt for the base
     * shove, then again from the attack for the bonus. That is exactly 1.8's shape — knockBack
     * in the hurt, then addVelocity for the bonus — so the first call gets the 1.8 base and the
     * second gets the 1.8 bonus as a plain addition. Treating both as the full hit doubled every
     * sprint hit.
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

        double resisted = 1.0D - knockbackResistance(victim);
        if (resisted <= 0.0D) {
            return; // fully resistant, so vanilla's own handling is already correct
        }

        int tick = Bukkit.getCurrentTick();
        Integer lastBase = baseShoveTick.get(victim.getUniqueId());
        if (lastBase != null && lastBase == tick) {
            // Second call this tick: the bonus. 1.8 added it straight onto the motion, along
            // the attacker's facing, which from a yaw is (-sin, cos).
            int level = knockbackLevel(attacker);
            if (level <= 0) {
                return;
            }
            double yaw = Math.toRadians(attacker.getLocation().getYaw());
            double bonus = level * config.knockbackExtraHorizontal() * resisted;
            event.setKnockback(new Vector(
                    -Math.sin(yaw) * bonus,
                    config.knockbackExtraVertical() * resisted,
                    Math.cos(yaw) * bonus));
            return;
        }

        // Away from the attacker.
        double dx = victim.getLocation().getX() - attacker.getLocation().getX();
        double dz = victim.getLocation().getZ() - attacker.getLocation().getZ();
        double separation = Math.sqrt(dx * dx + dz * dz);
        if (separation < MIN_SEPARATION) {
            return; // standing inside each other: no direction to push, leave vanilla to it
        }
        baseShoveTick.put(victim.getUniqueId(), tick);

        // 1.8 knockBack, exactly: halve the motion, add the shove, lift capped.
        Vector current = victim.getVelocity();
        double horizontal = config.knockbackHorizontal() * resisted;
        double finalX = current.getX() / 2.0D + dx / separation * horizontal;
        double finalZ = current.getZ() / 2.0D + dz / separation * horizontal;
        double finalY = Math.min(config.knockbackVerticalLimit(),
                current.getY() / 2.0D + config.knockbackVertical() * resisted);

        event.setKnockback(new Vector(
                finalX - current.getX(),
                finalY - current.getY(),
                finalZ - current.getZ()));
    }

    /** Victim -> tick of their last base shove, so the bonus call in the same tick is known. */
    private final java.util.Map<java.util.UUID, Integer> baseShoveTick = new java.util.HashMap<>();

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
