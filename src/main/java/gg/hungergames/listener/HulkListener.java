package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.HulkKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
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

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** When each Hulk may grab again, after a throw. */
    private final Map<UUID, Long> nextGrab = new HashMap<>();

    /**
     * Players whose dismount this listener caused by throwing them.
     *
     * <p>Every way out of the grip arrives as the same dismount event, so without this marker a
     * thrown player would also be told they wriggled free.
     */
    private final Set<UUID> thrown = new HashSet<>();

    public HulkListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset like every other kit's. */
    public void clearGrips() {
        nextGrab.clear();
        thrown.clear();
    }

    // ---------------------------------------------------------------- the lift

    @EventHandler(ignoreCancelled = true)
    public void onGrab(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // the off-hand fires its own copy of this event
        }
        Player hulk = event.getPlayer();
        if (!game.state().isLive() || !kits.hasKit(hulk, HulkKit.ID) || !emptyHanded(hulk)) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player victim) || victim.equals(hulk)) {
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
                + ". Left-click to throw.", NamedTextColor.GREEN));
        victim.sendMessage(Component.text(hulk.getName() + " has picked you up. ",
                        NamedTextColor.RED)
                .append(Component.text("Sneak to break free.", NamedTextColor.YELLOW)));
    }

    // ---------------------------------------------------------------- the throw

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player hulk = event.getPlayer();
        if (!game.state().isLive() || !kits.hasKit(hulk, HulkKit.ID) || !emptyHanded(hulk)) {
            return;
        }

        List<Entity> riders = hulk.getPassengers();
        if (riders.isEmpty()) {
            return;
        }
        for (Entity rider : riders) {
            if (rider instanceof Player victim) {
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
    private void throwPlayer(Player hulk, Player victim) {
        thrown.add(victim.getUniqueId());
        victim.leaveVehicle();

        Vector fling = hulk.getEyeLocation().getDirection().normalize()
                .multiply(game.config().hulkThrowPower())
                .setY(game.config().hulkThrowLift());

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (victim.isOnline()) {
                victim.setVelocity(fling);
            }
        });

        hulk.getWorld().playSound(hulk.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 1.6F);
        victim.sendMessage(Component.text("You were thrown.", NamedTextColor.RED));
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
    }

    /** The ability is the fist. Anything in the main hand switches it off. */
    private boolean emptyHanded(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.AIR;
    }
}
