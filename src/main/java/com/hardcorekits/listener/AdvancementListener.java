package com.hardcorekits.listener;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Turns advancements off.
 *
 * <p>A match is twenty minutes long on a world that is thrown away afterwards, so "Getting
 * Wood" popping up mid-fight is noise at best and a screen-blocking toast at worst.
 *
 * <p>Cancelling the criterion grant is what actually stops them: the announcement gamerule only
 * silences the chat line, and the toast still lands. With no criterion ever granted there is no
 * advancement to announce or display in the first place.
 */
public final class AdvancementListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCriterion(PlayerAdvancementCriterionGrantEvent event) {
        event.setCancelled(true);
    }
}
