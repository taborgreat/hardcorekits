package gg.hungergames.listener;

import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.ReaperKit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The Reaper's curse.
 *
 * <p>Melee with the wooden hoe, and only the hoe — the Reaper carrying a looted sword is
 * just a fighter until they swap back to the scythe. Every hoe hit lands Wither: ticking
 * damage, and hearts drawn black so the victim reads their health as a guess. Unlike poison,
 * wither <em>can</em> kill, which is what makes an early curse worth the weak swing.
 *
 * <p>Re-cursing refreshes the clock rather than stacking the damage.
 */
public final class ReaperListener implements Listener {

    private final GameManager game;
    private final KitRegistry kits;

    public ReaperListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!game.state().isLive()
                || !(event.getEntity() instanceof Player victim)
                || !(event.getDamager() instanceof Player reaper)
                || reaper.equals(victim)
                || !kits.canUseAbility(reaper, ReaperKit.ID)) {
            return;
        }
        if (reaper.getInventory().getItemInMainHand().getType() != ReaperKit.SCYTHE) {
            return; // the curse is the scythe's, not the player's
        }

        victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                game.config().reaperWitherSeconds() * 20, 0, true, false));
        reaper.playSound(reaper.getLocation(), Sound.ENTITY_WITHER_SKELETON_HURT, 0.5F, 0.8F);
    }
}
