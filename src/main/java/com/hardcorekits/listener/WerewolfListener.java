package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.WerewolfKit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The Werewolf's clock.
 *
 * <p>Applied on a sweep, like the Spy's radar: every couple of seconds each Werewolf gets the
 * effects their world's time says they deserve — Strength, Speed and Night Vision after dark,
 * Weakness in daylight. The durations outlast the sweep interval so the effects read as
 * constant; Night Vision gets a long one because the client fades it out during its last ten
 * seconds, and a flickering sky all night is worse than none.
 *
 * <p>Wolves refuse to target a Werewolf at any hour, tamed or wild — pack loyalty, and the
 * reason a Beastmaster's dogs are the wrong answer to this kit.
 */
public final class WerewolfListener implements Listener {

    /** Vanilla's night window, give or take dusk: beds work from 12542, mobs burn to 23460. */
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;

    private static final long SWEEP_TICKS = 40L;
    /** Comfortably past the sweep, so effects never visibly lapse between refreshes. */
    private static final int EFFECT_TICKS = 90;
    /** Long, because the client fades Night Vision in and out under ten seconds remaining. */
    private static final int NIGHT_VISION_TICKS = 400;

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    public WerewolfListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Started once at enable, like the border and the Spy's sweep. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, SWEEP_TICKS, SWEEP_TICKS);
    }

    private void sweep() {
        if (!game.state().isLive()) {
            return;
        }
        for (Player player : game.alivePlayers()) {
            if (!kits.canUseAbility(player, WerewolfKit.ID)) {
                continue;
            }
            long time = player.getWorld().getTime();
            if (time >= NIGHT_START && time <= NIGHT_END) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                        EFFECT_TICKS, 0, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                        EFFECT_TICKS, 0, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                        NIGHT_VISION_TICKS, 0, true, false));
            } else {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                        EFFECT_TICKS, 0, true, false));
                // Dawn strips the leftover night sight, so day starts honest.
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
    }

    /** Wolves never turn on one of their own. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Wolf
                && event.getTarget() instanceof Player player
                && kits.hasKit(player, WerewolfKit.ID)) {
            event.setCancelled(true);
        }
    }
}
