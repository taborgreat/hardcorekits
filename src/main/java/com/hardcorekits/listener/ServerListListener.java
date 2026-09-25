package com.hardcorekits.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.hardcorekits.game.GameConfig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Rewrites the version string in the multiplayer-list ping.
 *
 * <p>The server runs 26.2 and ViaVersion translates 26.3 clients onto it, so Paper's own
 * "Paper 26.2" is only half the answer. That string is what server list sites publish, and
 * what a client too old to join sees in red beside the ping bars, so it is worth saying the
 * whole range there.
 *
 * <p>Only the name is touched. The protocol NUMBER in the ping belongs to ViaVersion, which
 * answers each client with that client's own protocol — that is the thing stopping a 26.3
 * client from calling this server outdated. Setting it here would undo that.
 */
public final class ServerListListener implements Listener {

    private final GameConfig config;

    public ServerListListener(GameConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        String name = config.versionName();
        if (!name.isEmpty()) {
            event.setVersion(name);
        }
    }
}
