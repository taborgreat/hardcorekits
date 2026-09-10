package com.hardcorekits.listener;

import com.hardcorekits.game.GameManager;
import com.hardcorekits.util.Damage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
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
        if (Damage.isExplosion(event)) {
            event.setCancelled(true);
        }
    }

    /**
     * Pre-game, every container stays shut: chests, furnaces, villager trades, all of it.
     *
     * <p>The lobby is look-but-do-not-touch, and that includes looking <em>inside</em> things
     * or striking deals with the locals. Blanket by design — the event never fires for a
     * player's own inventory, so cancelling everything else is exactly "see everything, do
     * nothing". Once the match drops the world is fair game, invincibility included.
     */
    @EventHandler(ignoreCancelled = true)
    public void onOpenContainer(InventoryOpenEvent event) {
        if (!game.state().isPreGame()) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)
                || player.hasPermission("hardcoregames.admin")) {
            return;
        }
        event.setCancelled(true);
        player.sendActionBar(Component.text("The game has not started. Look, but do not touch.",
                NamedTextColor.RED));
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
        if (game.state().isPreGame() && !event.getPlayer().hasPermission("hardcoregames.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("hardcoregames.admin")) {
            return;
        }
        if (game.state().isPreGame()) {
            event.setCancelled(true);
            return;
        }

        // The build ceiling. Towering is a legitimate move — it is how you break line of sight,
        // escape a melee, or answer a Stomper — so the limit is set high enough that a normal
        // tower still works, and only stops the sky pillar nobody can reach or fight.
        //
        // Deliberately an absolute height rather than a height above the local ground: the
        // ground moves as you build on it, so a relative rule is one a player climbs out of a
        // block at a time. The cost is that the tallest natural peaks are already at the
        // ceiling, which is the right way round — high terrain is an advantage you have to
        // walk to, not one you carry a stack of dirt to.
        if (event.getBlock().getY() > game.config().maxBuildHeight()) {
            event.setCancelled(true);
            // Action bar, not chat: this fires on every held-down placement attempt.
            player.sendActionBar(Component.text(
                    "Build limit. Nothing goes above y=" + game.config().maxBuildHeight() + ".",
                    NamedTextColor.RED));
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
                && !player.hasPermission("hardcoregames.admin")) {
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
                && !player.hasPermission("hardcoregames.admin")) {
            event.setCancelled(true);
        }
    }

    /**
     * Mobs only ever hunt a living tribute in a live match.
     *
     * <p>Pre-game the lobby crowd is scenery to the world as much as the world is scenery to
     * them: a zombie that has to be kited around the centre while people pick kits is a
     * nuisance, and invincibility makes the aggro pointless anyway. The same applies to anyone
     * who is not in the alive set during a match — a mod in mod mode is not a target — and to
     * everyone once a winner is being celebrated. Cancelling the target event makes the mob
     * forget the player rather than merely miss, so it wanders off instead of following.
     */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        if (!game.state().isLive() || !game.isAlive(player)) {
            event.setCancelled(true);
        }
    }

    /** The Nether and The End are disabled — nobody gets to hide in another dimension. */
    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        event.setCancelled(true);
    }
}
