package gg.hungergames.game;

import gg.hungergames.HungerGames;
import gg.hungergames.util.Msg;
import gg.hungergames.util.Phases;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds the feast in two stages: the circle is revealed first as a warning, then the chests
 * drop into it some minutes later.
 *
 * <p>The circle is a flat disc placed at or above the local terrain height, with the space
 * above it cleared — so it never ends up buried inside a hill.
 */
public final class FeastManager {

    /**
     * The whole disc, rim included, is one material.
     *
     * <p>Note this is ordinary grass and nothing more — a Kaya's collapsing traps are a
     * registry of blocks that Kaya placed, so the feast floor is never armed.
     */
    private static final Material PLATFORM = Material.GRASS_BLOCK;
    /** Blocks of headroom carved out above the platform. */
    private static final int HEADROOM = 6;
    /** Chests are packed into a tight disc around the table, out to at most this radius. */
    private static final int CHEST_CLUSTER_RADIUS = 3;
    /** Rolls per chest. Enough that every chest is worth opening. */
    private static final int MIN_STACKS = 4;
    private static final int MAX_STACKS = 8;

    private final HungerGames plugin;
    private final GameManager game;
    private final GameConfig config;

    private BukkitTask circleTask;
    private BukkitTask chestTask;
    private Location site;

    FeastManager(HungerGames plugin, GameManager game, GameConfig config) {
        this.plugin = plugin;
        this.game = game;
        this.config = config;
    }

    /** Where the feast is, or null if it has not been sited yet. */
    public Location site() {
        return site == null ? null : site.clone();
    }

    void schedule() {
        cancel();
        circleTask = Phases.delayed(plugin, config.feastCircleMinutes() * 60L, this::revealCircle);
    }

    /**
     * Builds the circle, then counts down to the chests for the whole warning period.
     *
     * <p>The first countdown line doubles as the "the circle is here" announcement — there is
     * no separate message, which matches how the original servers read.
     */
    private void revealCircle() {
        if (site == null) {
            site = pickSite();
        }
        buildCircle();

        int warning = (config.feastMinutes() - config.feastCircleMinutes()) * 60;
        if (warning <= 0) {
            placeLoot();
            return;
        }

        String coords = "(" + site.getBlockX() + ", " + (site.getBlockY() + 1) + ", "
                + site.getBlockZ() + ")";

        chestTask = Phases.countdown(plugin, warning,
                remaining -> {
                    if (Phases.isMilestone(remaining)) {
                        Msg.timer("Feast will begin at " + coords + " in "
                                + Msg.duration(remaining) + ".");
                    }
                },
                this::placeLoot);
    }

    void cancel() {
        Phases.cancel(circleTask);
        Phases.cancel(chestTask);
        circleTask = null;
        chestTask = null;
    }

    void clear() {
        cancel();
        site = null;
    }

    // ---------------------------------------------------------------- stage 1: the circle

    /** Builds the disc at the already-chosen {@link #site}. Never picks a location itself. */
    private void buildCircle() {
        int radius = config.feastRadius();
        World world = site.getWorld();
        int cx = site.getBlockX();
        int cy = site.getBlockY();
        int cz = site.getBlockZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > radius * radius) {
                    continue;
                }
                world.getBlockAt(cx + dx, cy, cz + dz).setType(PLATFORM);

