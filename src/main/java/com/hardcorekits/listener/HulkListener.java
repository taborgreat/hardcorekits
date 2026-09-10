package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.HulkKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Hulk's grip.
 *
 * <p>The carry is an ordinary passenger — the same mechanic as riding a horse, pointed at a
 * player. That is what makes the rest fall out for free: the client already draws it, the
 * victim already moves with the Hulk, and sneaking already dismounts, so the escape is a
 * one-liner rather than a movement handler.
 *
 * <p>The two halves are keyed off an empty main hand, which is the kit's whole cost: a Hulk
 * cannot hold a sword and grab in the same moment.
 *
 * <p>Left-click is read from {@link PlayerAnimationEvent} rather than an interact event because
 * it is the one that fires for every swing — at air, at a block, or at the player on your
 * shoulders — and swinging at nothing is exactly what a throw looks like.
 */
public final class HulkListener implements Listener {

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** When each Hulk may grab again, after a throw. */
    private final Map<UUID, Long> nextGrab = new HashMap<>();

    /**
     * Wind-up per carrying Hulk, 0..1. Built by holding crouch, spent by the throw.
     *
     * <p>Drawn on the XP bar's fill only — the level number stays the match kill count, which
     * the repaint on release restores.
     */
    private final Map<UUID, Float> charge = new HashMap<>();
    /** The per-carry ticker that grows the charge. One per Hulk, only while carrying. */
    private final Map<UUID, BukkitTask> windups = new HashMap<>();

    /**
     * Players whose dismount this listener caused by throwing them.
     *
     * <p>Every way out of the grip arrives as the same dismount event, so without this marker a
     * thrown player would also be told they wriggled free.
     */
    private final Set<UUID> thrown = new HashSet<>();

    public HulkListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset like every other kit's. */
    public void clearGrips() {
        nextGrab.clear();
        thrown.clear();
        charge.clear();
        windups.values().forEach(BukkitTask::cancel);
        windups.clear();
    }

    // ---------------------------------------------------------------- the lift

