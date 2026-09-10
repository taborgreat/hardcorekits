package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameConfig;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.TimelordKit;
import com.hardcorekits.util.Interact;
import com.hardcorekits.util.CooldownBar;
import com.hardcorekits.util.Phases;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Timelord's spell.
 *
 * <p>Freezing is done by refusing the movement, not by stacking effects on the victim: a frozen
 * player's {@link PlayerMoveEvent} is pinned back to where they stood, with their yaw and pitch
 * let through so they can still look and fight. Nothing is written to the player that could
 * outlive the spell — if the plugin dies mid-freeze, everyone simply walks away.
 *
 * <p>Mobs are the exception: they lose their AI for the duration and get it back afterwards.
 */
public final class TimelordListener implements Listener {

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Frozen player -> the task that will thaw them. */
    private final Map<UUID, BukkitTask> frozen = new HashMap<>();
    /** Frozen mob -> the task that gives its AI back. */
    private final Map<UUID, BukkitTask> stilled = new HashMap<>();

    public TimelordListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Thaws everyone. Per-match state, dropped on reset like every other kit's. */
    public void releaseAll() {
        frozen.values().forEach(Phases::cancel);
        frozen.clear();
        stilled.forEach((uuid, task) -> {
            Phases.cancel(task);
            restoreAi(uuid);
        });
        stilled.clear();
    }

    // ---------------------------------------------------------------- casting

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != TimelordKit.WATCH) {
            return;
        }
        Player caster = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(caster, TimelordKit.ID)) {
            return; // anyone else is just checking the time
        }
        if (action == Action.RIGHT_CLICK_BLOCK && Interact.opensBlock(event)) {
            return;
        }
        if (caster.hasCooldown(TimelordKit.WATCH)) {
            return; // the watch draws its own cooldown sweep
        }

        cast(caster);
    }

    private void cast(Player caster) {
        GameConfig config = game.config();
        double radius = config.timelordRadius();
        int duration = config.timelordDurationSeconds();
        Location origin = caster.getLocation();

        int caught = 0;
        for (Entity entity : caster.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(caster)) {
                continue;
            }
            if (living instanceof Player player) {
                if (!game.isAlive(player)) {
                    continue;
                }
                freeze(player, duration);
                player.sendMessage(Component.text("Time freezes around you!", NamedTextColor.AQUA));
                caught++;
            } else {
                still(living, duration);
            }
        }

        // The spell's own duration is dead time too, so the watch is spent for both — on the
        // item's sweep and on the XP bar alike.
        int totalCooldown = duration + config.timelordCooldownSeconds();
        caster.setCooldown(TimelordKit.WATCH, totalCooldown * 20);
        CooldownBar.show(plugin, game, caster, totalCooldown);
        caster.getWorld().playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 2.0F);
        caster.sendMessage(Component.text(caught == 0
                        ? "Time freezes. Nobody was close enough to catch."
                        : "Time freezes. " + caught + " caught.",
                NamedTextColor.AQUA));
    }

    // ---------------------------------------------------------------- being frozen

    private void freeze(Player player, int seconds) {
        UUID uuid = player.getUniqueId();
        Phases.cancel(frozen.remove(uuid));
        frozen.put(uuid, Phases.delayed(plugin, seconds, () -> thaw(uuid)));

        // Being frozen should look frozen: a chime, a burst of ice on arrival, and a slow
        // snowfall around the victim for the whole hold. The shimmer task ends itself the
        // moment the freeze does, however it ends.
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_PLACE, 1.0F, 0.6F);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                player.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.4D, 0.9D, 0.4D, 0.02D);
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!frozen.containsKey(uuid) || !player.isOnline()) {
                player.getWorld().spawnParticle(Particle.CLOUD,
                        player.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.3D, 0.6D, 0.3D, 0.01D);
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8F, 1.4F);
                task.cancel();
                return;
            }
            player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    player.getLocation().add(0.0D, 1.2D, 0.0D), 6, 0.35D, 0.8D, 0.35D, 0.0D);
        }, 10L, 10L);
    }

    private void thaw(UUID uuid) {
        Phases.cancel(frozen.remove(uuid));
    }

    private void still(LivingEntity mob, int seconds) {
        UUID uuid = mob.getUniqueId();
        Phases.cancel(stilled.remove(uuid));
        mob.setAI(false);
        stilled.put(uuid, Phases.delayed(plugin, seconds, () -> {
            stilled.remove(uuid);
            restoreAi(uuid);
        }));
    }

    private void restoreAi(UUID uuid) {
        Entity entity = plugin.getServer().getEntity(uuid);
        if (entity instanceof LivingEntity living) {
            living.setAI(true);
        }
    }

    private boolean isFrozen(Player player) {
        return frozen.containsKey(player.getUniqueId());
    }

    /**
     * Pins a frozen player in place while letting them turn.
     *
     * <p>Teleports arrive here too — {@code PlayerTeleportEvent} is a move — so a frozen player
     * cannot pearl out of the spell either.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer())) {
            return;
        }
        if (!game.state().isLive()) {
            thaw(event.getPlayer().getUniqueId());
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return; // looking around is free
        }
        Location held = from.clone();
        held.setYaw(to.getYaw());
        held.setPitch(to.getPitch());
        event.setTo(held);
    }

    /** Frozen means frozen: no digging out and no pillaring away. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Hitting a frozen player releases them — the Timelord picks who gets to move. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && isFrozen(victim)) {
            thaw(victim.getUniqueId());
            victim.sendMessage(Component.text("Time starts again.", NamedTextColor.AQUA));
        }
    }
}
