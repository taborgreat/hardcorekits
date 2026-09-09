package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.JellyfishKit;
import gg.hungergames.util.Phases;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Jellyfish's water.
 *
 * <p>A source block is placed against the clicked face and taken back a few seconds later, so
 * what is left behind drains on its own. Every block placed is remembered until it is removed,
 * which is what lets a match reset mop up anything a disconnect or a crash left standing.
 *
 * <p>Only ever placed into air, fire or something else replaceable — the kit conjures water,
 * it does not delete terrain. And never against existing water: two touching sources breed a
 * third, and a bred source outlives the cleanup — which is exactly how the "infinite spring"
 * glitch was made. Refusing adjacency kills the whole family of it.
 */
public final class JellyfishListener implements Listener {

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Water this kit is responsible for, so a reset can take back anything still standing. */
    private final Set<Location> conjured = new LinkedHashSet<>();
    /** Live conjurings per player, against the cap. */
    private final Map<UUID, Integer> active = new HashMap<>();

    private static final org.bukkit.block.BlockFace[] TOUCHING = {
            org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST,
            org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN};

    public JellyfishListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state: drain anything outstanding when the game resets. */
    public void drainAll() {
        for (Location location : conjured) {
            Block block = location.getBlock();
            if (block.getType() == Material.WATER) {
                block.setType(Material.AIR);
            }
        }
        conjured.clear();
        active.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(player, JellyfishKit.ID)) {
            return;
        }
        // With a fist, as the kit says: anything held is used for whatever it is for.
        if (!player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        Block target = clicked.getRelative(event.getBlockFace());
        // Clicking the fire you are standing in should drown it, not place water beside it.
        if (clicked.getType() == Material.FIRE) {
            target = clicked;
        }
        if (!target.getType().isAir() && !target.isReplaceable()) {
            return;
        }

        // Never against existing water — sources that touch breed new sources, and those
        // outlive the cleanup. This is the infinite-spring glitch, refused at the door.
        for (org.bukkit.block.BlockFace face : TOUCHING) {
            if (target.getRelative(face).getType() == Material.WATER) {
                player.sendActionBar(Component.text("Too close to water.", NamedTextColor.AQUA));
                return;
            }
        }

        int cap = game.config().jellyfishMaxActive();
        if (active.getOrDefault(player.getUniqueId(), 0) >= cap) {
            player.sendActionBar(Component.text("Too much water in play (" + cap
                    + "). Wait for some to drain.", NamedTextColor.AQUA));
            return;
        }

        conjure(player, target);
    }

    private void conjure(Player player, Block target) {
        int seconds = game.config().jellyfishSeconds();
        Location where = target.getLocation();

        target.setType(Material.WATER);
        conjured.add(where);
        active.merge(player.getUniqueId(), 1, Integer::sum);
        target.getWorld().playSound(where, Sound.ITEM_BUCKET_EMPTY, 1.0F, 1.2F);

        UUID owner = player.getUniqueId();
        Phases.delayed(plugin, seconds, () -> {
            conjured.remove(where);
            active.merge(owner, -1, Integer::sum);
            Block block = where.getBlock();
            // Only take back water. Anything else means the world moved on without us.
            if (block.getType() == Material.WATER) {
                block.setType(Material.AIR);
            }
        });

        player.sendActionBar(Component.text("Water for " + seconds + "s.", NamedTextColor.AQUA));
    }
}
