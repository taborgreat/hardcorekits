package gg.hungergames.listener;

import gg.hungergames.game.GameConfig;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.StomperKit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * The Stomper's landing.
 *
 * <p>One fall does two things: it is capped for the Stomper, and the uncapped amount is spent
 * on everyone standing near the landing. Distance splits it — a landing on someone's own block
 * transfers all of it, and it fades to nothing at the edge of the radius. Crouching caps the
 * hit, so the counterplay is to see it coming and brace.
 *
 * <p>HIGH priority so {@link ProtectionListener} has already had its say: outside a live match
 * the fall damage is cancelled and there is nothing to transfer, and during the grace period
 * the stomp itself is cancelled as player-dealt damage.
 */
public final class StomperListener implements Listener {

    private final GameManager game;
    private final KitRegistry kits;

    public StomperListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player stomper)
                || !kits.hasKit(stomper, StomperKit.ID)) {
            return;
        }

        // Raw height-based damage, read before the cap — this is what the victims pay.
        double fall = event.getDamage();
        double cap = game.config().stomperFallCap();
        if (fall > cap) {
            event.setDamage(cap);
        }
        stomp(stomper, fall);
    }

    private void stomp(Player stomper, double fall) {
        GameConfig config = game.config();
        double radius = config.stomperRadius();
        if (fall <= 0.0D || radius <= 0.0D) {
            return;
        }

        Location landing = stomper.getLocation();
        boolean landed = false;
        for (Player victim : game.alivePlayers()) {
            if (victim.equals(stomper) || !victim.getWorld().equals(landing.getWorld())) {
                continue;
            }
            double distance = victim.getLocation().distance(landing);
            if (distance > radius) {
                continue;
            }

            // Full weight on their block, tapering to nothing at the edge of the radius.
            double share = fall * (1.0D - distance / radius);
            if (victim.isSneaking()) {
                share = Math.min(share, config.stomperSneakCap());
            }
            if (share <= 0.0D) {
                continue;
            }

            // Attributed to the Stomper, so the kill and the combat-log timer land on them.
            victim.damage(share, stomper);
            landed = true;
        }

        if (landed) {
            landing.getWorld().playSound(landing, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
        }
    }
}
