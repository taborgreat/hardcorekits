package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.staff.StaffManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The lightest possible anticheat: it watches, it tells staff, it touches nothing.
 *
 * <p>Two checks only — melee reach and sustained hover — because those are the two things a
 * legitimate client cannot do and a cheating one advertises. No packet inspection, no combat
 * heuristics, no auto-punishment: every finding is one {@code [STAFF]} line, and a human with
 * the ban hammer decides what it meant. Deliberately deaf to everything the kits make legal:
 * a Kangaroo mid-hop or a Launcher off a pad is <em>falling</em> within a second, and the
 * hover check demands seconds of not-falling in a row before it says a word.
 */
public final class WatchdogListener implements Listener {

    /** One check runs per second per player; this is that clock, in ticks. */
    private static final long SWEEP_TICKS = 20L;
    /** Seconds between repeated flags for the same player, so staff chat is not flooded. */
    private static final long FLAG_COOLDOWN_MILLIS = 30_000L;

    private final HardcoreGames plugin;
    private final GameManager game;
    private final StaffManager staff;

    /** Consecutive sweeps each player has spent hovering. */
    private final Map<UUID, Integer> hoverStreak = new ConcurrentHashMap<>();
    /** When each player may next be flagged, per check kind. */
    private final Map<String, Long> flagMuteUntil = new ConcurrentHashMap<>();

    public WatchdogListener(HardcoreGames plugin, GameManager game, StaffManager staff) {
        this.plugin = plugin;
        this.game = game;
        this.staff = staff;
    }

    public void start() {
        if (!game.config().watchdogEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, SWEEP_TICKS, SWEEP_TICKS);
    }

    public void clearState() {
        hoverStreak.clear();
        flagMuteUntil.clear();
    }

    // ---------------------------------------------------------------- reach

    /**
     * A landed melee hit from further than any client can legitimately swing.
     *
     * <p>Measured eye-to-hitbox at the moment of damage, with the threshold well above
     * vanilla's ~3 blocks — lag and hitbox edges eat the difference, and a watchdog that
     * cries at 3.1 blocks of latency is one that gets turned off.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!game.config().watchdogEnabled() || !game.state().isPvpEnabled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (event.getCause() != EntityDamageByEntityEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageByEntityEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        double distance = attacker.getEyeLocation()
                .distance(victim.getBoundingBox().getCenter().toLocation(victim.getWorld()));
        double limit = game.config().watchdogMaxReach();
        if (distance > limit) {
            flag(attacker, "reach", String.format("hit %s from %.1f blocks, limit %.1f",
                    victim.getName(), distance, limit));
        }
    }

    // ---------------------------------------------------------------- flight

    /**
     * One sweep of the hover check.
     *
     * <p>A player counts as hovering when they are airborne with nothing under them for two
     * blocks and are not actually descending — and none of the honest reasons apply: no
     * water, no ladder, no levitation or slow falling, no elytra, no vehicle, no post-launch
     * immunity from a kit. A Kangaroo hop or Launcher bounce spends almost all its arc
     * falling, so the streak dies long before it reaches the threshold.
     */
    private void sweep() {
        if (!game.state().isLive()) {
            if (!hoverStreak.isEmpty()) {
                hoverStreak.clear();
            }
            return;
        }
        int threshold = Math.max(2, game.config().watchdogHoverSeconds());
        for (Player player : game.alivePlayers()) {
            if (staff.isModMode(player) || player.getAllowFlight()) {
                hoverStreak.remove(player.getUniqueId());
                continue;
            }
            if (!isHovering(player)) {
                hoverStreak.remove(player.getUniqueId());
                continue;
            }
            int streak = hoverStreak.merge(player.getUniqueId(), 1, Integer::sum);
            if (streak >= threshold) {
                hoverStreak.remove(player.getUniqueId());
                flag(player, "fly", "hovering in the air for " + streak + "s at "
                        + player.getLocation().getBlockX() + ", "
                        + player.getLocation().getBlockY() + ", "
                        + player.getLocation().getBlockZ());
            }
        }
    }

    private boolean isHovering(Player player) {
        if (player.isOnGround() || player.isGliding() || player.isSwimming()
                || player.isInsideVehicle() || player.isClimbing()
                || player.isInWater() || player.isInLava()) {
            return false;
        }
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            return false;
        }
        if (player.getVelocity().getY() < -0.2D) {
            return false; // genuinely falling
        }
        // Cobwebs, scaffolding and powder snow all hold a player mid-air legitimately.
        Material inside = player.getLocation().getBlock().getType();
        if (inside == Material.COBWEB || inside == Material.SCAFFOLDING
                || inside == Material.POWDER_SNOW) {
            return false;
        }
        // Two blocks of clearance below, or they are just walking off an edge.
        Block below = player.getLocation().getBlock();
        for (int i = 1; i <= 2; i++) {
            Block check = below.getRelative(0, -i, 0);
            if (!check.isPassable()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- reporting

    /** One line to staff, throttled per player per check. Never anything more. */
    private void flag(Player suspect, String kind, String detail) {
        String key = suspect.getUniqueId() + ":" + kind;
        long now = System.currentTimeMillis();
        Long mutedUntil = flagMuteUntil.get(key);
        if (mutedUntil != null && now < mutedUntil) {
            return;
        }
        flagMuteUntil.put(key, now + FLAG_COOLDOWN_MILLIS);
        staff.notifyStaff("[watchdog] " + suspect.getName() + ": " + detail);
        plugin.getLogger().info("[watchdog] " + suspect.getName() + " (" + kind + "): " + detail);
    }
}
