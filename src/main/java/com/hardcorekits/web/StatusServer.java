package com.hardcorekits.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.Kit;
import com.hardcorekits.stats.StatsStore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * A tiny read-only JSON API for the website, on the JDK's built-in HTTP server.
 *
 * <p>Bound to localhost by default: the Node app on the same machine is the audience, not the
 * internet. Nothing here mutates game state and nothing takes input beyond a player name, so
 * the exposure of a mistake is a stats read.
 *
 * <p>Thread rules, since HTTP handlers run off the main thread: {@code /api/status} is served
 * from a snapshot string rebuilt every couple of seconds by a scheduler task on the main
 * thread — the handler never touches Bukkit. Stats are a concurrent map built for exactly this
 * cross-thread read, and the kit list is frozen at startup because the registry never changes
 * after enable.
 */
public final class StatusServer {

    /** Ticks between status snapshots. Fresh enough for a site that polls every 30s. */
    private static final long SNAPSHOT_TICKS = 40L;

    private static final Gson GSON = new Gson();

    private final HardcoreGames plugin;
    private final GameManager game;

    private HttpServer server;
    private BukkitTask snapshotTask;
    private volatile String statusJson = "{}";
    private String kitsJson = "[]";

    public StatusServer(HardcoreGames plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    public void start(String bind, int port) {
        kitsJson = buildKits();
        snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> statusJson = buildStatus(), 1L, SNAPSHOT_TICKS);

        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        } catch (IOException e) {
            plugin.getLogger().warning("Web API could not bind " + bind + ":" + port
                    + " — the site will have nothing to read: " + e.getMessage());
            return;
        }
        server.createContext("/api/status", exchange -> respond(exchange, statusJson));
        server.createContext("/api/kits", exchange -> respond(exchange, kitsJson));
        server.createContext("/api/stats/top",
                exchange -> respond(exchange, GSON.toJson(game.stats().top(10))));
        server.createContext("/api/stats", this::handleStatsLookup);
        server.setExecutor(null);
        server.start();
        plugin.getLogger().info("Web API listening on " + bind + ":" + port);
    }

    public void stop() {
        if (snapshotTask != null) {
            snapshotTask.cancel();
            snapshotTask = null;
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // ---------------------------------------------------------------- payloads

    /** Runs on the main thread only. */
    private String buildStatus() {
        // Riding the snapshot: everyone online is "seen now", so last-online stays honest
        // without a listener of its own. In-memory; it reaches disk with the next stats save.
        for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
            game.stats().touch(online.getUniqueId(), online.getName());
        }

        JsonObject json = new JsonObject();
        json.addProperty("state", game.state().name());
        json.addProperty("version", Bukkit.getMinecraftVersion());
        json.addProperty("online", Bukkit.getOnlinePlayers().size());
        json.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        json.addProperty("alive", game.alive().size());
        json.addProperty("participants", game.participantCount());
        json.addProperty("minPlayers", game.config().minPlayers());
        json.addProperty("matchElapsedSeconds", game.matchElapsedSeconds());
        json.addProperty("joinable", game.state().isPreGame());
        json.addProperty("totalGames", game.stats().totalMatches());
        JsonArray names = new JsonArray();
        for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        json.add("onlineNames", names);
        return GSON.toJson(json);
    }

    private String buildKits() {
        JsonArray kits = new JsonArray();
        for (Kit kit : plugin.kits().all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", kit.id());
            entry.addProperty("name", kit.displayName());
            entry.addProperty("description", kit.description());
            kits.add(entry);
        }
        return GSON.toJson(kits);
    }

    private void handleStatsLookup(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String name = null;
        if (query != null) {
            for (String pair : query.split("&")) {
                if (pair.startsWith("player=")) {
                    name = URLDecoder.decode(pair.substring("player=".length()),
                            StandardCharsets.UTF_8);
                }
            }
        }
        if (name == null || name.isBlank()) {
            respond(exchange, 400, "{\"error\":\"pass ?player=<name>\"}");
            return;
        }
        StatsStore.Entry entry = game.stats().byName(name);
        if (entry == null) {
            respond(exchange, 404, "{\"error\":\"no such player\"}");
            return;
        }
        respond(exchange, GSON.toJson(entry));
    }

    // ---------------------------------------------------------------- plumbing

    private void respond(HttpExchange exchange, String body) throws IOException {
        respond(exchange, 200, body);
    }

    private void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
