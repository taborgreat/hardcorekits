package com.hardcorekits.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * Message styling in one place.
 *
 * <p>Server colour convention, matching the classic servers:
 * <ul>
 *   <li><b>red</b> — timers: tournament start, invincibility, feast</li>
 *   <li><b>aqua</b> — kills and the remaining-player count</li>
 *   <li><b>dark aqua</b> — forfeits, so they are not mistaken for a kill</li>
 *   <li><b>yellow</b> — join/leave and compass tracking</li>
 *   <li><b>white</b> — ordinary player chat (untouched)</li>
 * </ul>
 *
 * <p>Nothing carries a prefix — command feedback speaks in the same clean voice as the
 * broadcasts, told apart by colour alone.
 */
public final class Msg {

    private Msg() {
    }

    // ---------------------------------------------------------------- command feedback

    public static void info(CommandSender to, String text) {
        to.sendMessage(Component.text(text, NamedTextColor.YELLOW));
    }

    public static void error(CommandSender to, String text) {
        to.sendMessage(Component.text(text, NamedTextColor.RED));
    }

    public static void success(CommandSender to, String text) {
        to.sendMessage(Component.text(text, NamedTextColor.GREEN));
    }

    // ---------------------------------------------------------------- broadcasts

    public static void broadcast(String text, NamedTextColor color) {
        Bukkit.broadcast(Component.text(text, color));
    }

    /** Timers: tournament start, invincibility, feast. */
    public static void timer(String text) {
        broadcast(text, NamedTextColor.RED);
    }

    /** Kills and the remaining-player count — same colour, so they read as one block. */
    public static void kill(String text) {
        broadcast(text, NamedTextColor.AQUA);
    }

    /** The victory line. Red like the timers, but its own call site — it is the loudest line. */
    public static void win(String text) {
        broadcast(text, NamedTextColor.RED);
    }

    /** Join/leave and tracking. */
    public static void notice(String text) {
        broadcast(text, NamedTextColor.YELLOW);
    }

    // Elimination lines are built rather than sent, because the caller follows them with the
    // remaining-player count as a single announcement.

    /** Blue kill line. */
    public static Component killLine(String text) {
        return Component.text(text, NamedTextColor.AQUA);
    }

    /** Teal forfeit line, so it reads as distinct from a normal kill. */
    public static Component forfeitLine(String text) {
        return Component.text(text, NamedTextColor.DARK_AQUA);
    }

    // ---------------------------------------------------------------- formatting

    /** "1 player" / "7 players". */
    public static String players(int count) {
        return count + (count == 1 ? " player" : " players");
    }

    /** "45 seconds" / "1 minute" / "2 minutes" / "2m 30s". */
    public static String duration(int seconds) {
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        int minutes = seconds / 60;
        int rest = seconds % 60;
        if (rest != 0) {
            return minutes + "m " + rest + "s";
        }
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
