package com.hardcorekits.command;

import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.Kit;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /kit &lt;name&gt; and /kits — the only way to browse kits, since there is no GUI. */
public final class KitCommand implements CommandExecutor, TabCompleter {

    private final GameManager game;
    private final KitRegistry kits;

    public KitCommand(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {

        if (command.getName().equalsIgnoreCase("kits")) {
            int page = args.length > 0 ? parsePage(args[0]) : 1;
            listKits(sender, page);
            return true;
        }

        if (!(sender instanceof Player player)) {
            Msg.error(sender, "Only players can select a kit.");
            return true;
        }
        boolean preGame = game.state().isPreGame();

        // Bare /kit is a question, not an attempt to switch, so it is answered before the lock:
        // mid-match it is the only way to remember what you are holding and what it does.
        if (args.length == 0) {
            describeOwnKit(player, preGame);
            return true;
        }

        // Kits lock at the countdown for everyone — except admins, who need to be able to
        // swap mid-match to test one without restarting the game.
        boolean midMatchOverride = !preGame;
        if (midMatchOverride && !player.hasPermission("hardcoregames.admin")) {
            Msg.error(player, "Kits are locked once the match starts.");
            return true;
        }
        if (args.length != 1) {
            Msg.error(player, "Usage: /kit name. See /kits.");
            return true;
        }

        Kit kit = kits.byId(args[0]);
        if (kit == null) {
            Msg.error(player, "No such kit: " + args[0] + ". See /kits.");
            return true;
        }

        kits.select(player.getUniqueId(), kit);
        player.sendMessage(Component.text("You have chosen the ", NamedTextColor.GREEN)
                .append(Component.text(kit.displayName(), NamedTextColor.AQUA))
                .append(Component.text(" kit.", NamedTextColor.GREEN)));

        if (midMatchOverride) {
            // Mid-match there is no start to hand the gear out at, so do it now. The old kit's
            // items are left alone rather than wiping an inventory mid-test.
            kit.apply(player);
            player.sendMessage(Component.text("Switched mid-match as admin. Kit items given.",
                    NamedTextColor.GRAY));
        }
        return true;
    }

    /**
     * What am I playing, and what does it do.
     *
     * <p>Picking nothing is a real choice here, so "None" is reported as a kit rather than as a
     * mistake — with the reminder to choose only while there is still time to.
     */
    private void describeOwnKit(Player player, boolean preGame) {
        Kit kit = kits.selectedFor(player.getUniqueId());

        if (kit == null) {
            player.sendMessage(Component.text("You are playing as ", NamedTextColor.GREEN)
                    .append(Component.text("None", NamedTextColor.AQUA))
                    .append(Component.text(".", NamedTextColor.GREEN)));
            player.sendMessage(Component.text("  No kit, no ability, just the compass.",
                    NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("You are playing as ", NamedTextColor.GREEN)
                    .append(Component.text(kit.displayName(), NamedTextColor.AQUA))
                    .append(Component.text(".", NamedTextColor.GREEN)));
            player.sendMessage(Component.text("  " + kit.description(), NamedTextColor.GRAY));
        }

        if (preGame) {
            player.sendMessage(Component.text("Use /kit name to change it. See /kits.",
                    NamedTextColor.GRAY));
        }
    }

    /** Kits per page of {@code /kits}. There are far more than fit one screen. */
    private static final int KITS_PER_PAGE = 8;

    private void listKits(CommandSender sender, int page) {
        List<Kit> all = new ArrayList<>(kits.all());
        int pages = Math.max(1, (all.size() + KITS_PER_PAGE - 1) / KITS_PER_PAGE);
        page = Math.max(1, Math.min(page, pages));

        sender.sendMessage(Component.text("Kits, page " + page + " of " + pages + ".",
                NamedTextColor.RED));
        int from = (page - 1) * KITS_PER_PAGE;
        int to = Math.min(from + KITS_PER_PAGE, all.size());
        for (Kit kit : all.subList(from, to)) {
            sender.sendMessage(Component.text("  " + kit.id(), NamedTextColor.GREEN)
                    .append(Component.text(": " + clean(kit.description()), NamedTextColor.GRAY)));
        }
        if (pages > 1) {
            sender.sendMessage(Component.text("Type /kits "
                    + (page < pages ? page + 1 : 1) + " for more.", NamedTextColor.RED));
        }
    }

    /** No dashes in the menu: a sentence break becomes a period, a stray dash a comma. */
    private static String clean(String description) {
        return description.replace(" — ", ". ").replace("—", ", ")
                .replace(" - ", ". ").replace("-", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static int parsePage(String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("kit") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Kit kit : kits.all()) {
            if (kit.id().startsWith(prefix)) {
                matches.add(kit.id());
            }
        }
        return matches;
    }
}
