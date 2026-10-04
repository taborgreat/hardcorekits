package com.hardcorekits.movie;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * What each player has said about appearing in the match films.
 *
 * <p>Two choices, both optional: {@code /movie block} keeps a player's name and skin out of
 * every film (they appear as an anonymous tribute), and {@code /movie voice} picks the voice
 * their lines are spoken in. A player who never touches either is shown, with a voice chosen
 * for them.
 *
 * <p>Stored in {@code movie-prefs.json} in the data folder, keyed by UUID, so it survives
 * world rotation like the stats do. The film pipeline (mcmovie) reads the same file.
 */
public final class MoviePrefs {

    private static final String FILE = "movie-prefs.json";

    /** One selectable voice: the id the voice server knows it by, and the label players see. */
    public record Voice(String id, String label) {
    }

    private static final class Entry {
        String name;
        boolean blocked;
        String voice;
    }

    private final File file;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<Voice> voices = new ArrayList<>();
    /** Concurrent: the recorder's chat listener reads it off the main thread. */
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public MoviePrefs(File dataFolder, ConfigurationSection voiceSection, Logger logger) {
        this.file = new File(dataFolder, FILE);
        this.logger = logger;
        if (voiceSection != null) {
            for (String id : voiceSection.getKeys(false)) {
                voices.add(new Voice(id, voiceSection.getString(id, id)));
            }
        }
        load();
    }

    public List<Voice> voices() {
        return voices;
    }

    public boolean isBlocked(UUID uuid) {
        Entry entry = entries.get(uuid);
        return entry != null && entry.blocked;
    }

    /** The voice id this player chose, or null if they left it to chance. */
    public String voice(UUID uuid) {
        Entry entry = entries.get(uuid);
        return entry == null ? null : entry.voice;
    }

    /** @return the new state: true if the player is now blocked */
    public boolean toggleBlocked(UUID uuid, String name) {
        Entry entry = entry(uuid, name);
        entry.blocked = !entry.blocked;
        save();
        return entry.blocked;
    }

    public void setVoice(UUID uuid, String name, String voiceId) {
        entry(uuid, name).voice = voiceId;
        save();
    }

    /** Finds a voice by its number in the list (1-based), its id, or its label. */
    public Voice find(String query) {
        String wanted = normalise(query);
        try {
            int number = Integer.parseInt(query.trim());
            return number >= 1 && number <= voices.size() ? voices.get(number - 1) : null;
        } catch (NumberFormatException ignored) {
            // not a number: fall through to names
        }
        for (Voice voice : voices) {
            if (normalise(voice.id()).equals(wanted) || normalise(voice.label()).equals(wanted)) {
                return voice;
            }
        }
        return null;
    }

    public Voice byId(String id) {
        for (Voice voice : voices) {
            if (voice.id().equals(id)) {
                return voice;
            }
        }
        return null;
    }

    private static String normalise(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private Entry entry(UUID uuid, String name) {
        Entry entry = entries.computeIfAbsent(uuid, key -> new Entry());
        entry.name = name;
        return entry;
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject players = root.getAsJsonObject("players");
            if (players == null) {
                return;
            }
            for (String key : players.keySet()) {
                entries.put(UUID.fromString(key), gson.fromJson(players.get(key), Entry.class));
            }
        } catch (IOException | RuntimeException e) {
            logger.warning("Could not read " + FILE + ": " + e.getMessage());
        }
    }

    private synchronized void save() {
        Map<String, Entry> players = new LinkedHashMap<>();
        entries.forEach((uuid, entry) -> players.put(uuid.toString(), entry));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("players", players);
        try {
            File folder = file.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                logger.warning("Could not create " + folder + " for " + FILE + ".");
                return;
            }
            Files.writeString(file.toPath(), gson.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warning("Could not save " + FILE + ": " + e.getMessage());
        }
    }
}
