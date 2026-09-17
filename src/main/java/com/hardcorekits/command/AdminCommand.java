package com.hardcorekits.command;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.game.GameState;
import com.hardcorekits.util.Msg;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Backs /hg, the testing loop's whole control surface behind one command.
 *
 * <p>Every reply is purple and goes to the sender alone. The permission on the command
 * (hardcoregames.admin) keeps it out of civilians' tab completion, and CommandGuard
 * refuses it outright if one is typed anyway.
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "state", "start", "hold", "release", "quickstart", "skipinvuln", "endgame", "reset",
            "fake");

    private static final String USAGE = "Usage: /hg state | start [minutes] | hold | release"
            + " | quickstart | skipinvuln | endgame | reset | fake";

    private final HardcoreGames plugin;
    private final GameManager game;

    public AdminCommand(HardcoreGames plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("announce")) {
            announce(sender, args);
            return true;
        }
        if (args.length == 0) {
            Msg.admin(sender, USAGE);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "state" -> {
                Msg.admin(sender, "State: " + game.state() + " | alive: " + game.alive().size());
                Msg.admin(sender, "Players: " + game.participantCount() + "/"
                        + game.config().minPlayers() + " needed to start"
                        + (game.lobbyHeld() ? "  [HELD BY HOST]" : "")
                        + (game.lobbyWatcherRunning() ? "" : "  [LOBBY WATCHER STOPPED]"));
                Msg.admin(sender, "Chunks shaped: " + plugin.worldShaper().chunksShaped()
                        + " | diamond ore stripped: " + plugin.worldShaper().diamondsStripped()
                        + " | mushrooms planted: " + plugin.worldShaper().mushroomsPlanted()
                        + " | pending: " + plugin.worldShaper().pending());
                Location feastSite = game.feast().site();
                Msg.admin(sender, "Feast site: " + (feastSite == null
                        ? "not sited yet"
                        : feastSite.getBlockX() + ", " + feastSite.getBlockZ()));
                Msg.admin(sender, "Fake tributes: " + game.fakeCount());
                Msg.admin(sender, "World time: " + game.config().world().getTime()
                        + ". 6000 is midday, held before the game.");
            }
            case "fake" -> handleFake(sender, Arrays.copyOfRange(args, 1, args.length));
            case "quickstart" -> {
                if (game.quickStart()) {
                    Msg.admin(sender, "Skipping the countdown. Dropping now.");
                } else {
                    Msg.admin(sender, "A match is already underway: " + game.state() + ".");
                }
            }
            case "skipinvuln" -> {
                if (game.skipInvulnerability()) {
                    Msg.admin(sender, "Invincibility ended. PvP is live.");
                } else {
                    Msg.admin(sender, "No grace period running: " + game.state() + ".");
                }
            }
            case "start" -> {
                if (game.state() != GameState.WAITING) {
                    Msg.admin(sender, "Can only start from WAITING, currently "
                            + game.state() + ". Use /hg reset to return to WAITING.");
                    return true;
                }
                // Five minutes unless told otherwise, and exactly that: a host's countdown is
                // not cut short by a full lobby.
                int minutes = args.length > 1 ? parseCount(args, 1) : 5;
                if (minutes < 1) {
                    Msg.admin(sender, "Usage: /hg start [minutes]");
                    return true;
                }
                Msg.admin(sender, "Starting a " + minutes + " minute countdown.");
                game.startCountdown(minutes * 60);
            }
            case "hold" -> {
                game.holdLobby();
                Msg.admin(sender, "Lobby held. Nothing starts until /hg release or /hg start.");
            }
            case "release" -> {
                game.releaseLobby();
                Msg.admin(sender, "Lobby released. It starts itself again when full enough.");
            }
            case "endgame" -> {
                if (!game.endgame().forceBegin()) {
                    Msg.admin(sender, game.endgame().hasBegun()
                            ? "The End Game is already running."
                            : "Only during a live match, currently " + game.state() + ".");
                    return true;
                }
                Msg.admin(sender, "Forcing the End Game.");
            }
            case "reset" -> {
                Msg.admin(sender, "Resetting the game.");
                game.reset();
            }
            default -> Msg.admin(sender, "Unknown: /hg " + args[0] + ". " + USAGE);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, String @NotNull [] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    options.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("fake")) {
            for (String sub : List.of("add", "kill", "clear")) {
                if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    options.add(sub);
                }
            }
        }
        return options;
    }

    /** A line from the host to everyone, in the timer red, exactly as typed. */
    private void announce(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Msg.admin(sender, "Usage: /announce <message>");
            return;
        }
        Msg.timer(String.join(" ", args));
    }

    /**
     * Stand-in tributes for solo testing.
     *
     * <p>These are counters, not entities — they satisfy the player threshold, the remaining
     * count and the win condition, but they have no body, so they cannot be fought or tracked
     * with a compass. Testing combat still needs a second real client.
     */
    private void handleFake(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Msg.admin(sender, "Fake tributes: " + game.fakeCount()
                    + ". Usage: /hg fake add <n> | kill <n> | clear");
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                int count = parseCount(args, 1);
                if (count < 1) {
                    Msg.admin(sender, "Usage: /hg fake add <n>");
                    return;
                }
                Msg.admin(sender, "Added " + count + " fake tributes, now "
                        + game.addFakes(count) + ".");
            }
            case "kill" -> {
                int count = parseCount(args, 1);
                int killed = game.killFakes(Math.max(1, count));
                Msg.admin(sender, "Eliminated " + killed + " fake tributes.");
            }
            case "clear" -> {
                game.clearFakes();
                Msg.admin(sender, "Cleared all fake tributes.");
            }
            default -> Msg.admin(sender, "Usage: /hg fake add <n> | kill <n> | clear");
        }
    }

    /** Defaults to 1 so "/hg fake kill" works without an argument. */
    private int parseCount(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
