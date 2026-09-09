package gg.hungergames.listener;

import gg.hungergames.game.GameManager;
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
 * Enforces the join rules: the server is open only before a match starts, and once it does
 * the only people let back in are participants reconnecting inside the grace window.
 */
public final class ConnectionListener implements Listener {

    private final GameManager game;

    public ConnectionListener(GameManager game) {
        this.game = game;
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
        Component denial = game.loginDenialReason(uuid);
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
            player.sendMessage(Component.text("Welcome to Hunger Games.", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Type ", NamedTextColor.YELLOW)
                    .append(Component.text("/kits", NamedTextColor.AQUA))
                    .append(Component.text(" to choose and then ", NamedTextColor.YELLOW))
                    .append(Component.text("/kit (name)", NamedTextColor.AQUA))
                    .append(Component.text(".", NamedTextColor.YELLOW)));
            game.announceLobbyNeed();
        } else {
            // Only reachable for someone reconnecting inside the grace window.
            game.handleRejoin(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        game.handleQuit(event.getPlayer());
        if (game.state().isPreGame()) {
            game.announceLobbyNeed();
        }
    }
}
