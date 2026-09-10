package com.hardcorekits.listener;

import com.hardcorekits.game.GameConfig;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.StomperKit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;

/**
 * The Stomper's landing.
 *
 * <p>One fall does two things: it is capped for the Stomper, and the uncapped amount lands on
 * everyone standing near the landing — each victim judged on their own distance, full weight
 * on the block that was hit and fading to nothing at the edge of the radius. A crowd is not a
 * discount: three people under a Stomper are three people who each chose to stand there.
 * Crouching caps the hit, so the counterplay is to see it coming and brace; armour is the
 * other counter, since the stomp lands through the normal damage pipeline and diamond eats
 * its share of it.
 *
 * <p>HIGH priority so {@link ProtectionListener} has already had its say: outside a live match
 * the fall damage is cancelled and there is nothing to transfer, and during the grace period
 * the stomp itself is cancelled as player-dealt damage.
 */
public final class StomperListener implements Listener {

    private final GameManager game;
    private final KitRegistry kits;

    /** Who is being dealt stomp damage this very moment. Null outside the damage call. */
    private UUID stompVictim;

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

            // Each victim on their own taper — a stomp into a crowd hits every one of them,
            // not a split of one fall's worth between them.
            double share = fall * (1.0D - distance / radius);
            if (victim.isSneaking()) {
                share = Math.min(share, config.stomperSneakCap());
            }
            if (share <= 0.0D) {
                continue;
            }

            // Attributed to the Stomper, so the kill and the combat-log timer land on them —
            // and marked for the duration of the blow, so a death inside it reads "was
            // stomped by" rather than as an ordinary melee kill. damage() resolves the whole
            // pipeline, death event included, before returning, which is what makes the
            // set-deal-clear window airtight.
            stompVictim = victim.getUniqueId();
            try {
                victim.damage(share, stomper);
            } finally {
                stompVictim = null;
            }
            landed = true;
        }

        if (landed) {
            landing.getWorld().playSound(landing, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
        }
    }

    /**
     * Whether this player is, right now, dying to a stomp — meaningful only inside the death
     * event, which is exactly where {@link DeathListener} asks.
     */
    public boolean isStompDeath(Player victim) {
        return victim.getUniqueId().equals(stompVictim);
    }
}
