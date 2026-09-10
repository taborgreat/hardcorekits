package com.hardcorekits.command;

import com.hardcorekits.game.GameManager;
import com.hardcorekits.staff.StaffManager;
import com.hardcorekits.stats.StatsStore;
import com.hardcorekits.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;

/**
 * The civilian command set: /help, /stats, /kills, /msg, /feast, /game.
 *
 * <p>Everything here is read-only or social; nothing mutates the match. That is what makes
 * the whole set safe to leave open all game long.
 */
public final class PlayerCommands implements CommandExecutor {

    private final GameManager game;
    private final StaffManager staff;

    public PlayerCommands(GameManager game, StaffManager staff) {
        this.game = game;
        this.staff = staff;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "help" -> help(sender);
            case "stats" -> stats(sender, args);
            case "kills" -> kills(sender);
            case "msg" -> whisper(sender, args);
            case "feast" -> feast(sender);
            case "game" -> gameInfo(sender);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- /help

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("Hardcore Games", NamedTextColor.RED));
        for (String line : new String[]{
                "Everyone drops into the wild. Last one standing wins.",
                "Pick a kit in the lobby, it is your one special power.",
                "After the drop everyone is invincible for a while: run, loot, get ready.",
                "Mushroom soup heals. Swamps are where the mushrooms grow.",
                "Later a feast of loot chests appears. /feast tells you where.",
                "Take too long and everyone left is sealed in a box with rising lava."}) {
            sender.sendMessage(Component.text(line, NamedTextColor.GREEN));
        }
        Msg.info(sender, "Commands:");
        for (String[] line : new String[][]{
                {"/kit <name>", "choose a kit before the game starts"},
                {"/kit", "see your current kit"},
                {"/kits [page]", "browse every kit"},
                {"/stats [player]", "lifetime stats, plus this match"},
                {"/kills", "your kills this match"},
                {"/msg <player> <text>", "whisper privately, also /tell and /whisper"},
                {"/feast", "the feast coordinates, once it has appeared"},
                {"/game", "match time and players remaining"}}) {
            commandLine(sender, line[0], line[1]);
        }

        // Staff and admins see their whole toolbox here too; civilians never see this.
        boolean isStaff = !(sender instanceof Player player) || staff.isStaff(player);
        if (isStaff) {
            Msg.info(sender, "Staff:");
            for (String[] line : new String[][]{
                    {"/mod", "toggle mod mode: invisible, flying, outside the game"},
                    {"/mods", "list staff"},
                    {"/mods mod <player>", "make a mod, owner only"},
                    {"/mods trainee <player>", "make a trainee, owner or mod"},
                    {"/mods remove <player>", "dismiss, only roles below your own"},
                    {"/ban <player> <reason>", "swing the hammer, trainees file a proposal"},
                    {"/propose ban <player> <reason>", "file a numbered ban proposal"},
                    {"/propose approve|deny <id>", "rule on a proposal, mods and owner"},
                    {"/pending", "open proposals, and every ban with who and why"}}) {
                commandLine(sender, line[0], line[1]);
            }
        }
        if (sender.hasPermission("hardcoregames.admin")) {
            Msg.info(sender, "Admin:");
            for (String[] line : new String[][]{
                    {"/hg start", "force-start the countdown"},
                    {"/hg quickstart", "skip the countdown, drop now"},
                    {"/hg skipinvuln", "end invincibility now"},
                    {"/hg endgame", "force the End Game now"},
                    {"/hg reset", "reset back to WAITING"},
                    {"/hg state", "print the game state"},
                    {"/hg fake add|kill <n> | clear", "stand-in tributes for testing"}}) {
                commandLine(sender, line[0], line[1]);
            }
        }
    }

    private static void commandLine(CommandSender sender, String usage, String what) {
        sender.sendMessage(Component.text("  " + usage, NamedTextColor.AQUA)
                .append(Component.text("  " + what, NamedTextColor.GRAY)));
    }

    // ---------------------------------------------------------------- /stats and /kills

    private void stats(CommandSender sender, String[] args) {
        String name = args.length > 0 ? args[0]
                : sender instanceof Player player ? player.getName() : null;
        if (name == null) {
            Msg.error(sender, "Usage: /stats <player>");
            return;
        }
        StatsStore.Entry entry = game.stats().byName(name);
        if (entry == null) {
            Msg.error(sender, "No stats for " + name + " yet.");
            return;
        }
        Msg.info(sender, entry.name + ": " + entry.wins + " wins, " + entry.kills
                + " kills, " + entry.games + " games. Streak " + entry.streak
                + ", best " + entry.bestStreak + ".");

        Player online = Bukkit.getPlayerExact(entry.name);
        if (online != null && game.isAlive(online)) {
            Msg.info(sender, "This match: " + game.killsThisMatch(online) + " kills.");
        }
    }

    private void kills(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.error(sender, "Only players have a match to count.");
            return;
        }
        Msg.info(sender, "Kills this match: " + game.killsThisMatch(player));
    }

    // ---------------------------------------------------------------- /msg

    private void whisper(CommandSender sender, String[] args) {
        if (!(sender instanceof Player from)) {
            Msg.error(sender, "The console has /say.");
            return;
        }
        if (args.length < 2) {
            Msg.error(sender, "Usage: /msg <player> <message>");
            return;
        }
        Player to = Bukkit.getPlayerExact(args[0]);
        if (to == null || !to.isOnline()) {
            Msg.error(sender, args[0] + " is not online.");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        from.sendMessage(Component.text("[you -> " + to.getName() + "] ", NamedTextColor.GRAY)
                .append(Component.text(text, NamedTextColor.WHITE)));
        to.sendMessage(Component.text("[" + from.getName() + " -> you] ", NamedTextColor.GRAY)
                .append(Component.text(text, NamedTextColor.WHITE)));
    }

    // ---------------------------------------------------------------- /feast and /game

    private void feast(CommandSender sender) {
        Location site = game.feast().site();
        if (site == null) {
            Msg.info(sender, "The feast has not appeared yet.");
            return;
        }
        Msg.info(sender, "The feast is at " + site.getBlockX() + ", "
                + (site.getBlockY() + 1) + ", " + site.getBlockZ() + ".");
    }

    private void gameInfo(CommandSender sender) {
        if (!game.state().isLive()) {
            int missing = game.config().minPlayers() - game.participantCount();
            Msg.info(sender, "No match running. " + Msg.players(game.participantCount())
                    + " here" + (missing > 0
                            ? ", need " + missing + " more to start." : "."));
            return;
        }
        Msg.info(sender, "Match time: " + Msg.duration((int) game.matchElapsedSeconds())
                + ". Players: " + game.alive().size() + " of "
                + game.startingPlayers() + " remain.");
    }
}
