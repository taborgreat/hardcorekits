package gg.hungergames.listener;

import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.SnailKit;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
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
 * The Snail's smear.
 *
 * <p>One roll per landed hit, players and mobs alike — a pet wolf slowed is a pet wolf that
 * never reaches you. MONITOR because this reads the fight rather than shaping it: by the time
 * it runs, everything else has decided whether the hit counts at all.
 */
public final class SnailListener implements Listener {

    private final GameManager game;
    private final KitRegistry kits;

    public SnailListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!game.state().isLive() || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Player snail = attacker(event);
        if (snail == null || snail.equals(victim)
                || !kits.canUseAbility(snail, SnailKit.ID)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= game.config().snailSlowChance()) {
            return;
        }

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                game.config().snailSlowSeconds() * 20,
                Math.max(0, game.config().snailSlowLevel() - 1), true, false));
        snail.playSound(snail.getLocation(), Sound.BLOCK_HONEY_BLOCK_SLIDE, 0.8F, 0.7F);
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
