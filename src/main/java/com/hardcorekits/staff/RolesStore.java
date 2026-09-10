package com.hardcorekits.staff;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Who is staff, and which bans are waiting for a grown-up.
 *
 * <p>Both live in {@code roles.json} in the data folder, so they survive world rotation like
 * the stats do. Roles are keyed by lowercase name rather than UUID on purpose: staff are
 * appointed by name at the console before they have ever logged in, and this server runs a
 * fixed, small staff list — the rename-attack surface is not worth the ceremony.
 *
 * <p>Owners listed in config.yml are grafted on top at load and can never be dismissed from
 * in game; the file only ever holds what was appointed at runtime.
 */
public final class RolesStore {

    /** A ban waiting for someone with the authority to swing. */
    public static final class Proposal {
        /** Stable number staff refer to it by: /propose approve 14. */
        public int id;
        public String target = "";
        public String reason = "";
        public String proposedBy = "";
    }

    private static final class FileShape {
        Map<String, Role> roles;
        List<Proposal> pending;
        int nextId;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final Logger logger;
    private final Map<String, Role> roles = new ConcurrentHashMap<>();
    private final List<Proposal> pending = new ArrayList<>();
    /** Last proposal number handed out. Never reused, so old ids stay unambiguous. */
    private int lastId;
    /** From config — permanent, not written to the file, not dismissable. */
    private final List<String> configOwners = new ArrayList<>();

    public RolesStore(File dataFolder, Logger logger, List<String> owners) {
        this.file = new File(dataFolder, "roles.json");
        this.logger = logger;
        for (String owner : owners) {
            configOwners.add(owner.toLowerCase(Locale.ROOT));
        }
        load();
    }

    // ---------------------------------------------------------------- roles

    /** The player's role, or null for a civilian. */
    public Role roleOf(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (configOwners.contains(key)) {
            return Role.OWNER;
        }
        return roles.get(key);
    }

    public boolean isStaff(String name) {
        return roleOf(name) != null;
    }

    public void appoint(String name, Role role) {
        roles.put(name.toLowerCase(Locale.ROOT), role);
        save();
    }

    /** @return false if they were not appointed staff (config owners cannot be dismissed) */
    public boolean dismiss(String name) {
        boolean removed = roles.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /** Appointed staff plus config owners, for /mods list. */
    public Map<String, Role> all() {
        Map<String, Role> out = new ConcurrentHashMap<>(roles);
        for (String owner : configOwners) {
            out.put(owner, Role.OWNER);
        }
        return out;
    }

    // ---------------------------------------------------------------- pending bans

    /** Files a proposal and returns it, numbered. */
    public synchronized Proposal propose(String target, String reason, String proposedBy) {
        Proposal proposal = new Proposal();
        proposal.id = ++lastId;
        proposal.target = target;
        proposal.reason = reason;
        proposal.proposedBy = proposedBy;
        pending.add(proposal);
        save();
        return proposal;
    }

    public synchronized List<Proposal> pending() {
        return new ArrayList<>(pending);
    }

    /** Takes a proposal off the list by its number. Null if no such proposal. */
    public synchronized Proposal resolve(int id) {
        for (Proposal proposal : pending) {
            if (proposal.id == id) {
                pending.remove(proposal);
                save();
                return proposal;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- persistence

    private void load() {
        if (!file.isFile()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            FileShape shape = GSON.fromJson(reader, FileShape.class);
            if (shape != null && shape.roles != null) {
                roles.putAll(shape.roles);
            }
            if (shape != null && shape.pending != null) {
                pending.addAll(shape.pending);
            }
            if (shape != null) {
                lastId = shape.nextId;
            }
            // Proposals from before numbering (or a zeroed counter) get numbers now.
            for (Proposal proposal : pending) {
                if (proposal.id <= 0) {
                    proposal.id = ++lastId;
                }
                lastId = Math.max(lastId, proposal.id);
            }
        } catch (IOException | RuntimeException e) {
            logger.severe("Could not read roles.json, starting with staff from config only: "
                    + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.toPath().getParent());
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                FileShape shape = new FileShape();
                shape.roles = roles;
                shape.pending = pending;
                shape.nextId = lastId;
                GSON.toJson(shape, writer);
            }
        } catch (IOException e) {
            logger.warning("Could not save roles.json: " + e.getMessage());
        }
    }
}
