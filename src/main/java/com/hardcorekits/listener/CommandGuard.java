package com.hardcorekits.listener;

import com.hardcorekits.staff.StaffManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;

/**
 * The bloat filter. A civilian on this server gets exactly the game's commands and nothing
 * else — no /me, no /trigger, no vanilla scrapyard. Staff and ops keep everything.
 *
 * <p>A whitelist rather than a blacklist on purpose: the set of commands the game means to
 * offer is small and known, while the set Paper ships grows with every version.
 */
public final class CommandGuard implements Listener {

    /** Everything a civilian may run. Namespaced forms (minecraft:me) are stripped first. */
    private static final Set<String> ALLOWED = Set.of(
            "kit", "kits", "help", "stats", "kills",
            "msg", "tell", "whisper", "w",
            "feast", "game");

    private final StaffManager staff;

    public CommandGuard(StaffManager staff) {
        this.staff = staff;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("hardcoregames.admin")
                || staff.isStaff(player)) {
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
}
