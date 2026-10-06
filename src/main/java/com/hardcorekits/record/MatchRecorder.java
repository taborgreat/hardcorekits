package com.hardcorekits.record;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.game.GameState;
import com.hardcorekits.movie.MoviePrefs;
import com.hardcorekits.world.WorldRotator;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Records a match to disk so the movie pipeline (mcmovie) can replay it afterwards.
 *
 * <p>Deliberately outside the game: it never changes state, never cancels an event and never
 * touches a player. It watches {@link GameManager#state()} to know when a match is live and
 * logs the events that tell the story — deaths, hits, block changes, explosions, lightning,
 * projectiles, chat.
 *
 * <p>Positions are sampled at three speeds. A player who is just travelling is sampled every
 * {@code idle-sample-ticks} (a point every couple of seconds; the film connects the dots over
 * the saved terrain). With another tribute within {@code near-blocks} they are sampled every
 * {@code near-sample-ticks} — an encounter may be coming. They are <em>hot</em>, sampled
 * every {@code sample-ticks}, only while something is actually happening: damage dealt or
 * taken, another tribute within {@code close-blocks}, a projectile fired, a teleport, the
 * opening drop, or the winner's ceremony. A player who has not moved or changed since their
 * last line is not written again until the idle interval comes round. So the file is dense
 * exactly where a camera will want detail and nearly empty everywhere else, which keeps both
 * the server's work and the storyteller's reading small.
 *
 * <p>Three cheap extras make the footage feel inhabited. A travelling player gets an extra
 * point whenever they turn sharply or have covered {@code travel-blocks}, so a path with few
 * points still bends where the player did. The animals and monsters within
 * {@code ambient-blocks} of a player in a fight are followed at the middle rate. And armour is
 * logged when it changes, so a geared player can be drawn geared.
 *
 * <p>A recording is a folder under {@code plugins/HardcoreGames/recordings/<id>/}:
 * <ul>
 *   <li>{@code tracks.csv.gz}   — {@code t,player,x,y,z,yaw,pitch,flags,health}</li>
 *   <li>{@code entities.csv.gz} — {@code t,entityId,type,x,y,z,yaw,pitch} for projectiles and
 *       mobs that fought a player</li>
 *   <li>{@code events.jsonl.gz} — one JSON object per line, each with tick {@code t}</li>
 *   <li>{@code meta.json}       — written last; a recording without it never finished</li>
 *   <li>{@code region/}         — the match world's region files, moved in on the next boot
 *       instead of being deleted with the retired world</li>
 * </ul>
 * {@code t} is server ticks since the drop. The movie pipeline deletes the folder once its
 * video is done; {@code keep-max} bounds the disk cost if nobody ever collects them.
 */
public final class MatchRecorder implements Listener {

    private static final String ROOT_FOLDER = "recordings";
    private static final String META_FILE = "meta.json";
    private static final String REGION_FOLDER = "region";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    // Bits of the per-sample flags column.
    private static final int SNEAKING = 1;
    private static final int SPRINTING = 2;
    private static final int SWIMMING = 4;
    private static final int GLIDING = 8;
    private static final int ON_GROUND = 16;
    private static final int HAND_RAISED = 32;
    private static final int INVISIBLE = 64;
    private static final int IN_VEHICLE = 128;
    private static final int SWUNG = 256;
    private static final int HURT = 512;

    private final HardcoreGames plugin;
    private final GameManager game;
    /** Who asked to stay out of the films, and who chose a voice. */
    private final MoviePrefs prefs;
    private final Gson gson = new Gson();

    private final boolean enabled;
    /** Ticks between samples of a hot player, and how often the sampler itself runs. */
    private final int sampleTicks;
    /** Ticks between samples of a player nothing is happening to. */
    private final int idleSampleTicks;
    /** Ticks between samples of a player with another tribute nearby but no fight yet. */
    private final int nearSampleTicks;
    private final double nearBlocks;
    private final double closeBlocks;
    private final int combatHoldTicks;
    private final int dropTicks;
    /** A travelling player is sampled early after this many blocks, or a turn this sharp. */
    private final double travelBlocks;
    private final double turnDegrees;
    /** Living things this close to a player in a fight are part of the scene. */
    private final double ambientBlocks;
    private final int ambientMax;
    private final int minPlayers;
    private final boolean recordChat;
    private final int projectileTicks;
    private final int mobTicks;
    private final double minLoggedDamage;
    private final double minLoggedTeleport;

    private BukkitTask task;
    private GameState lastState;

    // ---- per-recording state; null/empty outside a match
    private volatile RecordingWriter out;
    private volatile int tick;
    private File directory;
    private String id;
    private int startTick;
    private long startMillis;
    private String worldName;
    private final Map<UUID, Integer> index = new HashMap<>();
    private final List<JsonObject> roster = new ArrayList<>();
    /** Slot to UUID, kept in memory only: an anonymous player's UUID is never written. */
    private final List<UUID> slotOwners = new ArrayList<>();
    private final Map<UUID, String> held = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    /** Tick until which a player is sampled at the fast rate. */
    private final Map<UUID, Integer> hotUntil = new HashMap<>();
    private final Map<UUID, Integer> lastSampled = new HashMap<>();
    /** Tick until which a player is sampled at the middle rate. */
    private final Map<UUID, Integer> nearUntil = new HashMap<>();
    /** What a player's last written line said, to skip writing the same thing again. */
    private final Map<UUID, double[]> lastWritten = new HashMap<>();
    /** Heading (dx, dz, normalised) a travelling player had when last sampled. */
    private final Map<UUID, double[]> lastHeading = new HashMap<>();
    private final Map<UUID, String> armour = new HashMap<>();
    private int lastAmbientPass;
    private int lastProximityPass;
    private final Set<UUID> swung = new HashSet<>();
    private final Set<UUID> hurt = new HashSet<>();
    private Set<UUID> lastAlive = new HashSet<>();
    /** Entity id to the entity and the tick it stops being interesting. */
    private final Map<Integer, Tracked> tracked = new HashMap<>();

    /** {@code every}: ticks between samples of this entity (scenery is sampled more slowly). */
    private final Map<Integer, double[]> lastEntity = new HashMap<>();

    private record Tracked(Entity entity, String type, int until, int every) {
    }

    public MatchRecorder(HardcoreGames plugin, GameManager game, MoviePrefs prefs) {
        this.plugin = plugin;
        this.game = game;
        this.prefs = prefs;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("recording");
        this.enabled = section != null && section.getBoolean("enabled", false);
        this.sampleTicks = section == null ? 2 : Math.max(1, section.getInt("sample-ticks", 2));
        this.idleSampleTicks = section == null ? 40 : Math.max(sampleTicks, section.getInt("idle-sample-ticks", 40));
        this.nearSampleTicks = section == null ? 10 : Math.max(sampleTicks, section.getInt("near-sample-ticks", 10));
        this.nearBlocks = section == null ? 24.0D : section.getDouble("near-blocks", 24.0D);
        this.closeBlocks = section == null ? 8.0D : section.getDouble("close-blocks", 8.0D);
        this.combatHoldTicks = section == null ? 200 : section.getInt("combat-hold-ticks", 200);
        this.dropTicks = section == null ? 100 : section.getInt("drop-ticks", 100);
        this.travelBlocks = section == null ? 7.0D : section.getDouble("travel-blocks", 7.0D);
        this.turnDegrees = section == null ? 35.0D : section.getDouble("turn-degrees", 35.0D);
        this.ambientBlocks = section == null ? 20.0D : section.getDouble("ambient-blocks", 20.0D);
        this.ambientMax = section == null ? 12 : section.getInt("ambient-max", 12);
        this.minPlayers = section == null ? 2 : section.getInt("min-players", 2);
        this.recordChat = section != null && section.getBoolean("chat", true);
        this.projectileTicks = section == null ? 400 : section.getInt("projectile-track-ticks", 400);
        this.mobTicks = section == null ? 300 : section.getInt("mob-track-ticks", 300);
        this.minLoggedDamage = section == null ? 2.0D : section.getDouble("min-logged-damage", 2.0D);
        this.minLoggedTeleport = section == null ? 6.0D : section.getDouble("min-logged-teleport", 6.0D);
    }

    public void start() {
        if (!enabled) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 1L, sampleTicks);
        plugin.getLogger().info("Match recording is on (every " + sampleTicks + " ticks in a fight, "
                + nearSampleTicks + " near another player, " + idleSampleTicks + " otherwise).");
    }

    /** Plugin shutdown: a match cut short is closed as incomplete and never becomes a movie. */
    public void close() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (out != null) {
            finish(game.state() == GameState.ENDING || game.state() == GameState.RESETTING);
        }
    }

    // ---------------------------------------------------------------- boot-time housekeeping

    private static File root(Plugin plugin) {
        return new File(plugin.getDataFolder(), ROOT_FOLDER);
    }

    /**
     * Rescues the region files of the world the last shutdown retired, if a finished recording
     * is waiting for them. Must run before {@link WorldRotator#purgePrevious(Plugin)}, which
     * deletes whatever is left of that world.
     */
    public static void adoptRetiredWorld(Plugin plugin) {
        String retired = WorldRotator.retiredWorld(plugin);
        if (retired == null || retired.isBlank()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equals(retired)) {
                return; // booted straight back into it; nothing is retired
            }
        }
        File[] folders = root(plugin).listFiles(File::isDirectory);
        if (folders == null) {
            return;
        }
        for (File folder : folders) {
            JsonObject meta = readMeta(folder);
            if (meta == null || !meta.has("world") || !retired.equals(meta.get("world").getAsString())
                    || !meta.has("complete") || !meta.get("complete").getAsBoolean()) {
                continue;
            }
            File target = new File(folder, REGION_FOLDER);
            if (target.exists()) {
                continue;
            }
            File container = Bukkit.getWorldContainer();
            File source = new File(container, retired + "/dimensions/minecraft/overworld/region");
            if (!source.isDirectory()) {
                source = new File(container, retired + "/region");
            }
            if (!source.isDirectory()) {
                plugin.getLogger().warning("Recording " + folder.getName() + ": no region folder in '"
                        + retired + "' to keep.");
                continue;
            }
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
                plugin.getLogger().info("Recording " + folder.getName() + " kept the map of '"
                        + retired + "'.");
            } catch (IOException e) {
                plugin.getLogger().warning("Recording " + folder.getName()
                        + " could not keep its map: " + e.getMessage());
            }
        }
    }

    /** Drops recordings that never finished, then the oldest beyond {@code keep-max}. */
    public static void prune(Plugin plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("recording");
        int keepMax = section == null ? 3 : section.getInt("keep-max", 3);
        File[] folders = root(plugin).listFiles(File::isDirectory);
        if (folders == null) {
            return;
        }
        Arrays.sort(folders); // ids are timestamps, so name order is age order
        List<File> finished = new ArrayList<>();
        for (File folder : folders) {
            JsonObject meta = readMeta(folder);
            boolean complete = meta != null && meta.has("complete") && meta.get("complete").getAsBoolean();
            if (complete && new File(folder, REGION_FOLDER).isDirectory()) {
                finished.add(folder);
            } else {
                deleteRecursively(folder);
                plugin.getLogger().info("Dropped unfinished recording " + folder.getName() + ".");
            }
        }
        for (int i = 0; i < finished.size() - Math.max(0, keepMax); i++) {
            deleteRecursively(finished.get(i));
            plugin.getLogger().info("Dropped old recording " + finished.get(i).getName()
                    + " (keep-max " + keepMax + ").");
        }
    }

    private static JsonObject readMeta(File folder) {
        File file = new File(folder, META_FILE);
        if (!file.isFile()) {
            return null;
        }
        try {
            return JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    // ---------------------------------------------------------------- the sampler

    private void onTick() {
        try {
            sample();
        } catch (RuntimeException e) {
            // A recorder bug must cost a recording, never a match: stop recording and say why.
            plugin.getLogger().warning("Match recording stopped after an internal error: " + e);
            RecordingWriter writer = out;
            out = null;
            if (writer != null) {
                writer.close();
            }
        }
    }

    private void sample() {
        GameState state = game.state();
        GameState previous = lastState;
        lastState = state;

        boolean began = false;
        if (out == null) {
            // Only a real transition into a live match starts a recording.
            if (previous != null && !previous.isLive() && state.isLive()
                    && game.alivePlayers().size() >= minPlayers) {
                begin(state);
                began = true;
            }
            if (out == null) {
                return;
            }
        }

        tick = Bukkit.getCurrentTick() - startTick;
        if (state != previous && previous != null && !began) {
            JsonObject event = event("state");
            event.addProperty("state", state.name());
            write(event);
        }
        if (state == GameState.RESETTING || state.isPreGame()) {
            finish(true);
            return;
        }

        watchAliveSet();
        samplePlayers();
        sampleTracked();
    }

    private void begin(GameState state) {
        id = LocalDateTime.now().format(STAMP);
        directory = new File(root(plugin), id);
        if (!directory.mkdirs()) {
            plugin.getLogger().warning("Could not create " + directory + " — match not recorded.");
            return;
        }
        try {
            out = new RecordingWriter(directory, plugin.getLogger());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not open the match recording: " + e.getMessage());
            return;
        }
        startTick = Bukkit.getCurrentTick();
        startMillis = System.currentTimeMillis();
        tick = 0;
        World world = game.config().world();
        worldName = world == null ? null : world.getName();
        index.clear();
        lastEntity.clear();
        highlights.clear();
        lastAbility.clear();
        roster.clear();
        slotOwners.clear();
        held.clear();
        kills.clear();
        swung.clear();
        hurt.clear();
        tracked.clear();
        hotUntil.clear();
        nearUntil.clear();
        lastSampled.clear();
        lastWritten.clear();
        lastHeading.clear();
        armour.clear();
        lastAmbientPass = 0;
        lastProximityPass = 0;
        lastAlive = new HashSet<>(game.alive());

        JsonObject event = event("state");
        event.addProperty("state", state.name());
        write(event);
        plugin.getLogger().info("Recording match " + id + ".");
    }

    private void finish(boolean complete) {
        RecordingWriter writer = out;
        out = null;
        if (writer == null) {
            return;
        }
        writer.close();

        String winner = null;
        if (game.alive().size() == 1) {
            UUID uuid = game.alive().iterator().next();
            Integer slot = index.get(uuid);
            winner = slot == null ? null : roster.get(slot).get("name").getAsString();
        }
        JsonObject meta = new JsonObject();
        meta.addProperty("id", id);
        meta.addProperty("format", 1);
        meta.addProperty("complete", complete && !writer.failed());
        meta.addProperty("world", worldName);
        meta.addProperty("minecraft", Bukkit.getMinecraftVersion());
        meta.addProperty("started_ms", startMillis);
        meta.addProperty("ended_ms", System.currentTimeMillis());
        meta.addProperty("ticks", tick);
        meta.addProperty("tick_rate", 20);
        meta.addProperty("sample_ticks", sampleTicks);
        meta.addProperty("near_sample_ticks", nearSampleTicks);
        meta.addProperty("idle_sample_ticks", idleSampleTicks);
        meta.addProperty("starting_players", game.startingPlayers());
        if (winner != null) {
            meta.addProperty("winner", winner);
        }
        JsonArray players = new JsonArray();
        for (int slot = 0; slot < roster.size(); slot++) {
            JsonObject entry = roster.get(slot);
            entry.addProperty("kills", kills.getOrDefault(slotOwners.get(slot), 0));
            players.add(entry);
        }
        meta.add("players", players);
        try {
            Files.writeString(new File(directory, META_FILE).toPath(), gson.toJson(meta),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write the recording's meta.json: " + e.getMessage());
        }
        plugin.getLogger().info("Recording " + id + " closed after " + tick + " ticks"
                + (complete ? "." : " (incomplete)."));
        tracked.clear();
    }

    /** Every route out of a match ends in the alive set shrinking, so that is what is watched. */
    private void watchAliveSet() {
        Set<UUID> now = new HashSet<>(game.alive());
        for (UUID uuid : lastAlive) {
            if (!now.contains(uuid) && index.containsKey(uuid)) {
                JsonObject event = event("eliminated");
                event.addProperty("p", index.get(uuid));
                event.addProperty("remaining", now.size());
                write(event);
            }
        }
        lastAlive = now;
    }

    private int slot(Player player) {
        Integer known = index.get(player.getUniqueId());
        if (known != null) {
            return known;
        }
        int slot = roster.size();
        index.put(player.getUniqueId(), slot);
        slotOwners.add(player.getUniqueId());
        // A player who ran /movie block never has their name or UUID written anywhere: the
        // recording knows them only as a numbered tribute.
        boolean anonymous = prefs != null && prefs.isBlocked(player.getUniqueId());
        String shown = anonymous ? "Tribute" + (slot + 1) : player.getName();
        String voice = prefs == null ? null : prefs.voice(player.getUniqueId());
        JsonObject entry = new JsonObject();
        entry.addProperty("p", slot);
        entry.addProperty("name", shown);
        entry.addProperty("uuid", anonymous ? "" : player.getUniqueId().toString());
        entry.addProperty("kit", game.kitIdOf(player.getUniqueId()));
        if (anonymous) {
            entry.addProperty("anon", true);
        }
        if (voice != null) {
            entry.addProperty("voice", voice);
        }
        roster.add(entry);

        JsonObject event = event("player");
        event.addProperty("p", slot);
        event.addProperty("name", shown);
        event.addProperty("kit", game.kitIdOf(player.getUniqueId()));
        write(event);
        return slot;
    }

    /** Marks a player as worth watching closely for a while, and due for a sample right now. */
    private void heat(Player player, int forTicks) {
        UUID uuid = player.getUniqueId();
        if (hotUntil.getOrDefault(uuid, -1) < tick) {
            lastSampled.remove(uuid); // just turned hot: do not wait out the idle interval
        }
        hotUntil.merge(uuid, tick + forTicks, Math::max);
    }

    /**
     * Once a second: two tributes within near-blocks may be about to meet (middle rate); within
     * close-blocks they are as good as fighting (fast rate).
     */
    private void proximityPass(List<Player> players) {
        double near = nearBlocks * nearBlocks;
        double close = closeBlocks * closeBlocks;
        for (int i = 0; i < players.size(); i++) {
            Location a = players.get(i).getLocation();
            for (int j = i + 1; j < players.size(); j++) {
                Location b = players.get(j).getLocation();
                if (a.getWorld() != b.getWorld()) {
                    continue;
                }
                double distance = a.distanceSquared(b);
                if (distance <= close) {
                    heat(players.get(i), 40);
                    heat(players.get(j), 40);
                } else if (distance <= near) {
                    nearUntil.put(players.get(i).getUniqueId(), tick + 40);
                    nearUntil.put(players.get(j).getUniqueId(), tick + 40);
                }
            }
        }
    }

    private void samplePlayers() {
        List<Player> players = game.alivePlayers();
        if (tick - lastProximityPass >= 20) {
            lastProximityPass = tick;
            proximityPass(players);
        }
        if (tick % 600 == 0) {
            sky(); // the world's clock and weather, so the film has the same sky
        }
        if (tick - lastAmbientPass >= nearSampleTicks) {
            lastAmbientPass = tick;
            ambientPass(players);
        }
        boolean everyoneHot = tick < dropTicks || game.state() == GameState.ENDING;
        StringBuilder lines = new StringBuilder(64 * 8);
        for (Player player : players) {
            UUID uuid = player.getUniqueId();
            boolean hot = everyoneHot || hotUntil.getOrDefault(uuid, -1) >= tick;
            int interval = hot ? sampleTicks
                    : (nearUntil.getOrDefault(uuid, -1) >= tick ? nearSampleTicks : idleSampleTicks);
            Integer last = lastSampled.get(uuid);
            if (last != null && tick - last < interval && !(interval > sampleTicks && pathChanged(player))) {
                continue;
            }
            if (last != null && tick - last < idleSampleTicks && unchanged(player)) {
                continue; // standing still: the last line already says everything
            }
            appendSample(lines, player);
        }
        if (lines.length() > 0) {
            out.tracks(lines.toString());
        }
    }

    /**
     * True if a travelling player has turned sharply or gone far since their last line — the
     * two things a straight line between sparse points would get wrong.
     */
    private boolean pathChanged(Player player) {
        UUID uuid = player.getUniqueId();
        double[] was = lastWritten.get(uuid);
        if (was == null) {
            return false;
        }
        Location at = player.getLocation();
        double dx = at.getX() - was[0];
        double dz = at.getZ() - was[2];
        double moved = Math.sqrt(dx * dx + dz * dz);
        if (moved >= travelBlocks || Math.abs(at.getY() - was[1]) >= 3.0D) {
            return true;
        }
        if (moved < 1.5D) {
            return false;
        }
        double[] heading = lastHeading.get(uuid);
        if (heading == null) {
            lastHeading.put(uuid, new double[] {dx / moved, dz / moved});
            return false;
        }
        double dot = (dx / moved) * heading[0] + (dz / moved) * heading[1];
        return dot < Math.cos(Math.toRadians(turnDegrees));
    }

    /**
     * The animals and monsters around a player in a fight. Followed like the mobs that fought
     * a player, at the middle rate, nearest first, capped — a herd is scenery, not a census.
     */
    private void ambientPass(List<Player> players) {
        for (Player player : players) {
            if (hotUntil.getOrDefault(player.getUniqueId(), -1) < tick) {
                // Not in a fight yet, but something may be coming for them: a creature that has
                // picked a player as its target (the golem walking over, the zombie closing in)
                // is followed closely from that moment, so its approach and its first swing are
                // on the record instead of starting at the first hit. Checked once a second.
                if (tick % 20 < nearSampleTicks) {
                    // ...and whatever else is standing around them is followed slowly, so the golem
                    // in the village and the zombie in the cave are in the film from the moment the
                    // player walks up, not from the moment they trade blows.
                    int seen = 0;
                    for (Entity entity : player.getNearbyEntities(28.0D, 12.0D, 28.0D)) {
                        if (!(entity instanceof LivingEntity) || entity instanceof Player
                                || entity instanceof org.bukkit.entity.WaterMob
                                || entity instanceof org.bukkit.entity.Ambient) {
                            continue;
                        }
                        if (entity instanceof org.bukkit.entity.Mob mob && mob.getTarget() instanceof Player) {
                            track(entity, nearSampleTicks * 6);
                            continue;
                        }
                        boolean notable = entity instanceof org.bukkit.entity.Monster
                                || entity instanceof org.bukkit.entity.Golem
                                || entity instanceof org.bukkit.entity.Tameable
                                || entity instanceof org.bukkit.entity.AbstractVillager;
                        if (!notable && entity.getLocation().distanceSquared(player.getLocation()) > 256.0D) {
                            continue; // ordinary animals only when they are close
                        }
                        if (seen++ >= 20) {
                            break;
                        }
                        Tracked known = tracked.get(entity.getEntityId());
                        if (known == null || (known.every() >= 20 && known.until() < tick + 30)) {
                            track(entity, 60, 20);
                        }
                    }
                }
                continue;
            }
            Location centre = player.getLocation();
            int added = 0;
            for (Entity entity : player.getNearbyEntities(ambientBlocks, ambientBlocks / 2.0D, ambientBlocks)) {
                if (added >= ambientMax) {
                    break;
                }
                if (!(entity instanceof LivingEntity) || entity instanceof Player
                        || entity instanceof org.bukkit.entity.WaterMob
                        || entity instanceof org.bukkit.entity.Ambient) {
                    continue; // fish, squid and bats are not scenery worth the bytes
                }
                if (entity instanceof org.bukkit.entity.Mob mob && mob.getTarget() instanceof Player) {
                    track(entity, nearSampleTicks * 6); // hunting a player: every move counts
                    added++;
                    continue;
                }
                Tracked known = tracked.get(entity.getEntityId());
                if (known == null) {
                    track(entity, nearSampleTicks * 6, nearSampleTicks);
                } else if (known.every() > sampleTicks && known.until() < tick + nearSampleTicks * 3) {
                    track(entity, nearSampleTicks * 6, nearSampleTicks); // still scenery: keep it, slowly
                }
                added++;
            }
        }
    }

    /** True if nothing a track line records has changed since this player's last line. */
    private boolean unchanged(Player player) {
        UUID uuid = player.getUniqueId();
        double[] was = lastWritten.get(uuid);
        if (was == null || swung.contains(uuid) || hurt.contains(uuid)) {
            return false;
        }
        Location at = player.getLocation();
        return Math.abs(at.getX() - was[0]) < 0.03D && Math.abs(at.getY() - was[1]) < 0.03D
                && Math.abs(at.getZ() - was[2]) < 0.03D && Math.abs(at.getYaw() - was[3]) < 1.5D
                && Math.abs(at.getPitch() - was[4]) < 1.5D && Math.abs(player.getHealth() - was[5]) < 0.05D
                && (player.isSneaking() ? 1.0D : 0.0D) == was[6];
    }

    /** One track line for one player, plus the held-item and kit bookkeeping that rides along. */
    private void appendSample(StringBuilder lines, Player player) {
        int slot = slot(player);
        UUID uuid = player.getUniqueId();
        lastSampled.put(uuid, tick);
        Location at = player.getLocation();
        lastWritten.put(uuid, new double[] {at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch(),
                player.getHealth(), player.isSneaking() ? 1.0D : 0.0D});

        int flags = 0;
        if (player.isSneaking()) flags |= SNEAKING;
        if (player.isSprinting()) flags |= SPRINTING;
        if (player.isSwimming()) flags |= SWIMMING;
        if (player.isGliding()) flags |= GLIDING;
        if (((Entity) player).isOnGround()) flags |= ON_GROUND;
        if (player.isHandRaised()) flags |= HAND_RAISED;
        if (player.isInvisible()) flags |= INVISIBLE;
        if (player.isInsideVehicle()) flags |= IN_VEHICLE;
        if (swung.remove(uuid)) flags |= SWUNG;
        if (hurt.remove(uuid)) flags |= HURT;

        lines.append(tick).append(',').append(slot).append(',')
                .append(round(at.getX())).append(',').append(round(at.getY())).append(',')
                .append(round(at.getZ())).append(',')
                .append(Math.round(at.getYaw() * 10.0F) / 10.0F).append(',')
                .append(Math.round(at.getPitch() * 10.0F) / 10.0F).append(',')
                .append(flags).append(',')
                .append(Math.round(player.getHealth() * 10.0D) / 10.0D).append('\n');

        Material material = player.getInventory().getItemInMainHand().getType();
        String item = material.isAir() ? "" : material.name().toLowerCase(Locale.ROOT);
        if (!item.equals(held.getOrDefault(uuid, ""))) {
            held.put(uuid, item);
            JsonObject event = event("held");
            event.addProperty("p", slot);
            event.addProperty("item", item);
            write(event);
        }
        // heading at this sample, for the next turn check (yaw 0 faces +z)
        double yaw = Math.toRadians(at.getYaw());
        lastHeading.put(uuid, new double[] {-Math.sin(yaw), Math.cos(yaw)});
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        String worn = piece(inventory.getHelmet()) + "," + piece(inventory.getChestplate()) + ","
                + piece(inventory.getLeggings()) + "," + piece(inventory.getBoots());
        if (!worn.equals(armour.getOrDefault(uuid, ",,,"))) {
            armour.put(uuid, worn);
            JsonObject event = event("armor");
            event.addProperty("p", slot);
            event.addProperty("worn", worn);
            write(event);
        }
        String kit = game.kitIdOf(uuid);
        JsonObject entry = roster.get(slot);
        if (!kit.equals(entry.get("kit").getAsString())) {
            entry.addProperty("kit", kit);
            JsonObject event = event("kit");
            event.addProperty("p", slot);
            event.addProperty("kit", kit);
            write(event);
        }
    }

    private static String piece(org.bukkit.inventory.ItemStack item) {
        return item == null || item.getType().isAir() ? "" : item.getType().name().toLowerCase(Locale.ROOT);
    }

    /** A sample outside the sampler's rhythm — the exact spot something happened. */
    private void sampleNow(Player player) {
        if (out == null || !index.containsKey(player.getUniqueId())) {
            return;
        }
        StringBuilder line = new StringBuilder(64);
        appendSample(line, player);
        out.tracks(line.toString());
    }

    private void sampleTracked() {
        if (tracked.isEmpty()) {
            return;
        }
        StringBuilder lines = new StringBuilder();
        Iterator<Map.Entry<Integer, Tracked>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Tracked> entry = iterator.next();
            Tracked item = entry.getValue();
            if (!item.entity().isValid() || tick > item.until()) {
                iterator.remove();
                lastEntity.remove(entry.getKey());
                JsonObject event = event("gone");
                event.addProperty("eid", entry.getKey());
                write(event);
                continue;
            }
            if (item.every() > sampleTicks && tick % item.every() != 0) {
                continue;
            }
            Location at = item.entity().getLocation();
            double[] was = lastEntity.get(entry.getKey());
            if (was != null && tick - (int) was[3] < 100 && Math.abs(at.getX() - was[0]) < 0.05D
                    && Math.abs(at.getY() - was[1]) < 0.05D && Math.abs(at.getZ() - was[2]) < 0.05D) {
                continue;
            }
            lastEntity.put(entry.getKey(), new double[] {at.getX(), at.getY(), at.getZ(), tick});
            lines.append(tick).append(',').append(entry.getKey()).append(',').append(item.type()).append(',')
                    .append(round(at.getX())).append(',').append(round(at.getY())).append(',')
                    .append(round(at.getZ())).append(',')
                    .append(Math.round(at.getYaw() * 10.0F) / 10.0F).append(',')
                    .append(Math.round(at.getPitch() * 10.0F) / 10.0F).append('\n');
        }
        if (lines.length() > 0) {
            out.entities(lines.toString());
        }
    }

    private void track(Entity entity, int forTicks) {
        track(entity, forTicks, sampleTicks);
    }

    private void track(Entity entity, int forTicks, int every) {
        tracked.put(entity.getEntityId(),
                new Tracked(entity, entity.getType().name().toLowerCase(Locale.ROOT), tick + forTicks, every));
    }

    // ---------------------------------------------------------------- event plumbing

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private JsonObject event(String type) {
        JsonObject event = new JsonObject();
        event.addProperty("t", tick);
        event.addProperty("type", type);
        return event;
    }

    private void write(JsonObject event) {
        RecordingWriter writer = out;
        if (writer != null) {
            writer.event(gson.toJson(event) + "\n");
        }
    }

    private static void position(JsonObject event, Location at) {
        event.addProperty("x", round(at.getX()));
        event.addProperty("y", round(at.getY()));
        event.addProperty("z", round(at.getZ()));
    }

    /** A recorded participant's slot, or null for anyone the recording does not follow. */
    private Integer participant(Entity entity) {
        return entity instanceof Player player ? index.get(player.getUniqueId()) : null;
    }

    // ---------------------------------------------------------------- listeners

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (out == null) {
            return;
        }
        Player victim = event.getEntity();
        Integer slot = participant(victim);
        if (slot == null) {
            return;
        }
        JsonObject line = event("death");
        line.addProperty("v", slot);
        position(line, victim.getLocation());
        EntityDamageEvent last = victim.getLastDamageCause();
        if (last != null) {
            line.addProperty("cause", last.getCause().name().toLowerCase(Locale.ROOT));
            // A death to a mob has no killer; name the mob so the story can.
            if (last instanceof EntityDamageByEntityEvent by && !(by.getDamager() instanceof Player)) {
                Entity source = by.getDamager();
                if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                    source = shooter;
                }
                if (!(source instanceof Player)) {
                    line.addProperty("mob", source.getType().name().toLowerCase(Locale.ROOT));
                }
            }
        }
        // how the server itself described it ("was stomped by ..."): a kit's kill is not the item in hand
        if (event.deathMessage() != null) {
            String said = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(event.deathMessage());
            if (!said.isBlank()) {
                line.addProperty("msg", said.length() > 160 ? said.substring(0, 160) : said);
            }
        }
        // what they dropped, so the film can show the loot spill out where they fell
        JsonArray spilled = new JsonArray();
        for (org.bukkit.inventory.ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir() || spilled.size() >= 14) {
                continue;
            }
            JsonArray one = new JsonArray();
            one.add(drop.getType().name().toLowerCase(Locale.ROOT));
            one.add(drop.getAmount());
            spilled.add(one);
        }
        if (!spilled.isEmpty()) {
            line.add("drops", spilled);
        }
        sampleNow(victim); // the track must end exactly where they fell
        Player killer = victim.getKiller();
        Integer killerSlot = killer == null || killer.equals(victim) ? null : participant(killer);
        if (killerSlot != null) {
            heat(killer, combatHoldTicks);
            line.addProperty("k", killerSlot);
            Material weapon = killer.getInventory().getItemInMainHand().getType();
            line.addProperty("weapon", weapon.isAir() ? "fist" : weapon.name().toLowerCase(Locale.ROOT));
            line.addProperty("k_hp", Math.round(killer.getHealth() * 10.0D) / 10.0D);
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
        }
        write(line);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (out == null) {
            return;
        }
        Entity victim = event.getEntity();
        Integer victimSlot = participant(victim);
        String cause = event.getCause().name().toLowerCase(Locale.ROOT);

        if (event instanceof EntityDamageByEntityEvent by) {
            Entity damager = by.getDamager();
            Player attacker = null;
            String via = null;
            if (damager instanceof Player player) {
                attacker = player;
            } else if (damager instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player shooter) {
                attacker = shooter;
                via = projectile.getType().name().toLowerCase(Locale.ROOT);
            }
            Integer attackerSlot = attacker == null ? null : participant(attacker);

            if (victimSlot != null) {
                Player hit = (Player) victim;
                JsonObject line = event("hit");
                line.addProperty("v", victimSlot);
                if (attackerSlot != null) {
                    line.addProperty("a", attackerSlot);
                } else {
                    line.addProperty("mob", damager.getType().name().toLowerCase(Locale.ROOT));
                    // which creature it was, so the film can make that one swing at this moment
                    Entity striker = attacker != null ? attacker : damager;
                    line.addProperty("me", striker.getEntityId());
                    if (striker instanceof LivingEntity) {
                        track(striker, mobTicks);
                    }
                    if (damager instanceof LivingEntity) {
                        track(damager, mobTicks);
                    }
                }
                if (via != null) {
                    line.addProperty("via", via);
                }
                line.addProperty("dmg", Math.round(event.getFinalDamage() * 10.0D) / 10.0D);
                line.addProperty("hp", Math.round(Math.max(0.0D, hit.getHealth() - event.getFinalDamage()) * 10.0D) / 10.0D);
                line.addProperty("cause", cause);
                write(line);
                hurt.add(hit.getUniqueId());
                heat(hit, combatHoldTicks);
                if (attackerSlot != null) {
                    heat(attacker, combatHoldTicks);
                }
            } else if (attackerSlot != null && victim instanceof LivingEntity living) {
                // A participant fighting a mob: keep the mob on camera for a while, and log the
                // blow against that very creature so the film can show it land (a kit's own
                // wolves and golems being cut down matters as much as what they do to players).
                track(victim, mobTicks);
                heat(attacker, combatHoldTicks);
                JsonObject line = event("mobhit");
                line.addProperty("a", attackerSlot);
                line.addProperty("me", victim.getEntityId());
                line.addProperty("mob", victim.getType().name().toLowerCase(Locale.ROOT));
                line.addProperty("dmg", Math.round(event.getFinalDamage() * 10.0D) / 10.0D);
                if (living.getHealth() - event.getFinalDamage() <= 0.0D) {
                    line.addProperty("dead", true);
                }
                write(line);
            }
            return;
        }

        if (victimSlot != null && event.getFinalDamage() >= minLoggedDamage) {
            Player hit = (Player) victim;
            JsonObject line = event("dmg");
            line.addProperty("v", victimSlot);
            line.addProperty("dmg", Math.round(event.getFinalDamage() * 10.0D) / 10.0D);
            line.addProperty("hp", Math.round(Math.max(0.0D, hit.getHealth() - event.getFinalDamage()) * 10.0D) / 10.0D);
            line.addProperty("cause", cause);
            write(line);
            hurt.add(hit.getUniqueId());
            heat(hit, combatHoldTicks / 2);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (out != null && index.containsKey(event.getPlayer().getUniqueId())) {
            swung.add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (out == null) {
            return;
        }
        blockChange(event.getBlock(), event.getBlockReplacedState().getBlockData().getAsString(),
                event.getBlock().getBlockData().getAsString(), "place", participant(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (out == null) {
            return;
        }
        blockChange(event.getBlock(), event.getBlock().getBlockData().getAsString(),
                "minecraft:air", "break", participant(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (out == null) {
            return;
        }
        String liquid = event.getBucket() == Material.LAVA_BUCKET ? "minecraft:lava" : "minecraft:water";
        blockChange(event.getBlock(), event.getBlock().getBlockData().getAsString(), liquid, "bucket",
                participant(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (out == null) {
            return;
        }
        blockChange(event.getBlock(), event.getBlock().getBlockData().getAsString(), "minecraft:air",
                "bucket", participant(event.getPlayer()));
    }

    /**
     * A block the plugin itself placed (the feast, the winner's cake tower). No event fires for
     * those, so without this the film sees them in the saved map with no idea when they
     * appeared, and shows the cake tower hanging in the sky from the first minute. Call with
     * what the block was BEFORE it was changed.
     */
    public void pluginSet(Block block, String was) {
        if (out == null || worldName == null || !block.getWorld().getName().equals(worldName)) {
            return;
        }
        String now = block.getBlockData().getAsString();
        if (!now.equals(was)) {
            blockChange(block, was, now, "plugin", null);
        }
    }

    private void blockChange(Block block, String from, String to, String how, Integer slot) {
        JsonObject line = event("block");
        line.addProperty("x", block.getX());
        line.addProperty("y", block.getY());
        line.addProperty("z", block.getZ());
        line.addProperty("from", from);
        line.addProperty("to", to);
        line.addProperty("how", how);
        if (slot != null) {
            line.addProperty("p", slot);
        }
        write(line);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (out != null) {
            explosion(event.getLocation(), event.getEntityType().name().toLowerCase(Locale.ROOT),
                    event.blockList());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (out != null) {
            explosion(event.getBlock().getLocation(), "block", event.blockList());
        }
    }

    private void explosion(Location at, String by, List<Block> blocks) {
        JsonObject line = event("explosion");
        position(line, at);
        line.addProperty("by", by);
        JsonArray broken = new JsonArray();
        for (Block block : blocks) {
            JsonArray entry = new JsonArray();
            entry.add(block.getX());
            entry.add(block.getY());
            entry.add(block.getZ());
            entry.add(block.getBlockData().getAsString());
            broken.add(entry);
        }
        line.add("blocks", broken);
        write(line);
    }

    /** The match world's time of day and weather right now. */
    private void sky() {
        World world = game.config().world();
        if (out == null || world == null) {
            return;
        }
        JsonObject line = event("sky");
        line.addProperty("time", world.getTime());
        line.addProperty("running", Boolean.TRUE.equals(world.getGameRuleValue(org.bukkit.GameRules.ADVANCE_TIME)));
        line.addProperty("storm", world.hasStorm());
        line.addProperty("thunder", world.isThundering());
        write(line);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeather(org.bukkit.event.weather.WeatherChangeEvent event) {
        if (out != null && event.getWorld().getName().equals(worldName)) {
            Bukkit.getScheduler().runTask(plugin, this::sky); // after the change has applied
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThunder(org.bukkit.event.weather.ThunderChangeEvent event) {
        if (out != null && event.getWorld().getName().equals(worldName)) {
            Bukkit.getScheduler().runTask(plugin, this::sky);
        }
    }

    // Every kit checks in with the registry before its ability fires, so this one hook sees all
    // of them without touching a single kit. Passive kits check constantly, so a player's
    // kit is logged at most once every two seconds: enough to say "he used it here".
    private final Map<UUID, Integer> lastAbility = new HashMap<>();

    public void ability(Player player, String kitId) {
        if (out == null) {
            return;
        }
        Integer slot = participant(player);
        if (slot == null) {
            return;
        }
        Integer last = lastAbility.get(player.getUniqueId());
        if (last != null && tick - last < 40) {
            return;
        }
        lastAbility.put(player.getUniqueId(), tick);
        JsonObject line = event("ability");
        line.addProperty("p", slot);
        line.addProperty("kit", kitId);
        position(line, player.getLocation());
        write(line);
    }

    /** How many moments a player may mark in one match. */
    private static final int MAX_HIGHLIGHTS = 3;
    private final Map<UUID, Integer> highlights = new HashMap<>();

    /**
     * /highlight: a player marks the last few seconds as something the film should look at,
     * with a few words on why. It is one more event in the recording, so it is deleted with it.
     *
     * @return the line to show the player
     */
    public String highlight(Player player, String why) {
        Integer slot = participant(player);
        if (out == null || slot == null) {
            return "You can only highlight during a match you are playing in.";
        }
        int used = highlights.getOrDefault(player.getUniqueId(), 0);
        if (used >= MAX_HIGHLIGHTS) {
            return "You have used all " + MAX_HIGHLIGHTS + " highlights this game.";
        }
        highlights.put(player.getUniqueId(), used + 1);
        JsonObject line = event("highlight");
        line.addProperty("p", slot);
        position(line, player.getLocation());
        line.addProperty("text", why.length() > 160 ? why.substring(0, 160) : why);
        write(line);
        return "Marked for the film (" + (used + 1) + "/" + MAX_HIGHLIGHTS + ").";
    }

    // A stew drunk in a fight is the heartbeat of this game's PvP: good players hotkey through
    // a whole bar of them. SoupListener cancels the click and swaps the stew for a bowl, so the
    // drink is only visible as a before/after: stew in the hand going in, a bowl coming out.
    private java.util.UUID stewClick;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStewClick(org.bukkit.event.player.PlayerInteractEvent event) {
        stewClick = null;
        if (out == null || event.getHand() == null || event.getItem() == null
                || event.getItem().getType() != org.bukkit.Material.MUSHROOM_STEW) {
            return;
        }
        org.bukkit.event.block.Action action = event.getAction();
        if (action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            stewClick = event.getPlayer().getUniqueId();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStewDrunk(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (out == null || stewClick == null || !stewClick.equals(player.getUniqueId()) || event.getHand() == null) {
            return;
        }
        stewClick = null;
        org.bukkit.inventory.ItemStack now = player.getInventory().getItem(event.getHand());
        if (now == null || now.getType() != org.bukkit.Material.BOWL) {
            return; // full health and full hunger: the stew was kept
        }
        Integer slot = participant(player);
        if (slot == null) {
            return;
        }
        JsonObject line = event("stew");
        line.addProperty("p", slot);
        line.addProperty("hp", Math.round(player.getHealth() * 10.0) / 10.0);
        write(line);
    }

    /** Something thrown on the ground (Q): shown in the film as the item leaving the hand. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
        Integer slot = participant(event.getPlayer());
        if (out == null || slot == null) {
            return;
        }
        org.bukkit.inventory.ItemStack stack = event.getItemDrop().getItemStack();
        JsonObject line = event("drop");
        line.addProperty("p", slot);
        line.addProperty("item", stack.getType().name().toLowerCase(Locale.ROOT));
        line.addProperty("n", stack.getAmount());
        write(line);
    }

    /** The winner's send-off: every burst over the cake tower, with its colours. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFirework(FireworkExplodeEvent event) {
        if (out == null) {
            return;
        }
        JsonObject line = event("firework");
        position(line, event.getEntity().getLocation());
        JsonArray colours = new JsonArray();
        for (org.bukkit.FireworkEffect effect : event.getEntity().getFireworkMeta().getEffects()) {
            for (org.bukkit.Color colour : effect.getColors()) {
                colours.add(colour.asRGB());
            }
        }
        line.add("colors", colours);
        write(line);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent event) {
        if (out == null) {
            return;
        }
        JsonObject line = event("lightning");
        position(line, event.getLightning().getLocation());
        write(line);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (out == null || !(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        Integer slot = participant(shooter);
        if (slot == null) {
            return;
        }
        track(event.getEntity(), projectileTicks);
        heat(shooter, combatHoldTicks / 2);
        JsonObject line = event("projectile");
        line.addProperty("eid", event.getEntity().getEntityId());
        line.addProperty("kind", event.getEntity().getType().name().toLowerCase(Locale.ROOT));
        line.addProperty("p", slot);
        write(line);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (out == null || event.getTo() == null) {
            return;
        }
        Integer slot = participant(event.getPlayer());
        if (slot == null || event.getFrom().getWorld() != event.getTo().getWorld()
                || event.getFrom().distanceSquared(event.getTo()) < minLoggedTeleport * minLoggedTeleport) {
            return;
        }
        sampleNow(event.getPlayer()); // where they left from, so the film does not glide them across
        // ...and where they arrive, a tick later, so the film moves them at this instant and not
        // half a second on, whenever their next ordinary sample happens to fall
        Player moved = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (out != null && moved.isOnline()) {
                sampleNow(moved);
            }
        });
        heat(event.getPlayer(), 60);
        JsonObject line = event("teleport");
        line.addProperty("p", slot);
        line.addProperty("cause", event.getCause().name().toLowerCase(Locale.ROOT));
        position(line, event.getTo());
        JsonArray from = new JsonArray();
        from.add(round(event.getFrom().getX()));
        from.add(round(event.getFrom().getY()));
        from.add(round(event.getFrom().getZ()));
        line.add("from", from);
        write(line);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (out == null) {
            return;
        }
        Integer slot = participant(event.getPlayer());
        if (slot != null) {
            JsonObject line = event("quit");
            line.addProperty("p", slot);
            write(line);
        }
    }

    /** Off the main thread: only touches the volatile tick and the writer's queue. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (out == null || !recordChat) {
            return;
        }
        if (prefs != null && prefs.isBlocked(event.getPlayer().getUniqueId())) {
            return; // blocked players are not quoted
        }
        JsonObject line = event("chat");
        line.addProperty("name", event.getPlayer().getName());
        line.addProperty("text", PlainTextComponentSerializer.plainText().serialize(event.message()));
        write(line);
    }
}
