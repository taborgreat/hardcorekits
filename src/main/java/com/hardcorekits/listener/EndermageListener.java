package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.EndermageKit;
import com.hardcorekits.util.Phases;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Endermage's portal.
 *
 * <p>Reach is a square column of blocks around the portal, measured horizontally only — anyone
 * standing in it is pulled in regardless of how far above or below they are, which is what
 * makes it answer bedrock camping.
 *
 * <p>The portal block is cosmetic: it appears for a moment, then vanishes. The real cooldown is
 * the item itself, which leaves the inventory on use and is handed back later, so an Endermage
 * can only ever have one portal in play.
 */
public final class EndermageListener implements Listener {

    /** How long the portal block stays visible before vanishing. */
    private static final long PORTAL_VISIBLE_TICKS = 30L;

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Portals that finished recharging while their owner was offline; given on return. */
    private final Set<UUID> owed = new HashSet<>();

    public EndermageListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != EndermageKit.PORTAL_ITEM) {
            return;
        }
        Player caster = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(caster, EndermageKit.ID)) {
            // Refused, not ignored: ignoring a locked portal let vanilla put it down as a plain
            // end portal frame, which cannot be dug back up, with no recharge ever scheduled.
            // That was the kit gone for the match.
            if (kits.hasKit(caster, EndermageKit.ID)) {
                event.setCancelled(true);
            }
            return; // an ordinary block for anyone else
        }

        Location portal = block.getLocation().add(0.5D, 0.0D, 0.5D);
        portal.setYaw(caster.getLocation().getYaw());
        portal.setPitch(caster.getLocation().getPitch());

        // The portal opening: a burst of purple and the rising whoosh of a portal waking up, so
        // everyone nearby (and the match film) sees and hears that it was the Endermage.
        World world = portal.getWorld();
        Location mouth = portal.clone().add(0.0D, 1.0D, 0.0D);
        world.spawnParticle(Particle.PORTAL, mouth, 220, 0.7D, 0.7D, 0.7D, 1.2D);
        world.spawnParticle(Particle.REVERSE_PORTAL, mouth, 90, 0.5D, 0.6D, 0.5D, 0.08D);
        world.playSound(portal, Sound.BLOCK_PORTAL_TRIGGER, 0.9F, 1.35F);
        world.playSound(portal, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0F, 0.8F);

        dragIntoPortal(caster, portal);

        // The block is scenery: show it, then take it away again.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (block.getType() == EndermageKit.PORTAL_ITEM) {
                block.setType(Material.AIR);
            }
        }, PORTAL_VISIBLE_TICKS);

        rechargeLater(caster);
    }

    /**
     * Teleports a player, and if they were on a horse the horse comes too and they land on
     * it. Teleporting a player ejects them from any vehicle, so the horse is moved alongside
     * and the rider put back a tick later, once both have arrived.
     */
    private void dragWithMount(Player player, Location portal) {
        Entity vehicle = player.getVehicle();
        player.teleport(portal);
        if (vehicle instanceof AbstractHorse horse && horse.isValid()) {
            horse.teleport(portal);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (horse.isValid() && player.isOnline()) {
                    horse.addPassenger(player);
                }
            });
        }
    }

    /** Pulls everyone in the portal's column — the caster included — to the portal. */
    /** Being sucked in: purple left where they stood, and a trail of it up (or down) to the portal. */
    private void pullEffect(Location from, Location portal) {
        World world = portal.getWorld();
        Location feet = from.clone().add(0.0D, 1.0D, 0.0D);
        world.spawnParticle(Particle.REVERSE_PORTAL, feet, 70, 0.35D, 0.8D, 0.35D, 0.06D);
        world.playSound(from, Sound.BLOCK_PORTAL_TRIGGER, 0.7F, 1.7F);
        org.bukkit.util.Vector line = portal.toVector().subtract(feet.toVector());
        int steps = (int) Math.min(48.0D, Math.max(2.0D, line.length() * 2.0D));
        for (int i = 1; i <= steps; i++) {
            Location at = feet.clone().add(line.clone().multiply(i / (double) steps));
            world.spawnParticle(Particle.PORTAL, at, 5, 0.15D, 0.15D, 0.15D, 0.25D);
        }
    }

    private void dragIntoPortal(Player caster, Location portal) {
        // A square column of blocks centred on the portal: reach 2 is 5x5. Measured from the
        // block centre, so the half-block on each side of the outer ring is in the column.
        double half = game.config().endermageReach() + 0.5D;
        int immunity = game.config().endermageImmunitySeconds();

        List<Player> dragged = new ArrayList<>();
        for (Player target : game.alivePlayers()) {
            if (!target.getWorld().equals(portal.getWorld())) {
                continue;
            }
            // Horizontal distance only: height is deliberately ignored.
            double dx = target.getLocation().getX() - portal.getX();
            double dz = target.getLocation().getZ() - portal.getZ();
            if (Math.abs(dx) > half || Math.abs(dz) > half) {
                continue;
            }
            if (!target.equals(caster)) {
                dragged.add(target);
            }
            // Heard at both ends: the spot they vanished from, and the portal they land on.
            target.getWorld().playSound(target.getLocation(),
                    Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);
            pullEffect(target.getLocation(), portal);
            dragWithMount(target, portal);
            game.grantImmunity(target, immunity);
            target.getWorld().playSound(portal, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        }

        // The caster may have been outside their own column; they always come along.
        if (!dragged.contains(caster)) {
            dragWithMount(caster, portal);
            game.grantImmunity(caster, immunity);
        }

        if (dragged.isEmpty()) {
            caster.sendMessage(Component.text("Portal opened, nobody was in its column.",
                    NamedTextColor.DARK_PURPLE));
            return;
        }
        for (Player victim : dragged) {
            victim.sendMessage(Component.text("You were dragged through " + caster.getName()
                    + "'s portal!", NamedTextColor.DARK_PURPLE));
        }
        caster.sendMessage(Component.text("Dragged " + dragged.size()
                + " player(s) through your portal.", NamedTextColor.DARK_PURPLE));
    }

    /** Hands the portal back after its cooldown, so only one can ever be in play. */
    private void rechargeLater(Player caster) {
        int cooldown = game.config().endermageCooldownSeconds();
        caster.sendMessage(Component.text("Portal recharging (" + cooldown + "s).",
                NamedTextColor.DARK_PURPLE));

        UUID owner = caster.getUniqueId();
        Phases.delayed(plugin, cooldown, () -> handBack(owner));
    }

    /**
     * The other half of the cooldown, and it has to land: the portal is the whole kit.
     *
     * <p>The owner is looked up afresh rather than held from the placement, because a player
     * who relogged in between is a new object and the old one's inventory goes nowhere. If
     * they are in the reconnect window the portal waits for them, and if there is no room for
     * it, it lands at their feet.
     */
    private void handBack(UUID owner) {
        if (!game.alive().contains(owner)) {
            return;
        }
        Player caster = Bukkit.getPlayer(owner);
        if (caster == null) {
            owed.add(owner);
            return;
        }
        if (!kits.hasKit(caster, EndermageKit.ID)) {
            return;
        }
        for (ItemStack spill : caster.getInventory()
                .addItem(new ItemStack(EndermageKit.PORTAL_ITEM)).values()) {
            caster.getWorld().dropItemNaturally(caster.getLocation(), spill);
        }
        caster.sendMessage(Component.text("Your portal has recharged.",
                NamedTextColor.DARK_PURPLE));
    }

    /** Back inside the reconnect window, with a portal that recharged while they were away. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (owed.remove(uuid)) {
            handBack(uuid);
        }
    }
}
