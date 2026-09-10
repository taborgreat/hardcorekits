package com.hardcorekits.staff;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The staff layer over the game.
 *
 * <p>Two ideas, kept strictly apart. A staff member <em>playing</em> is just a player with a
 * red name — the game treats them like anyone else. A staff member in <b>mod mode</b> has
 * stepped outside the game: invisible to every civilian, visible to other staff, flying,
 * untouchable, and absent from the alive set — so five mods watching one player still means
 * that player has won.
 *
 * <p>Mod mode is entered with {@code /mod} only from outside a live run of play: in the
 * lobby, after dying, or when joining a match already in progress. An alive fighter cannot
 * /mod their way out of a losing fight.
 */
public final class StaffManager {

    private final HardcoreGames plugin;
    private final GameManager game;
    private final RolesStore roles;

    /** Who is currently in mod mode. */
    private final Set<java.util.UUID> modMode = ConcurrentHashMap.newKeySet();

    public StaffManager(HardcoreGames plugin, GameManager game, RolesStore roles) {
        this.plugin = plugin;
        this.game = game;
        this.roles = roles;
        // The badge, repainted every couple of seconds so nobody in mod mode forgets it.
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBadges, 40L, 40L);
    }

    public RolesStore roles() {
        return roles;
    }

    /** Server operators are owners outright — /op at the console is how owners are made. */
    public Role roleOf(Player player) {
        if (player.isOp()) {
            return Role.OWNER;
        }
        return roles.roleOf(player.getName());
    }

    public boolean isStaff(Player player) {
        return roleOf(player) != null;
    }

    public boolean isModMode(Player player) {
        return modMode.contains(player.getUniqueId());
    }

    /** Whether this name belongs to staff — for the join gate, which has no Player yet. */
    public boolean isStaffName(String name) {
        if (roles.isStaff(name)) {
            return true;
        }
        // Operators are owners; the ops list is in memory, so this is safe off-thread too.
        for (org.bukkit.OfflinePlayer op : Bukkit.getOperators()) {
            if (name.equalsIgnoreCase(op.getName())) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- joining

    /**
     * Runs for every join, staff or not. Staff get the red name, and forced mod mode when
     * arriving into a running match they are not a participant of; everyone gets the
     * visibility pass, so a newcomer never sees a mod who is supposed to be invisible.
     */
    public void handleJoin(Player player) {
        if (isStaff(player)) {
            paintName(player);
            if (game.state().isLive() && !game.isAlive(player)) {
                // A mod joining mid-game was never a participant; the only honest shape
                // for them to take is the invisible one.
                enterModMode(player);
            }
        }
        applyVisibility();
    }

    public void handleQuit(Player player) {
        modMode.remove(player.getUniqueId());
    }

    /**
     * Reset hook: the match is over, so mod mode is over. Runs before the lobby pass that
     * normalises everyone's gamemode, and leaves staff as ordinary red-named players who can
     * play the next game — or /mod again.
     */
    public void clearModMode() {
        for (java.util.UUID uuid : new java.util.ArrayList<>(modMode)) {
            Player mod = Bukkit.getPlayer(uuid);
            if (mod != null && mod.isOnline()) {
                exitModMode(mod);
            } else {
                modMode.remove(uuid);
            }
        }
        applyVisibility();
    }

    /** Purple for the owner, pink for mods, red for trainees — rank readable at a glance. */
    private void paintName(Player player) {
        Role role = roleOf(player);
        if (role == null) {
            return;
        }
        NamedTextColor color = switch (role) {
            case OWNER -> NamedTextColor.DARK_PURPLE;
            case MOD -> NamedTextColor.LIGHT_PURPLE;
            case TRAINEE -> NamedTextColor.RED;
        };
        Component painted = Component.text(player.getName(), color);
        player.displayName(painted);
        player.playerListName(painted);
    }

    // ---------------------------------------------------------------- mod mode

    /** @return an error to show, or null on success */
    public Component enterModMode(Player player) {
        if (!isStaff(player)) {
            return Component.text("You are not staff.", NamedTextColor.RED);
        }
        if (!modMode.add(player.getUniqueId())) {
            return Component.text("Already in mod mode.", NamedTextColor.RED);
        }

        // Running /mod while alive in a live match is instant death: eliminated on the
        // spot, loot dropped where they stood. The game moves on without them.
        if (game.state().isLive() && game.isAlive(player)) {
            game.retireToModMode(player);
        }

        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        applyVisibility();
        player.sendMessage(Component.text("[MOD MODE] ", NamedTextColor.RED)
                .append(Component.text("You are invisible to players and outside the game.",
                        NamedTextColor.GRAY)));
        return null;
    }

    /** @return an error to show, or null on success */
    public Component exitModMode(Player player) {
        if (!modMode.contains(player.getUniqueId())) {
            return Component.text("You are not in mod mode.", NamedTextColor.RED);
        }
        // Mod mode is a one-way door while a match runs: whether they died, joined late,
        // or retired themselves, there is no walking back into the game mid-match.
        if (game.state().isLive()) {
            return Component.text("Mod mode lasts until this game ends.", NamedTextColor.RED);
        }
        modMode.remove(player.getUniqueId());
        player.setInvulnerable(false);
        player.setFlying(false);
        player.setAllowFlight(false);
        applyVisibility();
        player.sendMessage(Component.text("Mod mode off.", NamedTextColor.GRAY));
        return null;
    }

    /**
     * Recomputes who can see whom. Civilians never see a mod-mode mod; staff always see
     * everyone — mods can watch each other work.
     */
    public void applyVisibility() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean viewerIsStaff = isStaff(viewer);
            for (Player subject : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(subject)) {
                    continue;
                }
                if (isModMode(subject) && !viewerIsStaff) {
                    viewer.hidePlayer(plugin, subject);
                } else {
                    viewer.showPlayer(plugin, subject);
                }
            }
        }
    }

    private void tickBadges() {
        for (java.util.UUID uuid : modMode) {
            Player mod = Bukkit.getPlayer(uuid);
            if (mod != null && mod.isOnline()) {
                mod.sendActionBar(Component.text("[MOD MODE]", NamedTextColor.RED));
            }
        }
    }

    // ---------------------------------------------------------------- the hammer

    /** Bans outright, on whatever authority the caller already checked. */
    public void ban(String target, String reason, String by) {
        Bukkit.getBanList(BanList.Type.PROFILE).addBan(target,
                "The ban hammer has spoken.\n" + reason, null, by);
        Player online = Bukkit.getPlayerExact(target);
        if (online != null) {
            online.kick(Component.text("The ban hammer has spoken.", NamedTextColor.DARK_RED)
                    .append(Component.newline())
                    .append(Component.text(reason, NamedTextColor.GRAY)));
        }
        notifyStaff(target + " was banned by " + by + ": " + reason);
    }

    /** A line every online staff member sees, whatever mode they are in. */
    public void notifyStaff(String message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isStaff(online)) {
                online.sendMessage(Component.text("[STAFF] ", NamedTextColor.RED)
                        .append(Component.text(message, NamedTextColor.GRAY)));
            }
        }
    }
}
