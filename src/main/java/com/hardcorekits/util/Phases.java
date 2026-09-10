package com.hardcorekits.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.IntConsumer;

/**
 * The "set flag -> wait -> clear flag -> broadcast" pattern, built once.
 *
 * <p>Used by the invulnerability grace period, the feast timer and the pre-game countdown.
 */
public final class Phases {

    private Phases() {
    }

    /** Runs {@code onComplete} after {@code seconds}. */
    public static BukkitTask delayed(Plugin plugin, long seconds, Runnable onComplete) {
        return Bukkit.getScheduler().runTaskLater(plugin, onComplete, seconds * 20L);
    }

    /**
     * Ticks once per second from {@code seconds} down to 1 (passing the remaining count to
     * {@code onSecond}), then runs {@code onComplete}.
     */
    public static BukkitTask countdown(Plugin plugin, int seconds, IntConsumer onSecond, Runnable onComplete) {
        return new BukkitRunnable() {
            private int remaining = seconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    cancel();
                    onComplete.run();
                    return;
                }
                onSecond.accept(remaining);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Whether a countdown should announce at this many seconds remaining.
     *
     * <p>Every whole minute, then 30 / 15 / 10, then every second from 5 down. For a 2-minute
     * timer that gives 2m, 1m, 30s, 15s, 10s, 5, 4, 3, 2, 1 — and for a 5-minute feast warning
     * it adds 5m, 4m, 3m.
     */
    public static boolean isMilestone(int remaining) {
        if (remaining <= 5) {
            return true;
        }
        if (remaining == 10 || remaining == 15 || remaining == 30) {
            return true;
        }
        return remaining % 60 == 0;
    }

    /** Null-safe cancel, so callers do not need the guard everywhere. */
    public static void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
