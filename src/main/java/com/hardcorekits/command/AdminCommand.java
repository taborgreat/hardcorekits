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

/** Backs /hg — the testing loop's whole control surface behind one command. */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "state", "start", "quickstart", "skipinvuln", "endgame", "reset", "fake");

    private final HardcoreGames plugin;
    private final GameManager game;

    public AdminCommand(HardcoreGames plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (args.length == 0) {
            Msg.info(sender, "Usage: /hg state | start | quickstart | skipinvuln | endgame"
                    + " | reset | fake");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "state" -> {
                Msg.info(sender, "State: " + game.state() + " | alive: " + game.alive().size());
                Msg.info(sender, "Players: " + game.participantCount() + "/"
                        + game.config().minPlayers() + " needed to start"
                        + (game.lobbyWatcherRunning() ? "" : "  [LOBBY WATCHER STOPPED]"));
                Msg.info(sender, "Chunks shaped: " + plugin.worldShaper().chunksShaped()
                        + " | diamond ore stripped: " + plugin.worldShaper().diamondsStripped()
                        + " | mushrooms planted: " + plugin.worldShaper().mushroomsPlanted()
                        + " | pending: " + plugin.worldShaper().pending());
                Location feastSite = game.feast().site();
                Msg.info(sender, "Feast site: " + (feastSite == null
                        ? "not sited yet"
                        : feastSite.getBlockX() + ", " + feastSite.getBlockZ()));
                Msg.info(sender, "Fake tributes: " + game.fakeCount());
                Msg.info(sender, "World time: " + game.config().world().getTime()
                        + ". 6000 is midday, held during pre-game.");
            }
            case "fake" -> handleFake(sender, Arrays.copyOfRange(args, 1, args.length));
            case "quickstart" -> {
                if (game.quickStart()) {
                    Msg.success(sender, "Skipping the countdown — dropping now.");
                } else {
                    Msg.error(sender, "A match is already underway: " + game.state() + ".");
                }
            }
            case "skipinvuln" -> {
                if (game.skipInvulnerability()) {
                    Msg.success(sender, "Invincibility ended. PvP is live.");
                } else {
                    Msg.error(sender, "No grace period running: " + game.state() + ".");
                }
            }
            case "start" -> {
                if (game.state() != GameState.WAITING) {
                    Msg.error(sender, "Can only force-start from WAITING, currently "
                            + game.state() + ". Use /hg reset to return to WAITING.");
                    return true;
                }
                Msg.success(sender, "Force-starting the countdown.");
                game.startCountdown();
            }
            case "endgame" -> {
                if (!game.endgame().forceBegin()) {
                    Msg.error(sender, game.endgame().hasBegun()
                            ? "The End Game is already running."
                            : "Only during a live match, currently " + game.state() + ".");
                    return true;
                }
                Msg.success(sender, "Forcing the End Game.");
            }
            case "reset" -> {
                Msg.success(sender, "Resetting the game.");
                game.reset();
            }
            default -> Msg.error(sender, "Unknown: /hg " + args[0] + ". Usage: /hg state"
                    + " | start | quickstart | skipinvuln | endgame | reset | fake");
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

    /**
     * Stand-in tributes for solo testing.
     *
     * <p>These are counters, not entities — they satisfy the player threshold, the remaining
     * count and the win condition, but they have no body, so they cannot be fought or tracked
     * with a compass. Testing combat still needs a second real client.
     */
    private void handleFake(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Msg.info(sender, "Fake tributes: " + game.fakeCount()
                    + ". Usage: /hg fake add <n> | kill <n> | clear");
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                int count = parseCount(args, 1);
                if (count < 1) {
                    Msg.error(sender, "Usage: /hg fake add <n>");
                    return;
                }
                Msg.success(sender, "Added " + count + " fake tributes, now "
                        + game.addFakes(count) + ".");
            }
            case "kill" -> {
                int count = parseCount(args, 1);
                int killed = game.killFakes(Math.max(1, count));
                Msg.success(sender, "Eliminated " + killed + " fake tributes.");
            }
            case "clear" -> {
                game.clearFakes();
                Msg.success(sender, "Cleared all fake tributes.");
            }
            default -> Msg.error(sender, "Usage: /hg fake add <n> | kill <n> | clear");
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
