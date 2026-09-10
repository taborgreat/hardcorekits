package com.hardcorekits.listener;

import com.hardcorekits.game.GameManager;
import com.hardcorekits.staff.StaffManager;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Enforces the join rules. The server is open before a match; once one starts, the door
 * stays ajar for exactly three kinds of arrival: participants reconnecting inside the grace
 * window, latecomers while invincibility still has more than the cutoff left (they play, but
 * kitless), and staff — who walk in any time and land in mod mode.
 */
public final class ConnectionListener implements Listener {

    private final GameManager game;
    private final StaffManager staff;

    public ConnectionListener(GameManager game, StaffManager staff) {
        this.game = game;
        this.staff = staff;
    }

    /**
     * Replaces the deprecated {@code PlayerLoginEvent} — this is Paper's hook for pre-login
     * ban/authentication checks. Note it runs off the main thread.
     */
    @EventHandler
    public void onValidateLogin(PlayerConnectionValidateLoginEvent event) {
        if (!(event.getConnection() instanceof PlayerLoginConnection login)) {
            return;
        }
        PlayerProfile profile = login.getAuthenticatedProfile();
        if (profile == null || profile.getId() == null) {
            return; // not authenticated yet; nothing to match against
        }
        UUID uuid = profile.getId();
        Component denial = game.loginDenialReason(uuid, profile.getName());
        if (denial != null) {
            event.kickMessage(denial);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        game.applyCombatAttributes(player);

        if (game.state().isPreGame()) {
            game.prepareForLobby(player);
            // The whole greeting, red, and nothing else — anything more belongs in /help.
            player.sendMessage(Component.text("Welcome to Hardcore Games.", NamedTextColor.RED));
            player.sendMessage(Component.text(
                    "Pick a kit directly with /kit name, view all kits with /kits, "
                            + "or get /help.", NamedTextColor.RED));
            staff.handleJoin(player);
            return;
        }

        if (game.isAlive(player)) {
            // Reconnecting inside the grace window.
            game.handleRejoin(player);
            staff.handleJoin(player); // paints the red name; alive staff are never forced out
            return;
        }

        // Not a participant and a match is running. Civilians only get here inside the
        // late-join window; anyone else through the login gate is staff, and handleJoin
        // drops them straight into mod mode.
        if (!staff.isStaff(player) && game.canLateJoin()) {
            game.handleLateJoin(player);
            return;
        }
        staff.handleJoin(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        staff.handleQuit(event.getPlayer());
        game.handleQuit(event.getPlayer());
        if (game.state().isPreGame()) {
            game.announceLobbyNeed();
        }
    }
}
