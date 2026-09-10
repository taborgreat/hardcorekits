package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.BerserkerKit;
import com.hardcorekits.util.Phases;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
     * Blood Lust's leg irons: jump strength multiplied to zero for the duration. An attribute
     * rather than the old negative Jump Boost, because modern versions clamp that amplifier
     * and the "cannot jump" half of the kit quietly stopped being true.
     */
    private static final NamespacedKey NO_JUMP_KEY =
            new NamespacedKey("hardcoregames", "blood_lust_no_jump");

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** The pending leg-iron releases, so a refreshed Blood Lust extends rather than races. */
    private final Map<UUID, BukkitTask> releases = new HashMap<>();

    public BerserkerListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state: every leg iron comes off with the match. */
    public void clearState() {
        releases.values().forEach(Phases::cancel);
        releases.clear();
        for (Player online : Bukkit.getOnlinePlayers()) {
            releaseJump(online);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (!game.state().isLive()) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null || !kits.canUseAbility(killer, BerserkerKit.ID)) {
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
        pinDown(killer, seconds);

        killer.sendMessage(Component.text("Blood Lust. Strength " + level + " for "
                + seconds + "s. You cannot jump.",
                playerKill ? NamedTextColor.DARK_RED : NamedTextColor.RED));
    }

    /** Grounds the Berserker for the Blood Lust, and books the release. */
    private void pinDown(Player killer, int seconds) {
        AttributeInstance jump = killer.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump == null) {
            return;
        }
        releaseJump(killer);
        jump.addTransientModifier(new AttributeModifier(NO_JUMP_KEY, -1.0D,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1));

        Phases.cancel(releases.remove(killer.getUniqueId()));
        releases.put(killer.getUniqueId(), Phases.delayed(plugin, seconds, () -> {
            releases.remove(killer.getUniqueId());
            if (killer.isOnline()) {
                releaseJump(killer);
            }
        }));
    }

    private void releaseJump(Player player) {
        AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.getModifiers().stream()
                    .filter(modifier -> NO_JUMP_KEY.equals(modifier.getKey()))
                    .toList()
                    .forEach(jump::removeModifier);
        }
    }
}
