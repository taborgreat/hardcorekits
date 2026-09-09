package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameConfig;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.TimelordKit;
import gg.hungergames.util.Interact;
import gg.hungergames.util.Phases;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
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

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Frozen player -> the task that will thaw them. */
    private final Map<UUID, BukkitTask> frozen = new HashMap<>();
    /** Frozen mob -> the task that gives its AI back. */
    private final Map<UUID, BukkitTask> stilled = new HashMap<>();

    public TimelordListener(HungerGames plugin, GameManager game, KitRegistry kits) {
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
        if (!game.state().isLive() || !kits.hasKit(caster, TimelordKit.ID)) {
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

        // The spell's own duration is dead time too, so the watch is spent for both.
        caster.setCooldown(TimelordKit.WATCH, (duration + config.timelordCooldownSeconds()) * 20);
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
