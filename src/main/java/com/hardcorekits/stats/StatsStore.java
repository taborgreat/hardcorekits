package com.hardcorekits.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Lifetime per-player numbers: games entered, kills, wins.
 *
 * <p>Kept in the plugin's data folder, so it survives the world rotation that throws
 * everything else away — the stats are the one thing that is supposed to outlive a match.
 *
 * <p>Keyed by UUID with the last seen name carried alongside, so a rename does not orphan a
 * record and a website search by name still works. Saved on every change: a write is a few
 * kilobytes at the end of rare events (a kill, a win, a match start), not something worth a
 * debounce that could lose the last game of the night in a crash.
 *
 * <p>The map is concurrent on purpose — the web endpoint reads it from an HTTP thread while
 * the game writes it from the main thread.
 */
public final class StatsStore {

    /** One player's lifetime record. Public fields, because Gson is the only other reader. */
    public static final class Entry {
        public String name = "";
        public int games;
        public int kills;
        public int wins;
        /** Wins in a row right now, and the best run ever. Losing any match resets the run. */
        public int streak;
        public int bestStreak;
        /** Last time this player was seen online, epoch millis. 0 for pre-tracking records. */
        public long lastSeen;
        /**
         * The same three numbers again, per kit id ("none" for no kit). A plain map on
         * purpose: a kit that does not exist yet is just a key that has not been written yet,
         * so new kits appear on the website without anyone touching this file. Concrete
         * ConcurrentHashMap so Gson deserialises into something the web thread can read while
         * the game writes.
         */
        public ConcurrentHashMap<String, KitLine> kits = new ConcurrentHashMap<>();
    }

    /** Per-kit slice of an {@link Entry}. */
    public static final class KitLine {
        public int games;
        public int kills;
        public int wins;
    }

    /** What sits in stats.json: the per-player map, plus the one server-wide number. */
    private static final class FileShape {
        Map<UUID, Entry> players;
        int matches;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final Logger logger;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    /** Matches started on this server, ever. Volatile: the web thread reads it. */
    private volatile int matches;

    public StatsStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "stats.json");
        this.logger = logger;
        load();
    }

    // ---------------------------------------------------------------- recording

    /** Called once per match, at the drop. The website's "games played" headline. */
    public void recordMatchStart() {
        matches++;
        save();
    }

    /** Matches ever started here. */
    public int totalMatches() {
        return matches;
    }

    /** A real player entered a match. Fakes never reach this. */
    public void recordGameStart(Player player, String kitId) {
        Entry entry = entry(player.getUniqueId(), player.getName());
        entry.games++;
        kitLine(entry, kitId).games++;
        save();
    }

    /** The kit is read at the moment of the kill, straight off the registry. */
    public void recordKill(Player killer, String kitId) {
        Entry entry = entry(killer.getUniqueId(), killer.getName());
        entry.kills++;
        kitLine(entry, kitId).kills++;
        save();
    }

    public void recordWin(UUID uuid, String name, String kitId) {
        Entry entry = entry(uuid, name);
        entry.wins++;
        entry.streak++;
        entry.bestStreak = Math.max(entry.bestStreak, entry.streak);
        kitLine(entry, kitId).wins++;
        save();
    }

    /**
     * A match ended and this player was not the winner: the run is over.
     *
     * <p>Only known players — an unknown UUID here is a fake tribute, and fakes have no
     * lifetime to lose. No save of its own: the caller ends the match with a {@link #flush}
     * or a win, so a whole lobby's worth of resets costs one write.
     */
    public void recordLoss(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry != null) {
            entry.streak = 0;
        }
    }

    /** Marks a player as seen right now. In-memory only; rides the next save to disk. */
    public void touch(UUID uuid, String name) {
        entry(uuid, name).lastSeen = System.currentTimeMillis();
    }

    /** Writes the current state out. For batch operations and the shutdown path. */
    public void flush() {
        save();
    }

    private Entry entry(UUID uuid, String name) {
        Entry entry = entries.computeIfAbsent(uuid, ignored -> new Entry());
        if (name != null && !name.isBlank()) {
            entry.name = name;
        }
        return entry;
    }

    private KitLine kitLine(Entry entry, String kitId) {
        String key = kitId == null || kitId.isBlank() ? "none" : kitId;
        return entry.kits.computeIfAbsent(key, ignored -> new KitLine());
    }

    // ---------------------------------------------------------------- reading

    /** Case-insensitive name lookup, or null. A scan — fine at server-population scale. */
    public Entry byName(String name) {
        for (Entry entry : entries.values()) {
            if (entry.name.equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    /** Most wins first, kills as the tiebreak. */
    public List<Entry> top(int limit) {
        List<Entry> all = new ArrayList<>(entries.values());
        all.sort(Comparator.comparingInt((Entry e) -> e.wins).thenComparingInt(e -> e.kills)
                .reversed());
        return all.subList(0, Math.min(limit, all.size()));
    }

    // ---------------------------------------------------------------- persistence

    private void load() {
        if (!file.isFile()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            FileShape shape = GSON.fromJson(reader, FileShape.class);
            if (shape != null && shape.players != null) {
                entries.putAll(shape.players);
                matches = shape.matches;
            } else if (shape != null) {
                // A file from before the match counter: the whole root was the player map.
                try (Reader again = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                    Map<UUID, Entry> legacy = GSON.fromJson(again,
                            new TypeToken<Map<UUID, Entry>>() { }.getType());
                    if (legacy != null) {
                        entries.putAll(legacy);
                    }
                }
            }
            // Files written before per-kit stats existed have no kits object; heal them.
            for (Entry entry : entries.values()) {
                if (entry.kits == null) {
                    entry.kits = new ConcurrentHashMap<>();
                }
            }
        } catch (IOException | RuntimeException e) {
            // A corrupt file must not brick the plugin; it does cost the history, so shout.
            logger.severe("Could not read stats.json — starting with empty stats: " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.toPath().getParent());
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                FileShape shape = new FileShape();
                shape.players = entries;
                shape.matches = matches;
                GSON.toJson(shape, writer);
            }
        } catch (IOException e) {
            logger.warning("Could not save stats.json: " + e.getMessage());
        }
    }
}
