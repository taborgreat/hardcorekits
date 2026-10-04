package com.hardcorekits.record;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * The disk half of a match recording: three gzip streams fed from one background thread.
 *
 * <p>The main thread only ever hands over finished strings; compression and file IO happen
 * here, so a slow disk can never cost the game a tick. Streams are sync-flushed on a timer so
 * a crash loses seconds, not the match.
 */
final class RecordingWriter {

    private static final long FLUSH_MILLIS = 5000L;

    private final Logger logger;
    private final ExecutorService io;
    private final Writer tracks;
    private final Writer events;
    private final Writer entities;
    private long lastFlush = System.currentTimeMillis();
    private boolean failed;

    RecordingWriter(File directory, Logger logger) throws IOException {
        this.logger = logger;
        this.tracks = open(new File(directory, "tracks.csv.gz"));
        this.events = open(new File(directory, "events.jsonl.gz"));
        this.entities = open(new File(directory, "entities.csv.gz"));
        this.io = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "hg-match-recorder");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static Writer open(File file) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(new FileOutputStream(file), 8192, true), StandardCharsets.UTF_8), 16384);
    }

    void tracks(String lines) {
        submit(tracks, lines);
    }

    void event(String line) {
        submit(events, line);
    }

    void entities(String lines) {
        submit(entities, lines);
    }

    private void submit(Writer target, String text) {
        if (io.isShutdown()) {
            return;
        }
        io.execute(() -> {
            if (failed) {
                return;
            }
            try {
                target.write(text);
                long now = System.currentTimeMillis();
                if (now - lastFlush > FLUSH_MILLIS) {
                    lastFlush = now;
                    tracks.flush();
                    events.flush();
                    entities.flush();
                }
            } catch (IOException e) {
                failed = true;
                logger.warning("Match recording stopped writing: " + e.getMessage());
            }
        });
    }

    /** Drains the queue and closes the files. Blocks briefly; called once, at match end. */
    void close() {
        io.execute(() -> {
            for (Writer writer : new Writer[] {tracks, events, entities}) {
                try {
                    writer.close();
                } catch (IOException e) {
                    logger.warning("Could not close a recording stream: " + e.getMessage());
                }
            }
        });
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Match recording did not finish flushing in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    boolean failed() {
        return failed;
    }
}
