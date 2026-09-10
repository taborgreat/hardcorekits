package com.hardcorekits.listener;

import com.hardcorekits.game.GameManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;

/**
 * The XP bar is a kill counter, so nothing else is allowed to write to it.
 *
 * <p>Orbs from mobs, ore and bottles would otherwise push the level up and make the number a
 * lie. They are zeroed at HIGHEST, after every other listener has read the amount: the
 * Barbarian's ledger still counts ambient XP towards Tyrfing, the player's bar simply never
 * sees it.
 *
 * <p>Nothing in the game reads levels, which is what makes the bar free to borrow.
 */
public final class KillCounterListener implements Listener {

    private final GameManager game;

    public KillCounterListener(GameManager game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        event.setAmount(0);
        // Repaint rather than trust: the pickup may already have nudged the bar.
        game.showKills(event.getPlayer());
    }
}
