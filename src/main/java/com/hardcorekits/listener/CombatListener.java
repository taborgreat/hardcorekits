package com.hardcorekits.listener;

import com.hardcorekits.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Feeds the {@link com.hardcorekits.game.CombatTracker} so combat logging can be punished.
 *
 * <p>MONITOR + ignoreCancelled: this only records damage that actually landed, and never
 * changes the outcome of the event.
 */
public final class CombatListener implements Listener {

    private final GameManager game;

    public CombatListener(GameManager game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !game.state().isLive()) {
            return;
        }
        Player attacker = resolveAttacker(event);
        game.combat().record(victim.getUniqueId(),
                attacker == null ? null : attacker.getUniqueId(),
                attacker == null ? null : attacker.getName());
    }

    /** The player responsible, following projectiles back to whoever fired them. */
    private Player resolveAttacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        if (byEntity.getDamager() instanceof Player direct) {
            return direct;
        }
        if (byEntity.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
