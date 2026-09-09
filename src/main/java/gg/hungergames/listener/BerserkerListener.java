package gg.hungergames.listener;

import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.BerserkerKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Blood Lust.
 *
 * <p>One handler covers both cases: {@code PlayerDeathEvent} extends {@link EntityDeathEvent},
 * so a player kill and a mob kill arrive the same way and differ only in what died.
 *
 * <p>The "cannot jump" half is Jump Boost at a <em>negative</em> amplifier, which reduces jump
 * height to nothing rather than increasing it — the same trick vanilla map-makers use to pin
 * players down. It runs for exactly as long as the Strength does.
 */
public final class BerserkerListener implements Listener {

    /**
     * Jump Boost below level zero: height is reduced to nothing, so the player cannot leave
     * the ground at all.
     */
    private static final int NO_JUMP_AMPLIFIER = -1;

    private final GameManager game;
    private final KitRegistry kits;

    public BerserkerListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (!game.state().isLive()) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null || !kits.hasKit(killer, BerserkerKit.ID)) {
            return;
        }

        boolean playerKill = event.getEntity() instanceof Player;
        int level = playerKill
                ? game.config().berserkerPlayerStrengthLevel()
                : game.config().berserkerMobStrengthLevel();
        int seconds = playerKill
                ? game.config().berserkerPlayerSeconds()
                : game.config().berserkerMobSeconds();

        applyBloodLust(killer, level, seconds, playerKill);
    }

    private void applyBloodLust(Player killer, int level, int seconds, boolean playerKill) {
        int amplifier = Math.max(0, level - 1);
        int ticks = seconds * 20;

        // A mob kill must never downgrade the stronger buff from a player kill.
        PotionEffect current = killer.getPotionEffect(PotionEffectType.STRENGTH);
        if (current != null && current.getAmplifier() > amplifier
                && current.getDuration() > ticks) {
            return;
        }

        killer.addPotionEffect(new PotionEffect(
                PotionEffectType.STRENGTH, ticks, amplifier, true, false));
        killer.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP_BOOST, ticks, NO_JUMP_AMPLIFIER, true, false));

        killer.sendMessage(Component.text("Blood Lust. Strength " + level + " for "
                + seconds + "s. You cannot jump.",
                playerKill ? NamedTextColor.DARK_RED : NamedTextColor.RED));
    }
}
