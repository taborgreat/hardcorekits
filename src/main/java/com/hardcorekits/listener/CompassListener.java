package com.hardcorekits.listener;

import com.hardcorekits.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Click the compass to lock on to the nearest opponent. Either mouse button, at a block or at
 * the open air.
 *
 * <p>The target is a <em>snapshot</em>: the compass points at where they were when you
 * clicked, not where they are now. Following it to the end and finding nothing means they
 * have moved — or that they are directly below you.
 *
 * <p>Opponents closer than {@code compass-min-distance} are ignored, so the compass cannot
 * be used as a close-range radar during a fight.
 *
 * <p>Both halves of that — what counts as a compass click, and who it locks on to — are shared
 * with {@link SpyListener}, so the Spy's extra intel always describes the player the compass
 * actually picked rather than a second opinion about it.
 */
public final class CompassListener implements Listener {

    private final GameManager game;

    /** Player -> server tick of the last click the compass answered. */
    private final Map<UUID, Integer> answered = new HashMap<>();

    public CompassListener(GameManager game) {
        this.game = game;
    }

    /** Dropped on reset, like any other per-match state. */
    public void clearState() {
        answered.clear();
    }

    /**
     * Whether this player's compass answered a click on this very tick. The Spy asks, so its
     * extra line appears exactly when the ordinary one did and never on a swallowed repeat.
     */
    boolean answeredThisTick(Player player) {
        Integer tick = answered.get(player.getUniqueId());
        return tick != null && tick == Bukkit.getCurrentTick();
    }

    /**
     * Deliberately NOT {@code ignoreCancelled}. Bukkit delivers every click at the open air
     * already "cancelled" (there is no block to use), so that flag silently threw away every
     * air click and the compass only ever answered when aimed at a block.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isCompassClick(event, game)) {
            return;
        }

        Player player = event.getPlayer();
        int now = Bukkit.getCurrentTick();
        Integer last = answered.get(player.getUniqueId());
        if (last != null && now - last < game.config().compassCooldownTicks()) {
            return; // the same click arriving twice, or a held button
        }
        answered.put(player.getUniqueId(), now);

        Player target = nearestOpponent(game, player);
        if (target == null) {
            // Two very different situations read the same to the player otherwise: everyone
            // left is standing on top of you, versus there being nobody left to point at.
            player.sendMessage(Component.text(
                    hasOpponents(game, player) ? "All players are nearby." : "No valid targets.",
                    NamedTextColor.YELLOW));
            return;
        }

        player.setCompassTarget(target.getLocation());
        player.sendMessage(Component.text("Compass pointing at " + target.getName(),
                NamedTextColor.YELLOW));
    }

    /**
     * A live participant clicking with a compass in the main hand: left or right, at a block
     * or at the air. Counted once rather than once per hand.
     */
    static boolean isCompassClick(PlayerInteractEvent event, GameManager game) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return false; // otherwise this fires twice, once per hand
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR) {
            return false;
        }
        // An air click arrives with the BLOCK half denied and the item half untouched. Only a
        // listener that really cancelled the click denies the item, and that is respected.
        if (event.useItemInHand() == Event.Result.DENY) {
            return false;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COMPASS) {
            return false;
        }
        return game.state().isLive() && game.isAlive(event.getPlayer());
    }

    /**
     * Whether anyone is left to track at all, regardless of how close they are.
     *
     * <p>Used only to tell "they are all too close to lock on to" apart from "there is nobody
     * left" — which matters most in the last couple of minutes of a match, when a silent
     * "no valid targets" would read as the compass being broken.
     */
    static boolean hasOpponents(GameManager game, Player self) {
        for (Player other : game.alivePlayers()) {
            if (!other.equals(self) && other.getWorld().equals(self.getWorld())) {
                return true;
            }
        }
        return false;
    }

    /** Nearest living opponent at least {@code compass-min-distance} blocks away. */
    static Player nearestOpponent(GameManager game, Player self) {
        double minimum = game.config().compassMinDistance();
        double minimumSquared = minimum * minimum;

        Player best = null;
        double bestSquared = Double.MAX_VALUE;

        for (Player other : game.alivePlayers()) {
            if (other.equals(self) || !other.getWorld().equals(self.getWorld())) {
                continue;
            }
            double distanceSquared = other.getLocation().distanceSquared(self.getLocation());
            if (distanceSquared < minimumSquared || distanceSquared >= bestSquared) {
                continue;
            }
            bestSquared = distanceSquared;
            best = other;
        }
        return best;
    }
}
