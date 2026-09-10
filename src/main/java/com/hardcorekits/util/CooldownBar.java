package com.hardcorekits.util;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a running kit timer on the XP bar: full when it starts, draining to empty as it runs
 * out — a Kangaroo's fall-immunity window, a Thor or Jackhammer cooldown, whatever comes next.
 *
 * <p>Only the bar's <em>fill</em> is used. The level number is the match kill count and is
 * never touched here; when a timer ends, {@link GameManager#showKills} hands the bar straight
 * back to it. One timer per player — starting a new one replaces whatever was showing, which
 * is the right answer for every current user (a refreshed window should restart the drain).
 *
 * <p>Static on purpose: half a dozen listeners each showing one timer is exactly the case
 * where threading an instance through every constructor buys nothing but noise. The reset
 * hook in {@code HardcoreGames} clears it between matches like any per-match state.
 */
public final class CooldownBar {

    private static final Map<UUID, BukkitTask> running = new ConcurrentHashMap<>();

    private CooldownBar() {
    }

    /** Shows {@code seconds} draining on the player's XP bar, then restores the kill count. */
    public static void show(HardcoreGames plugin, GameManager game, Player player, double seconds) {
        UUID uuid = player.getUniqueId();
        cancel(uuid);
        if (seconds <= 0.0D) {
            return;
        }

        long endAt = System.currentTimeMillis() + (long) (seconds * 1000.0D);
        running.put(uuid, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long left = endAt - System.currentTimeMillis();
            if (!player.isOnline() || left <= 0L) {
                cancel(uuid);
                if (player.isOnline()) {
                    game.showKills(player);
                }
                return;
            }
            float fraction = (float) (left / (seconds * 1000.0D));
            player.setExp(Math.min(0.999F, fraction));
        }, 1L, 2L));
    }

    /** Ends one player's timer early — the drain stops and the kill count returns. */
    public static void clear(GameManager game, Player player) {
        cancel(player.getUniqueId());
        if (player.isOnline()) {
            game.showKills(player);
        }
    }

    /** Per-match state like any other; wired into the game's reset hooks. */
    public static void clearAll() {
        running.values().forEach(BukkitTask::cancel);
        running.clear();
    }

    private static void cancel(UUID uuid) {
        BukkitTask task = running.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }
}
