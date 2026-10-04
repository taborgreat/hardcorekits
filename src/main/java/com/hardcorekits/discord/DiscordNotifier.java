package com.hardcorekits.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hardcorekits.game.GameConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Posts to a Discord channel through a webhook: once when a match starts counting down, and
 * once when someone wins.
 *
 * <p>The webhook URL is a password for posting into that channel, so it is read from its own
 * file in the data folder rather than config.yml — that file is in git and is overwritten
 * from the jar at every boot. No file, or an empty one, means this stays silent.
 *
 * <p>Every post goes out on the HTTP client's own threads; the server thread never waits on
 * Discord. A failed post is logged and forgotten — a missed announcement is not worth a retry
 * loop.
 */
public final class DiscordNotifier {

    public static final String WEBHOOK_FILE = "discord-webhook.txt";

    private final Logger log;
    private final GameConfig config;
    private final URI webhook;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Set<CompletableFuture<?>> inFlight = ConcurrentHashMap.newKeySet();

    /** One countdown post per match: a lobby that dips below the minimum and refills is the
     *  same match coming together, not news twice. Cleared when the match resets. */
    private boolean countdownAnnounced;

    public DiscordNotifier(Path dataFolder, GameConfig config, Logger log) {
        this.log = log;
        this.config = config;
        this.webhook = readWebhook(dataFolder.resolve(WEBHOOK_FILE));
    }

    private URI readWebhook(Path file) {
        if (!Files.isRegularFile(file)) {
            log.info("Discord: no " + WEBHOOK_FILE + ", match announcements are off.");
            return null;
        }
        String raw;
        try {
            raw = Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            log.warning("Discord: could not read " + WEBHOOK_FILE + ": " + e.getMessage());
            return null;
        }
        if (raw.isEmpty()) {
            log.info("Discord: " + WEBHOOK_FILE + " is empty, match announcements are off.");
            return null;
        }
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException e) {
            log.warning("Discord: " + WEBHOOK_FILE + " does not hold a URL.");
            return null;
        }
        String host = uri.getHost() == null ? "" : uri.getHost();
        boolean discordHost = host.equals("discord.com") || host.endsWith(".discord.com")
                || host.equals("discordapp.com") || host.endsWith(".discordapp.com");
        if (!"https".equals(uri.getScheme()) || !discordHost
                || uri.getPath() == null || !uri.getPath().startsWith("/api/webhooks/")) {
            log.warning("Discord: " + WEBHOOK_FILE + " is not a Discord webhook URL"
                    + " (expected https://discord.com/api/webhooks/...).");
            return null;
        }
        // Never log the token half of the URL: it is the password.
        log.info("Discord: match announcements on.");
        return uri;
    }

    public boolean enabled() {
        return webhook != null;
    }

    public void countdownStarted(int seconds) {
        if (countdownAnnounced) {
            return;
        }
        countdownAnnounced = true;
        post(config.discordCountdownMessage().replace("{time}", spell(seconds)));
    }

    public void matchWon(String playerName) {
        post(config.discordWinMessage().replace("{player}", escapeMarkdown(playerName)));
    }

    public void reset() {
        countdownAnnounced = false;
    }

    /**
     * Gives posts still in flight a moment to land. The plugin shuts the server down a couple
     * of seconds after every match, and the win post is the one most likely to be mid-air.
     */
    public void awaitPending(long timeoutMillis) {
        if (inFlight.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(inFlight.toArray(CompletableFuture[]::new))
                    .get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warning("Discord: a post did not finish before shutdown.");
        }
    }

    private void post(String content) {
        if (webhook == null || content.isBlank()) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("content", content);
        // No @everyone / @here / role or user pings, whatever ends up in a player's name.
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        body.add("allowed_mentions", mentions);

        HttpRequest request = HttpRequest.newBuilder(webhook)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        CompletableFuture<HttpResponse<String>> call =
                http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        inFlight.add(call);
        call.whenComplete((response, error) -> {
            inFlight.remove(call);
            if (error != null) {
                log.warning("Discord: post failed: " + error.getMessage());
            } else if (response.statusCode() >= 300) {
                log.warning("Discord: post rejected with HTTP " + response.statusCode() + ".");
            }
        });
    }

    /** 180 -> "3 minutes", 60 -> "1 minute", 45 -> "45 seconds". */
    static String spell(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            int minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }

    /** Minecraft names can hold underscores, which Discord would read as italics. */
    static String escapeMarkdown(String text) {
        StringBuilder out = new StringBuilder(text.length() + 4);
        for (char c : text.toCharArray()) {
            if ("\\*_~`|>#".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}
