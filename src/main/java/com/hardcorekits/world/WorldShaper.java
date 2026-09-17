package com.hardcorekits.world;

import com.hardcorekits.HardcoreGames;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Reshapes generated terrain into the old-school playfield, one chunk at a time:
 *
 * <ol>
 *   <li>Lays a solid bedrock floor at Y=0, so the world bottoms out where it used to instead
 *       of at Y=-64. Everything below is sealed off and unreachable.</li>
 *   <li>Removes natural diamond ore above that floor, making the feast and structure loot the
 *       only sources of diamond gear.</li>
 * </ol>
 *
 * <p>Chunks are queued on load and processed a few per tick — sweeping a 500x500 area in one
 * go would stall the main thread for a long time.
 */
public final class WorldShaper implements Listener {

    /** The old world bottom. Everything below this is sealed off. */
    private static final int FLOOR_Y = 0;
    /** Diamonds never generate above this, so there is no point scanning higher. */
    private static final int MAX_SCAN_Y = 20;
    private static final int CHUNKS_PER_TICK = 2;

    private final HardcoreGames plugin;
    private final String worldName;
    private final int swampMushroomsPerChunk;
    private final int forestMushroomsPerChunk;
    private final Set<Long> processed = new HashSet<>();
    /**
     * Natural surface height per column, recorded the first time a chunk is shaped, before
     * any player could have built there. The build limit reads it to allow a few blocks on a
     * mountain top without allowing a tower from the valley floor. 256 shorts a chunk.
     */
    private final Map<Long, short[]> naturalSurface = new HashMap<>();
    private final Deque<Chunk> queue = new ArrayDeque<>();

    private int diamondsStripped;
    private int chunksShaped;
    private int mushroomsPlanted;

    public WorldShaper(HardcoreGames plugin, String worldName, int forestMushroomsPerChunk,
                       int swampMushroomsPerChunk) {
        this.plugin = plugin;
        this.worldName = worldName;
        this.forestMushroomsPerChunk = forestMushroomsPerChunk;
        this.swampMushroomsPerChunk = swampMushroomsPerChunk;
    }

    /** Biomes whose floors are shaded enough for mushrooms to take. */
    private static final Set<Biome> FOREST_FLOORS = Set.of(
            Biome.FOREST, Biome.FLOWER_FOREST, Biome.BIRCH_FOREST, Biome.OLD_GROWTH_BIRCH_FOREST,
            Biome.DARK_FOREST, Biome.TAIGA, Biome.OLD_GROWTH_PINE_TAIGA,
            Biome.OLD_GROWTH_SPRUCE_TAIGA, Biome.JUNGLE, Biome.BAMBOO_JUNGLE, Biome.SPARSE_JUNGLE);

    private static final Set<Biome> SWAMPS = Set.of(Biome.SWAMP, Biome.MANGROVE_SWAMP);

    /**
     * The ground height recorded for a column when its chunk was shaped, or -1 if that chunk
     * has not been shaped (in which case the caller falls back to the flat cap).
     */
    public int naturalSurface(int x, int z) {
        short[] heights = naturalSurface.get(Chunk.getChunkKey(x >> 4, z >> 4));
        return heights == null ? -1 : heights[((z & 15) << 4) | (x & 15)];
    }

    public void start() {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            for (Chunk chunk : world.getLoadedChunks()) {
                enqueue(chunk);
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::drain, 1L, 1L);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        enqueue(event.getChunk());
    }

    private void enqueue(Chunk chunk) {
        if (chunk.getWorld().getName().equals(worldName) && !processed.contains(chunk.getChunkKey())) {
            queue.add(chunk);
        }
    }

    private void drain() {
        for (int i = 0; i < CHUNKS_PER_TICK && !queue.isEmpty(); i++) {
            Chunk chunk = queue.poll();
            if (chunk != null && chunk.isLoaded() && processed.add(chunk.getChunkKey())) {
                shape(chunk);
                chunksShaped++;
            }
        }
    }

    private void shape(Chunk chunk) {
        recordSurface(chunk);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Block floor = chunk.getBlock(x, FLOOR_Y, z);
                // A generated structure occasionally leaves a container — a hopper, a chest —
                // exactly at floor level, its block entity still pending promotion from the
                // chunk's NBT. Swap the block first and that promotion later fails loudly
                // ("Invalid block entity ... got Block{minecraft:bedrock}"). Reading the
                // state promotes it NOW, while block and entity still agree, so the swap
                // retires both cleanly.
                floor.getState();
                // Physics updates are pointless here and expensive, hence setType(..., false).
                floor.setType(Material.BEDROCK, false);

                // Only scan above the new floor — anything below it is sealed off anyway.
                for (int y = FLOOR_Y + 1; y <= MAX_SCAN_Y; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    Material replacement = switch (block.getType()) {
                        case DIAMOND_ORE -> Material.STONE;
                        case DEEPSLATE_DIAMOND_ORE -> Material.DEEPSLATE;
                        default -> null;
                    };
                    if (replacement != null) {
                        block.setType(replacement, false);
                        diamondsStripped++;
                    }
                }
            }
        }

        sprinkleMushrooms(chunk);
    }

    /** Snapshot of the natural ground before anything is built on it. */
    private void recordSurface(Chunk chunk) {
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        short[] heights = new short[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                heights[(z << 4) | x] = (short) world.getHighestBlockYAt(baseX + x, baseZ + z,
                        HeightMap.MOTION_BLOCKING_NO_LEAVES);
            }
        }
        naturalSurface.put(chunk.getChunkKey(), heights);
    }

    /**
     * Extra mushrooms for swamp chunks — soup is the healing economy, so the swamps this map
     * is centred on should actually feed it. Forest floors get a smaller share too.
     *
     * <p>Per column, not per chunk biome: chunks straddle biome borders, and only the swampy
     * columns should get anything. Placement obeys the block's own survival rule — mushrooms
     * pop off in bright light at the first neighbour update — so only shaded spots (under the
     * swamp oaks, which is where vanilla puts them too) are used, and the count per chunk is
     * attempts, not a quota.
     */
    private void sprinkleMushrooms(Chunk chunk) {
        sprinkle(chunk, swampMushroomsPerChunk, SWAMPS);
        sprinkle(chunk, forestMushroomsPerChunk, FOREST_FLOORS);
    }

    private void sprinkle(Chunk chunk, int attempts, Set<Biome> biomes) {
        if (attempts <= 0) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int i = 0; i < attempts; i++) {
            // NO_LEAVES, or the "surface" under a swamp oak is its canopy — and under the
            // canopy is the only shade where a mushroom survives daylight in the first place.
            Block surface = world.getHighestBlockAt(
                    baseX + random.nextInt(16), baseZ + random.nextInt(16),
                    HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (!biomes.contains(surface.getBiome())) {
                continue;
            }
            Material ground = surface.getType();
            if (ground != Material.GRASS_BLOCK && ground != Material.MUD
                    && ground != Material.PODZOL) {
                continue;
            }
            Block spot = surface.getRelative(BlockFace.UP);
            if (!spot.getType().isAir() || spot.getLightFromSky() > 12) {
                continue;
            }
            spot.setType(random.nextBoolean() ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM,
                    false);
            mushroomsPlanted++;
        }
    }

    public int diamondsStripped() {
        return diamondsStripped;
    }

    public int chunksShaped() {
        return chunksShaped;
    }

    public int mushroomsPlanted() {
        return mushroomsPlanted;
    }

    /** Chunks still waiting to be processed. */
    public int pending() {
        return queue.size();
    }
}
