package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.DiggerKit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;

/**
 * The Digger's shaft.
 *
 * <p>Placing the egg starts a fuse; when it burns down the column under the egg is removed in
 * one go, five by five and several blocks deep, and the egg goes with it. The fuse is armed by
 * the <em>placement</em> rather than by the block still being there, so knocking the egg away —
 * a dragon egg teleports when it is hit — does not defuse the hole. What it buys you is the
 * warning: the egg is visible, it hisses, and the ground it is standing on is about to go.
 *
 * <p>Two things are never dug out. Bedrock, so the world floor and the End Game box hold; and
 * anything holding an inventory, so nobody can delete the feast by opening a hole under it.
 */
public final class DiggerListener implements Listener {

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Fuses still burning, so a reset does not leave one to fire into the next match. */
    private final Set<BukkitTask> fuses = new HashSet<>();

    public DiggerListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Dropped on reset, like the Demoman's mines. */
    public void clearFuses() {
        fuses.forEach(BukkitTask::cancel);
        fuses.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (placed.getType() != DiggerKit.EGG || !game.state().isLive()) {
            return;
        }
        if (!kits.canUseAbility(event.getPlayer(), DiggerKit.ID)) {
            return; // an ordinary dragon egg for anyone else
        }

        Location egg = placed.getLocation();
        egg.getWorld().playSound(egg, Sound.ENTITY_TNT_PRIMED, 1.0F, 1.0F);

        BukkitTask[] fuse = new BukkitTask[1];
        fuse[0] = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            fuses.remove(fuse[0]);
            if (game.state().isLive()) {
                dig(egg);
            }
        }, game.config().diggerFuseTicks());
        fuses.add(fuse[0]);
    }

    /** Opens the shaft: the egg's own block, and everything under it inside the footprint. */
    private void dig(Location egg) {
        World world = egg.getWorld();
        int radius = game.config().diggerRadius();
        int depth = game.config().diggerDepth();
        int x = egg.getBlockX();
        int z = egg.getBlockZ();

        // The egg is consumed by its own hole — unless it was knocked somewhere else, in which
        // case whoever finds it has earned it.
        Block eggBlock = world.getBlockAt(egg);
        if (eggBlock.getType() == DiggerKit.EGG) {
            eggBlock.setType(Material.AIR);
        }

        int top = egg.getBlockY() - 1;
        int bottom = Math.max(world.getMinHeight(), top - depth + 1);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = top; y >= bottom; y--) {
                    Block block = world.getBlockAt(x + dx, y, z + dz);
                    if (block.getType().isAir() || isProtected(block)) {
                        continue;
                    }
                    block.setType(Material.AIR);
                }
            }
        }

        world.playSound(egg, Sound.BLOCK_STONE_BREAK, 1.5F, 0.6F);
    }

    /**
     * Bedrock keeps the world floor and the End Game box intact, and containers keep the feast
     * intact — clearing a chest to air would delete what is inside it rather than spill it.
     */
    private boolean isProtected(Block block) {
        return block.getType() == Material.BEDROCK || block.getState() instanceof InventoryHolder;
    }
}
