package com.hardcorekits.command;

import com.hardcorekits.movie.MoviePrefs;
import com.hardcorekits.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /movie (also /youtube): a player's say in the match films.
 *
 * <pre>
 * /movie, /movie help        what this is, where you stand, the commands
 * /movie block               toggle: keep your name and skin out of the films
 * /movie voice list          every voice, numbered
 * /movie voice &lt;name|number&gt; choose yours
 * /movie voice random        leave it to chance again
 * </pre>
 *
 * <p>Read-only as far as the match goes: it only writes {@link MoviePrefs}.
 */
public final class MovieCommand implements CommandExecutor, TabCompleter {

    private final MoviePrefs prefs;
    /** Where the films go, e.g. "@hardcorepvpcom". */
    private final String channel;
    /** Marks a moment in the running recording; returns the line to show the player. */
    private java.util.function.BiFunction<Player, String, String> highlighter;

    public void highlighter(java.util.function.BiFunction<Player, String, String> highlighter) {
        this.highlighter = highlighter;
    }

    public MovieCommand(MoviePrefs prefs, String channel) {
        this.prefs = prefs;
        this.channel = channel;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            Msg.error(sender, "Players only.");
            return true;
        }
        // /highlight <why> is /movie highlight <why>
        if (command.getName().equalsIgnoreCase("highlight")) {
            highlight(player, args);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "block" -> {
                boolean blocked = prefs.toggleBlocked(player.getUniqueId(), player.getName());
                if (blocked) {
                    Msg.success(player, "Blocked. Your name and skin stay out of the films.");
                } else {
                    Msg.success(player, "Unblocked. You are back in the films.");
                }
            }
            case "voice" -> voice(player, Arrays.copyOfRange(args, 1, args.length));
            case "highlight" -> highlight(player, Arrays.copyOfRange(args, 1, args.length));
            default -> help(player);
        }
        return true;
    }

    /** "I just did something cool": mark this moment for the film, with a few words on why. */
    private void highlight(Player player, String[] words) {
        if (words.length == 0) {
            player.sendMessage(Component.text("/highlight <what happened and why it was cool>", NamedTextColor.RED));
            Msg.info(player, "Marks this moment for the film. Up to 3 a game.");
            return;
        }
        if (highlighter == null) {
            Msg.error(player, "Highlights are off right now.");
            return;
        }
        Msg.info(player, highlighter.apply(player, String.join(" ", words)));
    }

    private void help(Player player) {
        boolean blocked = prefs.isBlocked(player.getUniqueId());
        MoviePrefs.Voice voice = prefs.byId(String.valueOf(prefs.voice(player.getUniqueId())));
        player.sendMessage(Component.text("Every tournament is made into a YouTube video. " + channel,
                NamedTextColor.RED));
        player.sendMessage(Component.text("Set your settings here, then go watch the movie.",
                NamedTextColor.RED));
        Msg.info(player, blocked ? "You: blocked. Shown as an anonymous tribute."
                : "You: shown, name and skin.");
        Msg.info(player, "Voice: " + (voice == null ? "random" : voice.label()) + ".");
        usage(player);
    }

    private void usage(Player player) {
        line(player, "/movie block", "toggle your name and skin");
        line(player, "/movie voice list", "every voice");
        line(player, "/movie voice <name|number>", "choose yours");
        line(player, "/highlight <why>", "mark a cool moment for the film, 3 a game");
    }

    private void voice(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("help")) {
            player.sendMessage(Component.text("Voices", NamedTextColor.RED));
            List<MoviePrefs.Voice> voices = prefs.voices();
            String mine = prefs.voice(player.getUniqueId());
            for (int i = 0; i < voices.size(); i++) {
                MoviePrefs.Voice voice = voices.get(i);
                boolean chosen = voice.id().equals(mine);
                player.sendMessage(Component.text("  " + (i + 1) + "  ", NamedTextColor.RED)
                        .append(Component.text(voice.label(), chosen ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                        .append(Component.text(chosen ? "  yours" : "", NamedTextColor.GREEN)));
            }
            line(player, "/movie voice <name|number>", "choose. /movie voice random to reset");
            return;
        }
        String query = String.join(" ", args);
        if (query.equalsIgnoreCase("random") || query.equalsIgnoreCase("clear")) {
            prefs.setVoice(player.getUniqueId(), player.getName(), null);
            Msg.success(player, "Voice: random.");
            return;
        }
        MoviePrefs.Voice voice = prefs.find(query);
        if (voice == null) {
            Msg.error(player, "No voice called " + query + ". /movie voice list");
            return;
        }
        prefs.setVoice(player.getUniqueId(), player.getName(), voice.id());
        Msg.success(player, "Voice: " + voice.label() + ".");
    }

    private static void line(CommandSender sender, String usage, String what) {
        sender.sendMessage(Component.text("  " + usage, NamedTextColor.RED)
                .append(Component.text("  " + what, NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, String @NotNull [] args) {
        List<String> options = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("highlight")) {
            return options;
        }
        if (args.length == 1) {
            options.add("help");
            options.add("block");
            options.add("voice");
            options.add("highlight");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("voice")) {
            options.add("list");
            options.add("random");
            for (MoviePrefs.Voice voice : prefs.voices()) {
                options.add(voice.id());
            }
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(typed));
        return options;
    }
}