                // Carve out anything above so the circle is never buried in a hillside.
                for (int dy = 1; dy <= HEADROOM; dy++) {
                    Block above = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (!above.getType().isAir()) {
                        above.setType(Material.AIR);
                    }
                }
            }
        }

        playCue();
    }

    /**
     * Random spot inside the border, sited on top of the terrain.
     *
     * <p>Y is the highest surface across the whole footprint, so a circle straddling a slope
     * sits on the high side rather than clipping into it.
     */
    private Location pickSite() {
        World world = config.world();
        int radius = config.feastRadius();
        // Keep the whole footprint inside the border.
        int limit = (int) (config.borderSize() / 2.0D) - radius - 2;

        // Kept near the middle on purpose: the feast exists to pull whoever is left back into
        // one place, and a chest cluster 400 blocks into a corner is a private restock for
        // whoever happened to be standing there. Clamped to the border, so a spawn radius
        // larger than the map cannot push the footprint outside it.
        int spread = Math.max(1, Math.min(config.feastSpawnRadius(), limit));

        ThreadLocalRandom random = ThreadLocalRandom.current();
        // Uniform over the disc rather than the radius, so it does not cluster at the centre.
        double angle = random.nextDouble() * 2.0D * Math.PI;
        double distance = spread * Math.sqrt(random.nextDouble());

        int cx = config.centerX() + (int) Math.round(Math.cos(angle) * distance);
        int cz = config.centerZ() + (int) Math.round(Math.sin(angle) * distance);

        int highest = world.getMinHeight();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                highest = Math.max(highest, world.getHighestBlockYAt(cx + dx, cz + dz));
            }
        }
        return new Location(world, cx, highest, cz);
    }

    // ---------------------------------------------------------------- stage 2: the loot

    private void placeLoot() {
        if (site == null) {
            // Nothing was sited, so there is no circle to fill. Re-picking here would drop the
            // chests somewhere other than the announced coordinates. Logged rather than
            // swallowed: a countdown that ends in silence is the symptom to chase.
            plugin.getLogger().warning("Feast timer finished with no site — nothing placed.");
            return;
        }
        World world = site.getWorld();
        int cx = site.getBlockX();
        int cy = site.getBlockY() + 1;
        int cz = site.getBlockZ();

        world.getBlockAt(cx, cy, cz).setType(Material.ENCHANTING_TABLE);

        for (int[] offset : chestOffsets(config.feastChests())) {
            Block block = world.getBlockAt(cx + offset[0], cy, cz + offset[1]);
            block.setType(Material.CHEST);

            // getBlockInventory() is this chest's own live inventory. The obvious
            // getInventory() returns the *combined* inventory when chests are paired, and it
            // comes off a snapshot that has to be written back with update() — which is how
            // the loot went missing.
            if (block.getState() instanceof Chest chest) {
                fill(chest.getBlockInventory());
            }
        }

        Msg.timer("The Feast has Begun!");
        playCue();
        game.enterFeastState();
    }

    /**
     * Offsets for a tight disc of chests around the enchanting table, nearest ring first.
     *
     * <p>12 chests fills the ring at distance 1, the diagonals, and the ring at distance 2 —
     * the classic packed cluster rather than a thin wide ring.
     */
    private static List<int[]> chestOffsets(int count) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -CHEST_CLUSTER_RADIUS; dx <= CHEST_CLUSTER_RADIUS; dx++) {
            for (int dz = -CHEST_CLUSTER_RADIUS; dz <= CHEST_CLUSTER_RADIUS; dz++) {
                if (dx == 0 && dz == 0) {
                    continue; // the enchanting table sits here
                }
                // Same parity only, so no two chests are ever edge-to-edge. Touching chests
                // pair into double chests, which is why twelve of them showed up as a handful.
                // Diagonal neighbours never pair, so the cluster still reads as a tight blob.
                if (((dx + dz) & 1) == 0) {
                    offsets.add(new int[]{dx, dz});
                }
            }
        }
        offsets.sort(Comparator.comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1]));
        return offsets.subList(0, Math.min(count, offsets.size()));
    }

    /**
     * Scatters a handful of rolls into distinct slots.
     *
     * <p>Slots are shuffled rather than picked at random each time: repeatedly drawing a slot
     * lets later rolls land on earlier ones, so a chest that should hold six things quietly
     * held three.
     */
    private void fill(Inventory inventory) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            slots.add(slot);
        }
        Collections.shuffle(slots, random);

        int rolls = Math.min(random.nextInt(MIN_STACKS, MAX_STACKS + 1), slots.size());
        for (int i = 0; i < rolls; i++) {
            inventory.setItem(slots.get(i), FeastLoot.roll(random));
        }
    }

    private void playCue() {
        for (Player player : game.alivePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.0F);
        }
    }

    /** Weighted loot table — the only source of diamond gear in the match. */
    private static final class FeastLoot {

        /** A potion type to brew into the rolled item, or null for a plain item. */
        private record Entry(int weight, Material material, int min, int max, PotionType potion) {
            Entry(int weight, Material material, int min, int max) {
                this(weight, material, min, max, null);
            }
        }

        private static final List<Entry> ENTRIES = List.of(
                // Diamond gear at 5x weight. The feast is the only source of it in the match,
                // so it should be the thing the fight over the chests is actually about.
                new Entry(15, Material.DIAMOND_SWORD, 1, 1),
                new Entry(15, Material.DIAMOND_HELMET, 1, 1),
                new Entry(15, Material.DIAMOND_CHESTPLATE, 1, 1),
                new Entry(15, Material.DIAMOND_LEGGINGS, 1, 1),
                new Entry(15, Material.DIAMOND_BOOTS, 1, 1),
                new Entry(2, Material.ENCHANTED_GOLDEN_APPLE, 1, 1),
                new Entry(8, Material.GOLDEN_APPLE, 1, 3),
                new Entry(8, Material.ENDER_PEARL, 1, 4),
                new Entry(6, Material.IRON_SWORD, 1, 1),
                new Entry(6, Material.BOW, 1, 1),
                new Entry(8, Material.ARROW, 8, 24),
                new Entry(10, Material.COOKED_BEEF, 4, 10),
                new Entry(6, Material.GOLDEN_CARROT, 3, 8),
                new Entry(4, Material.IRON_CHESTPLATE, 1, 1),
                new Entry(4, Material.EXPERIENCE_BOTTLE, 4, 12),
                new Entry(15, Material.DIAMOND, 1, 3),

                // Soup is the healing economy, not a snack, so it is the commonest thing here.
                // Stew does not stack, so a roll is one bowl and the weight does the work.
                new Entry(14, Material.MUSHROOM_STEW, 1, 1),

                // Meat.
                new Entry(8, Material.COOKED_PORKCHOP, 2, 6),
                new Entry(6, Material.COOKED_CHICKEN, 2, 6),
                new Entry(5, Material.COOKED_MUTTON, 2, 5),
                new Entry(4, Material.BREAD, 3, 6),

                // Buckets. Neither stacks, so these are one-offs — a lava bucket is a weapon
                // and a wall, water is an escape from a drop and an answer to lava.
                new Entry(5, Material.LAVA_BUCKET, 1, 1),
                new Entry(5, Material.WATER_BUCKET, 1, 1),

                // TNT, with the means to light it — one without the other is dead weight.
                new Entry(4, Material.TNT, 1, 3),
                new Entry(3, Material.FLINT_AND_STEEL, 1, 1),

                // Potions. Splash healing is the one worth fighting over at the feast.
                new Entry(25, Material.SPLASH_POTION, 1, 1, PotionType.STRONG_HEALING),
                new Entry(20, Material.POTION, 1, 1, PotionType.HEALING),
                new Entry(20, Material.POTION, 1, 1, PotionType.SWIFTNESS),
                new Entry(15, Material.POTION, 1, 1, PotionType.STRENGTH),
                new Entry(15, Material.POTION, 1, 1, PotionType.REGENERATION),
                new Entry(15, Material.POTION, 1, 1, PotionType.FIRE_RESISTANCE));

        private static final int TOTAL_WEIGHT = ENTRIES.stream().mapToInt(Entry::weight).sum();

        private FeastLoot() {
        }

        static ItemStack roll(ThreadLocalRandom random) {
            int pick = random.nextInt(TOTAL_WEIGHT);
            for (Entry entry : ENTRIES) {
                pick -= entry.weight();
                if (pick < 0) {
                    int amount = entry.min() == entry.max()
                            ? entry.min()
                            : random.nextInt(entry.min(), entry.max() + 1);
                    ItemStack item = new ItemStack(entry.material(), amount);

                    if (entry.potion() != null && item.getItemMeta() instanceof PotionMeta meta) {
                        meta.setBasePotionType(entry.potion());
                        item.setItemMeta(meta);
                    }
                    return item;
                }
            }
            return new ItemStack(Material.COOKED_BEEF, 4);
        }
    }
}
