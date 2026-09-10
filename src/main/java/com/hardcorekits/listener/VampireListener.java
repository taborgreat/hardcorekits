package com.hardcorekits.listener;

import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.VampireKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Vampire's diet.
 *
 * <p>Three rules, one theme: health flows towards the Vampire.
 *
 * <p>Kills heal — more from players than mobs. A kill earned without being touched for the
 * closing stretch of the fight also pays a tagged vial: a weak splash of harming that damages
 * everyone else it wets and heals the Vampire, custom numbers on a tagged potion rather than
 * vanilla's, so it is a sidearm and not a nuke.
 *
 * <p>And splash chemistry runs backwards: a harming splash heals a Vampire instead, a healing
 * splash hurts them. Implemented at the splash — the Vampire's share of the vanilla effect is
 * zeroed and the inverse applied by hand — so everyone else in the blast is exactly as
 * healed or hurt as vanilla says.
 */
public final class VampireListener implements Listener {

    /** Marks the earned vial and its thrown form. */
    private static final NamespacedKey VIAL_KEY = new NamespacedKey("hardcoregames", "vampire_vial");

    /** "Without taking damage" means untouched for this long before the kill. */
    private static final long CLEAN_KILL_WINDOW_MILLIS = 10_000L;

    private static final double DEFAULT_MAX_HEALTH = 20.0D;

    private final GameManager game;
    private final KitRegistry kits;

    /** Last time each player took damage from anything, for the clean-kill test. */
    private final Map<UUID, Long> lastHurt = new HashMap<>();

    public VampireListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset like every other kit's. */
    public void clearState() {
        lastHurt.clear();
    }

    // ---------------------------------------------------------------- the feeding

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastHurt.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (!game.state().isLive()) {
            return;
        }
        Player vampire = event.getEntity().getKiller();
        if (vampire == null || !kits.canUseAbility(vampire, VampireKit.ID)
                || vampire.equals(event.getEntity())) {
            return;
        }

        boolean playerKill = event.getEntity() instanceof Player;
        heal(vampire, playerKill
                ? game.config().vampirePlayerKillHeal()
                : game.config().vampireMobKillHeal());

        if (playerKill && wasClean(vampire)) {
            vampire.getInventory().addItem(vial());
            vampire.sendMessage(Component.text("A clean kill. Your vial is filled.",
                    NamedTextColor.DARK_RED));
        }
        vampire.playSound(vampire.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.8F, 0.6F);
    }

    private boolean wasClean(Player vampire) {
        Long hurt = lastHurt.get(vampire.getUniqueId());
        return hurt == null || System.currentTimeMillis() - hurt > CLEAN_KILL_WINDOW_MILLIS;
    }

    // ---------------------------------------------------------------- the chemistry

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        boolean vial = event.getPotion().getPersistentDataContainer()
                .has(VIAL_KEY, PersistentDataType.BYTE);
        boolean harming = vial || carries(event.getPotion(), PotionEffectType.INSTANT_DAMAGE);
        boolean healing = carries(event.getPotion(), PotionEffectType.INSTANT_HEALTH);
        if (!harming && !healing) {
            return;
        }

        for (LivingEntity splashed : event.getAffectedEntities()) {
            if (!(splashed instanceof Player player)) {
                continue;
            }
            boolean isVampire = kits.hasKit(player, VampireKit.ID);

            if (vial) {
                // The vial's numbers are its own; nobody gets the vanilla effect from it.
                event.setIntensity(splashed, 0.0D);
                if (isVampire) {
                    heal(player, game.config().vampireVialHeal());
                } else if (game.state().isLive()) {
                    player.damage(game.config().vampireVialDamage());
                }
                continue;
            }

            if (!isVampire) {
                continue; // ordinary chemistry for ordinary people
            }
            double intensity = event.getIntensity(splashed);
            event.setIntensity(splashed, 0.0D);
            if (harming) {
                heal(player, game.config().vampireInvertHealth() * intensity);
            } else {
                player.damage(game.config().vampireInvertHealth() * intensity);
            }
        }
    }

    private boolean carries(ThrownPotion potion, PotionEffectType type) {
        for (PotionEffect effect : potion.getEffects()) {
            if (effect.getType().equals(type)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- plumbing

    private void heal(Player player, double amount) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? DEFAULT_MAX_HEALTH : attribute.getValue();
        player.setHealth(Math.min(max, player.getHealth() + amount));
    }

    private ItemStack vial() {
        ItemStack vial = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) vial.getItemMeta();
        meta.setBasePotionType(PotionType.HARMING);
        meta.displayName(Component.text("Vampire's Vial", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(VIAL_KEY, PersistentDataType.BYTE, (byte) 1);
        vial.setItemMeta(meta);
        return vial;
    }
}
