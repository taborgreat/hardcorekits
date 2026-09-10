package com.hardcorekits.world;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameConfig;
import com.hardcorekits.game.GameManager;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates the whole playable area up front, so terrain generation stops being something
 * players can cause mid-fight.
 *
 * <p>Every world here is born fresh, so the sweep runs at every boot — but it is built to be
 * invisible. It works <b>centre-out</b>: the drop zone and the middle of the map, which every
 * match certainly uses, are baked first, and the border fringe nobody may ever stand on comes
 * last. It rides Paper's async chunk API with a bounded pipeline, and the moment a match goes
 * live the pipeline narrows to a trickle — the sweep yields to the game, never the other way
 * round. Chunks are not ticketed, so they unload behind the sweep instead of piling up in
 * memory; once generated they persist on disk and every later visit is a cheap read.
 */
public final class ChunkPregenerator {

    private final HardcoreGames plugin;
    private final GameManager game;

    private World world;
    /** Chunk coordinates in the order they are generated: centre first, rim last. */
    private final List<int[]> order = new ArrayList<>();

    private int cursor;
    private int done;
    private int inFlight;
    private int lastLoggedDecile;
    private long startedMillis;
    private boolean finishedAnnounced;
    /** Set on plugin disable. Volatile: completion callbacks race the shutdown. */
    private volatile boolean stopped;

    public ChunkPregenerator(HardcoreGames plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    public void start() {
        GameConfig config = game.config();
        if (!config.pregenEnabled()) {
            return;
        }
        world = config.world();
        int centerChunkX = config.centerX() >> 4;
        int centerChunkZ = config.centerZ() >> 4;

        double reach = config.borderSize() / 2.0D + config.borderHardWallMargin();
        int radius = (int) Math.ceil(reach / 16.0D) + config.pregenMarginChunks();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                order.add(new int[]{centerChunkX + dx, centerChunkZ + dz, dx * dx + dz * dz});
            }
        }
        // Centre-out: what every match uses for certain is ready first.
        order.sort(Comparator.comparingInt(coords -> coords[2]));

        startedMillis = System.currentTimeMillis();
        int side = radius * 2 + 1;
        plugin.getLogger().info("Pre-generating " + order.size() + " chunks (" + side + "x"
                + side + "), centre outward…");
        pump();
    }

    /**
     * The pipeline breathes with the game: full width while the lobby idles, a trickle once
     * a match is live. Re-read on every completion, so the narrowing takes effect mid-sweep.
     */
    private int width() {
        GameConfig config = game.config();
        return Math.max(1, game.state().isLive()
                ? config.pregenParallelDuringMatch()
                : config.pregenParallel());
    }

    /**
     * The sweep must die WITH the plugin. During shutdown every in-flight future completes
     * exceptionally, and a callback that pumps another request feeds fresh work into a chunk
     * system that is trying to halt — which is a server that stands at "Awaiting termination
     * of worker pool" for the full 60s timeout, twice, on every restart.
     */
    public void stop() {
        stopped = true;
    }

    private void pump() {
        while (!stopped && plugin.isEnabled() && cursor < order.size() && inFlight < width()) {
            int[] coords = order.get(cursor++);
            inFlight++;
            // gen=true: generate if missing. The callback lands on the main thread and pulls
            // the next chunk, so the pipeline stays exactly as wide as width() allows.
            world.getChunkAtAsync(coords[0], coords[1], true).whenComplete((chunk, error) -> {
                inFlight--;
                done++;
                if (stopped) {
                    return; // the server is going down; not another word, not another chunk
                }
                if (error != null) {
                    plugin.getLogger().warning("Pre-generation failed at chunk " + coords[0]
                            + "," + coords[1] + ": " + error.getMessage());
                }
                logProgress();
                pump();
            });
        }
    }

    private void logProgress() {
        int total = order.size();
        if (done >= total) {
            if (!finishedAnnounced) {
                finishedAnnounced = true;
                long seconds = (System.currentTimeMillis() - startedMillis) / 1000L;
                plugin.getLogger().info("Pre-generation complete: " + total + " chunks in "
                        + seconds + "s. No terrain generates mid-match now.");
            }
            return;
        }
        int decile = done * 10 / Math.max(1, total);
        if (decile > lastLoggedDecile) {
            lastLoggedDecile = decile;
            plugin.getLogger().info("Pre-generation " + decile * 10 + "% ("
                    + done + "/" + total + " chunks).");
        }
    }
}
