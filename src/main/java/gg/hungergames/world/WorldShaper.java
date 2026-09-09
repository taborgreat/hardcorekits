package gg.hungergames.world;

import gg.hungergames.HungerGames;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

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
    private final Set<Long> processed = new HashSet<>();
    private final Deque<Chunk> queue = new ArrayDeque<>();

    private int diamondsStripped;
    private int chunksShaped;

    public WorldShaper(HungerGames plugin, String worldName) {
        this.plugin = plugin;
        this.worldName = worldName;
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
    }

    public int diamondsStripped() {
        return diamondsStripped;
    }

    public int chunksShaped() {
        return chunksShaped;
    }

    /** Chunks still waiting to be processed. */
    public int pending() {
        return queue.size();
    }
}
