package gg.hungergames.listener;

import gg.hungergames.game.GameManager;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerPortalEvent;

/**
 * Enforces the rules of the current state. Everything here is "check state, then cancel" —
 * no game logic lives in this class.
 */
public final class ProtectionListener implements Listener {

    private final GameManager game;

    public ProtectionListener(GameManager game) {
        this.game = game;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // Ability-granted grace, e.g. the seconds after an Endermage portal drag.
        if (game.isImmune(player)) {
            event.setCancelled(true);
            return;
        }
        // "Everyone is invincible" is meant literally: nothing may hurt a player until PvP
        // goes live. Filtering to entity-dealt damage is not enough — players are dropped
        // twenty blocks onto the map, so fall damage alone would gut the grace period, and a
        // Demoman mine has no damaging entity either.
        if (!game.state().isPvpEnabled()) {
            event.setCancelled(true);
        }
    }

    /**
     * Explosions never destroy dropped loot.
     *
     * <p>Several kits detonate exactly where someone just died — a Demoman mine that killed
     * them, a Tank's kill charge — and a blast that vaporises the reward it just earned is a
     * bad trade for everyone. This is a game-wide rule rather than something each exploding kit
     * remembers to handle, so any future one gets it for free.
     */
    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!game.state().isLive()) {
            event.setCancelled(true);
        }
    }

    /**
     * Pre-game the map is look-but-don't-touch. Inventories are wiped at match start anyway,
     * so the point is preserving the terrain rather than preventing stockpiling.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (game.state().isPreGame() && !event.getPlayer().hasPermission("hungergames.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (game.state().isPreGame() && !event.getPlayer().hasPermission("hungergames.admin")) {
            event.setCancelled(true);
        }
    }

    /**
     * Pre-game the map is scenery, and that includes the things living on it.
     *
     * <p>Adventure mode stops the blocks being broken but not the cows being killed, so the
     * lobby would otherwise be a slaughterhouse with the corpses still there when the match
     * starts. Only players are stopped — a zombie is free to burn at dawn.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamageEntity(EntityDamageByEntityEvent event) {
        if (game.state().isPreGame() && event.getDamager() instanceof Player player
                && !player.hasPermission("hungergames.admin")) {
            event.setCancelled(true);
        }
    }

    /**
     * The gap adventure mode leaves: trampled farmland, and anything else a player changes by
     * walking into it rather than by breaking it.
     */
    @EventHandler(ignoreCancelled = true)
    public void onTrample(EntityChangeBlockEvent event) {
        if (game.state().isPreGame() && event.getEntity() instanceof Player player
                && !player.hasPermission("hungergames.admin")) {
            event.setCancelled(true);
        }
    }

    /** The Nether and The End are disabled — nobody gets to hide in another dimension. */
    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        event.setCancelled(true);
    }
}
