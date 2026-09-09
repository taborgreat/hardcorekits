package gg.hungergames.listener;

import gg.hungergames.game.GameConfig;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.CannibalKit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

/**
 * The Cannibal's meal.
 *
 * <p>One handler, both halves of the kit: the hit feeds the attacker and starves the victim.
 *
 * <p>Only hits on <em>players</em> count. Feeding off mobs would turn any cow into an
 * unlimited food supply and the kit would stop being about hunting people, which is the whole
 * idea. Projectiles are followed back to the shooter, same as every other kit that rewards
 * landing a hit.
 *
 * <p>MONITOR because this reads the fight rather than shaping it — by the time it runs, every
 * other listener has had its say about whether the hit lands at all.
 */
public final class CannibalListener implements Listener {

    /** Vanilla's cap. Food is drumsticks, so twenty is a full bar. */
    private static final int MAX_FOOD = 20;

    private final GameManager game;
    private final KitRegistry kits;

    public CannibalListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!game.state().isLive() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || attacker.equals(victim)
                || !kits.canUseAbility(attacker, CannibalKit.ID)) {
            return;
        }

        GameConfig config = game.config();
        feed(attacker, config.cannibalFoodPerHit(), config.cannibalSaturationPerHit());
        starve(victim, config.cannibalHungerSeconds(), config.cannibalHungerLevel());
    }

    /**
     * Tops up the food bar, and the saturation behind it.
     *
     * <p>Saturation is clamped to the food level because vanilla does the same — a bar that is
     * not full cannot hold more saturation than it has drumsticks, and setting it higher just
     * gets clipped the next time the food system ticks.
     */
    private void feed(Player cannibal, int food, float saturation) {
        int level = Math.min(MAX_FOOD, cannibal.getFoodLevel() + food);
        cannibal.setFoodLevel(level);
        cannibal.setSaturation(Math.min(level, cannibal.getSaturation() + saturation));
    }

    /** Hunger on the victim. Re-hitting refreshes it rather than stacking it. */
    private void starve(Player victim, int seconds, int level) {
        victim.addPotionEffect(new PotionEffect(
                PotionEffectType.HUNGER, seconds * 20, Math.max(0, level - 1), true, false));
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
