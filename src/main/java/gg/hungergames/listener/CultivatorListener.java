package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.CultivatorKit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Cultivator's instant growth.
 *
 * <p>Two different things have to happen depending on what was planted:
 * <ul>
 *   <li>Crops carry an age, so they are simply snapped to their maximum — wheat, carrots,
 *       potatoes, beetroot, nether wart, cocoa, berry bushes and stems all work this way.</li>
 *   <li>Saplings have no age; they are replaced by an actual generated tree.</li>
 * </ul>
 *
 * <p>Growth is deferred a tick: the block is only really in the world once the place event
 * has finished resolving.
 */
public final class CultivatorListener implements Listener {

    /** Vanilla bonemeal only works some of the time, so try until it takes. */
    private static final int BONEMEAL_ATTEMPTS = 24;

    /** Fallback only: what to force when vanilla bonemeal will not grow the sapling. */
    private static final Map<Material, TreeType> SAPLINGS = Map.ofEntries(
            Map.entry(Material.OAK_SAPLING, TreeType.TREE),
            Map.entry(Material.SPRUCE_SAPLING, TreeType.REDWOOD),
            Map.entry(Material.BIRCH_SAPLING, TreeType.BIRCH),
            Map.entry(Material.JUNGLE_SAPLING, TreeType.SMALL_JUNGLE),
            Map.entry(Material.ACACIA_SAPLING, TreeType.ACACIA),
            Map.entry(Material.DARK_OAK_SAPLING, TreeType.DARK_OAK),
            Map.entry(Material.CHERRY_SAPLING, TreeType.CHERRY),
            Map.entry(Material.PALE_OAK_SAPLING, TreeType.PALE_OAK),
            Map.entry(Material.MANGROVE_PROPAGULE, TreeType.MANGROVE),
            Map.entry(Material.AZALEA, TreeType.AZALEA),
            Map.entry(Material.FLOWERING_AZALEA, TreeType.AZALEA),
            Map.entry(Material.CRIMSON_FUNGUS, TreeType.CRIMSON_FUNGUS),
            Map.entry(Material.WARPED_FUNGUS, TreeType.WARPED_FUNGUS),
            Map.entry(Material.RED_MUSHROOM, TreeType.RED_MUSHROOM),
            Map.entry(Material.BROWN_MUSHROOM, TreeType.BROWN_MUSHROOM));

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    public CultivatorListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!game.state().isLive() || !kits.hasKit(event.getPlayer(), CultivatorKit.ID)) {
            return;
        }

        Block block = event.getBlockPlaced();
        Material planted = block.getType();
        TreeType tree = SAPLINGS.get(planted);

        if (tree == null && !(block.getBlockData() instanceof Ageable)) {
            return; // not something that grows
        }

        Bukkit.getScheduler().runTask(plugin, () -> grow(block, planted, tree));
    }

    private void grow(Block block, Material planted, TreeType tree) {
        // Someone may have broken it in the intervening tick.
        if (block.getType() != planted) {
            return;
        }

        if (tree != null) {
            growSapling(block, planted, tree);
            return;
        }

        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
            ageable.setAge(ageable.getMaximumAge());
            block.setBlockData(ageable);
        }
    }

    /**
     * Grows a sapling by repeatedly applying bonemeal, exactly as a player would — the item is
     * never consumed, the player just does not need one.
     *
     * <p>Going through vanilla keeps the result honest: oak sometimes comes up as a big oak,
     * jungle and spruce form their 2x2 variants when the saplings are arranged for it, and a
     * lone dark oak refuses to grow just as it should. Each application only has a chance of
     * working, hence the loop.
     *
     * <p>If vanilla will not take (most often a single dark oak), fall back to forcing the
     * tree so the kit's promise still holds.
     */
    private void growSapling(Block block, Material planted, TreeType tree) {
        for (int attempt = 0; attempt < BONEMEAL_ATTEMPTS; attempt++) {
            block.applyBoneMeal(BlockFace.UP);
            if (block.getType() != planted) {
                return; // it grew
            }
        }

        Location location = block.getLocation();
        block.setType(Material.AIR);
        if (!block.getWorld().generateTree(location, ThreadLocalRandom.current(), tree)) {
            block.setType(planted); // no room for a tree; leave the sapling be
        }
    }
}
