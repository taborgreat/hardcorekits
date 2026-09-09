package gg.hungergames.command;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.game.GameState;
import gg.hungergames.util.Msg;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Backs /hgstart, /hgendgame, /hgreset and /hgstate: the testing loop's control surface. */
public final class AdminCommand implements CommandExecutor {

    private final HungerGames plugin;
    private final GameManager game;

    public AdminCommand(HungerGames plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "hgstate" -> {
                Msg.info(sender, "State: " + game.state() + " | alive: " + game.alive().size());
                Msg.info(sender, "Players: " + game.participantCount() + "/"
                        + game.config().minPlayers() + " needed to start"
                        + (game.lobbyWatcherRunning() ? "" : "  [LOBBY WATCHER STOPPED]"));
                Msg.info(sender, "Chunks shaped: " + plugin.worldShaper().chunksShaped()
                        + " | diamond ore stripped: " + plugin.worldShaper().diamondsStripped()
                        + " | pending: " + plugin.worldShaper().pending());
                Location feastSite = game.feast().site();
                Msg.info(sender, "Feast site: " + (feastSite == null
                        ? "not sited yet"
                        : feastSite.getBlockX() + ", " + feastSite.getBlockZ()));
                Msg.info(sender, "Fake tributes: " + game.fakeCount());
                Msg.info(sender, "World time: " + game.config().world().getTime()
                        + " (6000 = midday, held during pre-game)");
            }
            case "hgfake" -> handleFake(sender, args);
            case "hgstart" -> {
                if (game.state() != GameState.WAITING) {
                    Msg.error(sender, "Can only force-start from WAITING (currently "
                            + game.state() + "). Use /hgreset to return to WAITING.");
                    return true;
                }
                Msg.success(sender, "Force-starting the countdown.");
                game.startCountdown();
            }
            case "hgendgame" -> {
                if (!game.endgame().forceBegin()) {
                    Msg.error(sender, game.endgame().hasBegun()
                            ? "The End Game is already running."
                            : "Only during a live match (currently " + game.state() + ").");
                    return true;
                }
                Msg.success(sender, "Forcing the End Game.");
            }
            case "hgreset" -> {
                Msg.success(sender, "Resetting the game.");
                game.reset();
            }
            default -> {
                return false;
            }
        }
        return true;
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
                    + ". Usage: /hgfake add <n> | kill <n> | clear");
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                int count = parseCount(args, 1);
                if (count < 1) {
                    Msg.error(sender, "Usage: /hgfake add <n>");
                    return;
                }
                Msg.success(sender, "Added " + count + " fake tributes (now "
                        + game.addFakes(count) + ").");
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
            default -> Msg.error(sender, "Usage: /hgfake add <n> | kill <n> | clear");
        }
    }

    /** Defaults to 1 so "/hgfake kill" works without an argument. */
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
