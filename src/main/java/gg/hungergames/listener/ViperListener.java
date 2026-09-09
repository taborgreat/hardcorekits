package gg.hungergames.listener;

import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.ViperKit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The Viper's bite.
 *
 * <p>Players only — the Snail is the kit that slows the wildlife; the Viper's venom is for
 * people. One roll per landed hit, MONITOR so everything else has already decided the hit
 * counts. Re-poisoning refreshes rather than stacks, and vanilla poison never lands the
 * killing blow — it stops at half a heart, which is why it causes panic instead of kills.
 */
public final class ViperListener implements Listener {

    private final GameManager game;
    private final KitRegistry kits;

    public ViperListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!game.state().isLive() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player viper = attacker(event);
        if (viper == null || viper.equals(victim)
                || !kits.canUseAbility(viper, ViperKit.ID)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= game.config().viperPoisonChance()) {
            return;
        }

        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                game.config().viperPoisonSeconds() * 20, 0, true, false));
        viper.playSound(viper.getLocation(), Sound.ENTITY_SPIDER_HURT, 0.6F, 1.4F);
    }

    /** The player responsible, following projectiles back to whoever fired them. */
    private Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