    @EventHandler(ignoreCancelled = true)
    public void onGrab(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // the off-hand fires its own copy of this event
        }
        Player hulk = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(hulk, HulkKit.ID) || !emptyHanded(hulk)) {
            return;
        }
        // Players and mobs alike — a cow is a shield, a creeper is a grenade with legs.
        if (!(event.getRightClicked() instanceof LivingEntity victim) || victim.equals(hulk)
                || victim instanceof org.bukkit.entity.ArmorStand) {
            return;
        }
        if (victim instanceof Player prey && !game.isAlive(prey)) {
            return;
        }
        if (!hulk.getPassengers().isEmpty()) {
            return; // one at a time
        }
        if (victim.getVehicle() != null || !victim.getPassengers().isEmpty()) {
            return; // no towers of players
        }

        long now = System.currentTimeMillis();
        Long until = nextGrab.get(hulk.getUniqueId());
        if (until != null && now < until) {
            hulk.sendActionBar(Component.text("Still catching your breath.", NamedTextColor.GRAY));
            return;
        }

        if (!hulk.addPassenger(victim)) {
            return;
        }
        event.setCancelled(true);

        hulk.getWorld().playSound(hulk.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0F, 1.2F);
        hulk.sendActionBar(Component.text("You have " + victim.getName()
                + ". Crouch to charge, left-click to throw.", NamedTextColor.GREEN));
        startWindup(hulk);
        if (victim instanceof Player prey) {
            prey.sendMessage(Component.text(hulk.getName() + " has picked you up. ",
                            NamedTextColor.RED)
                    .append(Component.text("Sneak to break free.", NamedTextColor.YELLOW)));
        }
    }

    // ---------------------------------------------------------------- the throw

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player hulk = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(hulk, HulkKit.ID) || !emptyHanded(hulk)) {
            return;
        }

        List<Entity> riders = hulk.getPassengers();
        if (riders.isEmpty()) {
            return;
        }
        for (Entity rider : riders) {
            if (rider instanceof LivingEntity victim) {
                throwPlayer(hulk, victim);
            }
        }
        nextGrab.put(hulk.getUniqueId(),
                System.currentTimeMillis() + game.config().hulkCooldownSeconds() * 1000L);
    }

    /**
     * Launches the passenger along the Hulk's line of sight.
     *
     * <p>The velocity is set a tick after the dismount on purpose. Leaving a vehicle moves the
     * rider itself, and anything written to their velocity during that same tick is overwritten
     * by the placement — which lands them at the Hulk's feet instead of across the valley.
     */
    private void throwPlayer(Player hulk, LivingEntity victim) {
        thrown.add(victim.getUniqueId());
        victim.leaveVehicle();

        // Base throw at zero charge, up to charged-multiplier at a full bar.
        double wound = charge.getOrDefault(hulk.getUniqueId(), 0.0F);
        double factor = 1.0D + (game.config().hulkChargedMultiplier() - 1.0D) * wound;

        Vector fling = hulk.getEyeLocation().getDirection().normalize()
                .multiply(game.config().hulkThrowPower() * factor)
                .setY(game.config().hulkThrowLift() * factor);
        endWindup(hulk);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (victim.isValid()) {
                victim.setVelocity(fling);
            }
        });

        hulk.getWorld().playSound(hulk.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 1.6F);
        if (victim instanceof Player prey) {
            prey.sendMessage(Component.text("You were thrown.", NamedTextColor.RED));
        }
    }

    // ---------------------------------------------------------------- getting free

    /**
     * Sneaking out of the grip.
     *
     * <p>A client riding a vehicle usually asks the server to dismount rather than sending a
     * sneak, so this may never fire — it is here for the case where the sneak does arrive, and
     * it only forces the dismount. Everything the players are told about it lives in
     * {@link #onDismount}, which is the one place both routes out of the grip pass through.
     */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player victim = event.getPlayer();
        if (victim.getVehicle() instanceof Player) {
            victim.leaveVehicle();
        }
    }

    /** The single exit from a grip: thrown, sneaked out of, or dropped. */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player victim)
                || !(event.getDismounted() instanceof Player hulk)) {
            return;
        }
        if (thrown.remove(victim.getUniqueId())) {
            return; // the throw already said its piece
        }
        endWindup(hulk);
        victim.sendMessage(Component.text("You wriggled free.", NamedTextColor.GREEN));
        hulk.sendActionBar(Component.text(victim.getName() + " broke free.", NamedTextColor.RED));
    }

    /**
     * A grip does not survive its owner.
     *
     * <p>Death here means being kicked for the rest of the match, so both handlers matter: the
     * carried player must not be left riding a corpse, and a Hulk logging out mid-carry must
     * not take their passenger's position with them.
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        release(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        release(event.getPlayer());
    }

    /** Drops whoever this player is carrying, and gets them off whoever is carrying them. */
    private void release(Player player) {
        for (Entity rider : player.getPassengers()) {
            rider.leaveVehicle();
        }
        if (player.getVehicle() instanceof Player) {
            player.leaveVehicle();
        }
        nextGrab.remove(player.getUniqueId());
        endWindup(player);
    }

    // ---------------------------------------------------------------- the wind-up

    /**
     * Grows the charge while the carrying Hulk holds crouch, and paints it on the XP bar.
     *
     * <p>Crouch rather than a click on purpose: an empty-hand right-click at air never reaches
     * the server, so a click-driven charge would only work while staring at a block. Held
     * crouch is a continuous, reliable signal — and a Hulk visibly winding up is its own
     * warning to everyone watching.
     *
     * <p>Only the bar's fill is used. The level number is the match kill count and is left
     * alone; {@code showKills} restores the fill to zero once the grip ends.
     */
    private void startWindup(Player hulk) {
        UUID uuid = hulk.getUniqueId();
        charge.put(uuid, 0.0F);
        hulk.setExp(0.0F);

        // The ticker runs every 2 ticks, so 10 steps a second.
        float perTick = (float) (1.0D / (game.config().hulkChargeSeconds() * 10.0D));
        BukkitTask old = windups.put(uuid, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!hulk.isOnline() || hulk.getPassengers().isEmpty()) {
                endWindup(hulk);
                return;
            }
            if (!hulk.isSneaking()) {
                return;
            }
            float wound = charge.merge(uuid, perTick, (a, b) -> Math.min(1.0F, a + b));
            hulk.setExp(Math.min(0.999F, wound));
            if (wound >= 1.0F) {
                hulk.playSound(hulk.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 2.0F);
            }
        }, 2L, 2L));
        if (old != null) {
            old.cancel();
        }
    }

    /** Ends the wind-up however the grip ended, and hands the XP bar back to the kill count. */
    private void endWindup(Player hulk) {
        charge.remove(hulk.getUniqueId());
        BukkitTask task = windups.remove(hulk.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        if (hulk.isOnline()) {
            game.showKills(hulk);
        }
    }

    /** The ability is the fist. Anything in the main hand switches it off. */
    private boolean emptyHanded(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.AIR;
    }
}
