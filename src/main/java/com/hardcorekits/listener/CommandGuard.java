package com.hardcorekits.listener;

import com.hardcorekits.staff.StaffManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Locale;
import java.util.Set;

/**
 * The bloat filter. A civilian on this server gets exactly the game's commands and nothing
 * else — no /me, no /trigger, no vanilla scrapyard. Staff and ops keep everything.
 *
 * <p>A whitelist rather than a blacklist on purpose: the set of commands the game means to
 * offer is small and known, while the set Paper ships grows with every version.
 *
 * <p>Two layers. {@link PlayerCommandSendEvent} trims the command tree the server sends a
 * civilian, so tab completion and Bedrock's /help (Geyser builds it from that same tree)
 * list only the game's commands. {@link PlayerCommandPreprocessEvent} then refuses anything
 * typed by hand that slipped past, since a client can send any command it likes.
 */
public final class CommandGuard implements Listener {

    /** Everything a civilian may run. Namespaced forms (minecraft:me) are stripped first. */
    private static final Set<String> ALLOWED = Set.of(
            "kit", "kits", "help", "stats",
            "msg", "tell", "whisper", "w",
            "feast", "game");

    private final StaffManager staff;

    public CommandGuard(StaffManager staff) {
        this.staff = staff;
    }

    /** Civilians are told about the whitelisted commands only, and never a namespaced form. */
    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (isPrivileged(event.getPlayer())) {
            return;
        }
        event.getCommands().removeIf(name -> !ALLOWED.contains(name.toLowerCase(Locale.ROOT)));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isPrivileged(player)) {
            return;
        }

        String message = event.getMessage();
        String label = message.substring(1);
        int space = label.indexOf(' ');
        if (space >= 0) {
            label = label.substring(0, space);
        }
        // "/minecraft:me hi" must not slip past a check that only knows "me".
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }

        if (ALLOWED.contains(label.toLowerCase(Locale.ROOT))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(Component.text("Unknown command. Try ", NamedTextColor.RED)
                .append(Component.text("/help", NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.RED)));
    }

    private boolean isPrivileged(Player player) {
        return player.isOp() || player.hasPermission("hardcoregames.admin")
                || staff.isStaff(player);
    }
}
