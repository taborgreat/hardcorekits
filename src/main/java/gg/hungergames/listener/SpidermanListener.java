package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.SpidermanKit;
import gg.hungergames.util.CooldownBar;
import gg.hungergames.util.Phases;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Spiderman's webbing.
 *
 * <p>Any snowball this kit throws bursts into a 2x2 patch of cobweb where it lands — only
 * over air, so terrain is dressed in web rather than replaced by it. The burst-then-rest
 * rhythm is the Thor pattern: three throws on demand, then the arm rests, with the rest drawn
 * on the XP bar. A throw refused by the cooldown is cancelled before the snowball is spent.
 *
 * <p>Standing in any cobweb grants the Spiderman a heavy Speed boost — webs are home ground, so the same
 * patch that traps the chase is a lane for its maker. Note the webs are ordinary blocks:
 * they can be broken, they drop nothing, and the world rotation sweeps them away with
 * everything else.
 */
public final class SpidermanListener implements Listener {

    /** How long a landed web patch stands before it evaporates. */
    private static final long WEB_LIFETIME_SECONDS = 20L;
    private static final int WEB_SPEED_TICKS = 40;
    /** Webs inside this box around a Spiderman are ghosted to air on their client. */
    private static final int GHOST_RADIUS = 3;
    /** Squared distance past which a ghosted web is shown to them again. */
    private static final double RESTORE_DISTANCE_SQUARED = 36.0D;
    private static final org.bukkit.block.data.BlockData AIR_DATA =
            Material.AIR.createBlockData();

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Throws spent in the current burst. */
    private final Map<UUID, Integer> spent = new HashMap<>();
    /** Arms currently resting. */
    private final Map<UUID, Long> restingUntil = new HashMap<>();
    /** Blocks this listener turned into web, so only its own work is ever reverted. */
    private final Set<Location> webs = new HashSet<>();
    /** Per Spiderman, the webs currently ghosted to air on their client. */
    private final Map<UUID, Set<Location>> hiddenWebs = new HashMap<>();

    public SpidermanListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset like every other kit's. */
    public void clearWebs() {
        for (Location location : webs) {
            if (location.getBlock().getType() == Material.COBWEB) {
                location.getBlock().setType(Material.AIR);
            }
        }
        webs.clear();
        spent.clear();
        restingUntil.clear();
        hiddenWebs.clear();
    }

    // ---------------------------------------------------------------- the throw

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball ball)
                || !(ball.getShooter() instanceof Player player)) {
            return;
        }
        if (!game.state().isLive() || !kits.hasKit(player, SpidermanKit.ID)) {
            return; // everyone else throws ordinary snow
        }
        // During the grace period the throw is refused outright rather than ignored —
        // ignoring it let vanilla spend the snowball as plain snow, which is a wasted web.
        if (!kits.canUseAbility(player, SpidermanKit.ID)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Webs unlock when invincibility ends.",
                    NamedTextColor.RED));
            return;
        }

        long now = System.currentTimeMillis();
        Long until = restingUntil.get(player.getUniqueId());
        if (until != null && now < until) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Your webs have "
                    + ((until - now) / 1000L + 1) + "s cooldown left!", NamedTextColor.RED));
            return;
        }

        int used = spent.merge(player.getUniqueId(), 1, Integer::sum);
        if (used >= game.config().spidermanBurst()) {
            int cooldown = game.config().spidermanCooldownSeconds();
            spent.remove(player.getUniqueId());
            restingUntil.put(player.getUniqueId(), now + cooldown * 1000L);
            CooldownBar.show(plugin, game, player, cooldown);
        }
    }

    // ---------------------------------------------------------------- the web

    @EventHandler(ignoreCancelled = true)
    public void onLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball ball)
                || !(ball.getShooter() instanceof Player player)
                || !kits.hasKit(player, SpidermanKit.ID)
                || !game.state().isLive()) {
            return;
        }

        Block centre = event.getHitEntity() != null
                ? event.getHitEntity().getLocation().getBlock()
                : ball.getLocation().getBlock();

        // A 2x2 footprint, one high: the impact corner plus its three neighbours. Air only,
        // so a wall takes a web on its face rather than losing blocks to one.
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                Block block = centre.getRelative(dx, 0, dz);
                if (!block.getType().isAir()) {
                    continue;
                }
                block.setType(Material.COBWEB);
                Location location = block.getLocation();
                webs.add(location);
                Phases.delayed(plugin, WEB_LIFETIME_SECONDS, () -> {
                    webs.remove(location);
                    if (location.getBlock().getType() == Material.COBWEB) {
                        location.getBlock().setType(Material.AIR);
                    }
                });
            }
        }
    }

    // ---------------------------------------------------------------- home ground

    /**
     * Webs are nothing to a Spiderman — mechanically nothing.
     *
     * <p>Speed alone cannot do this: a cobweb multiplies movement down <em>after</em> speed is
     * applied, and the crawl is computed on the client from the blocks the client can see. So
     * the trick is played on exactly that: every web within a few blocks of a Spiderman is
     * sent to <em>their</em> client as air. Their physics never meets a web at all — they walk
     * through at full stride — while the server, and everyone else's client, keeps the real
     * block doing its real job. Walk away and the web is sent back, so the field still reads
     * true from a distance.
     *
     * <p>Checked with {@code hasKit}, not the ability gate: this is a passive perk, and being
     * webbed during the grace period should not also mean being stuck in it.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.hasKit(player, SpidermanKit.ID)) {
            return;
        }

        Set<Location> hidden = hiddenWebs.computeIfAbsent(player.getUniqueId(),
                ignored -> new HashSet<>());
        Block feet = event.getTo().getBlock();

        // Hide every web within reach of the next few strides.
        for (int dx = -GHOST_RADIUS; dx <= GHOST_RADIUS; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -GHOST_RADIUS; dz <= GHOST_RADIUS; dz++) {
                    Block block = feet.getRelative(dx, dy, dz);
                    if (block.getType() == Material.COBWEB
                            && hidden.add(block.getLocation())) {
                        player.sendBlockChange(block.getLocation(), AIR_DATA);
                    }
                }
            }
        }

        // And put back the ones left behind, so distant webs are still visible traps.
        hidden.removeIf(location -> {
            if (location.distanceSquared(feet.getLocation()) <= RESTORE_DISTANCE_SQUARED) {
                return false;
            }
            player.sendBlockChange(location, location.getBlock().getBlockData());
            return true;
        });

        // The boost on top, for crossing a webbed area faster than anyone who has to fight it.
        if (feet.getType() == Material.COBWEB) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, WEB_SPEED_TICKS,
                    Math.max(0, game.config().spidermanWebSpeedLevel() - 1), true, false));
        }
    }
}
