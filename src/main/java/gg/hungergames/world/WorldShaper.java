package gg.hungergames.world;

import gg.hungergames.HungerGames;
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
import java.util.HashSet;
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

    private final HungerGames plugin;
    private final String worldName;
    private final int swampMushroomsPerChunk;
    private final Set<Long> processed = new HashSet<>();
    private final Deque<Chunk> queue = new ArrayDeque<>();

    private int diamondsStripped;
    private int chunksShaped;
    private int mushroomsPlanted;

    public WorldShaper(HungerGames plugin, String worldName, int swampMushroomsPerChunk) {
        this.plugin = plugin;
        this.worldName = worldName;
        this.swampMushroomsPerChunk = swampMushroomsPerChunk;
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
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Physics updates are pointless here and expensive, hence setType(..., false).
                chunk.getBlock(x, FLOOR_Y, z).setType(Material.BEDROCK, false);

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

    /**
     * Extra mushrooms for swamp chunks — soup is the healing economy, so the swamps this map
     * is centred on should actually feed it.
     *
     * <p>Per column, not per chunk biome: chunks straddle biome borders, and only the swampy
     * columns should get anything. Placement obeys the block's own survival rule — mushrooms
     * pop off in bright light at the first neighbour update — so only shaded spots (under the
     * swamp oaks, which is where vanilla puts them too) are used, and the count per chunk is
     * attempts, not a quota.
     */
    private void sprinkleMushrooms(Chunk chunk) {
        if (swampMushroomsPerChunk <= 0) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int i = 0; i < swampMushroomsPerChunk; i++) {
            // NO_LEAVES, or the "surface" under a swamp oak is its canopy — and under the
            // canopy is the only shade where a mushroom survives daylight in the first place.
            Block surface = world.getHighestBlockAt(
                    baseX + random.nextInt(16), baseZ + random.nextInt(16),
                    HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Biome biome = surface.getBiome();
            if (biome != Biome.SWAMP && biome != Biome.MANGROVE_SWAMP) {
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
