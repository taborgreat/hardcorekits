package com.hardcorekits.command;

import com.hardcorekits.staff.Role;
import com.hardcorekits.staff.RolesStore;
import com.hardcorekits.staff.StaffManager;
import com.hardcorekits.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /mod, /mods, /ban, /pending and /propose — the staff control surface.
 *
 * <p>Authority is checked here, once, against the roles store: the console counts as the
 * owner, appointed owners appoint, mods swing the hammer, and every proposal carries a
 * number the verdict refers to.
 */
public final class StaffCommand implements CommandExecutor {

    private final StaffManager staff;

    public StaffCommand(StaffManager staff) {
        this.staff = staff;
    }

    /** The caller's role: console is the owner, players are whatever they were appointed. */
    private Role roleOf(CommandSender sender) {
        if (sender instanceof Player player) {
            return staff.roleOf(player);
        }
        return Role.OWNER;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        Role role = roleOf(sender);
        if (role == null) {
            return true; // civilians see nothing, not even a refusal worth reading
        }

        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "mod" -> handleModMode(sender);
            case "mods" -> handleRoster(sender, role, args);
            case "ban" -> handleBan(sender, role, args);
            case "pending" -> handlePending(sender);
            case "propose" -> handlePropose(sender, role, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- /mod

    private void handleModMode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.error(sender, "Mod mode is for players.");
            return;
        }
        Component error = staff.isModMode(player)
                ? staff.exitModMode(player)
                : staff.enterModMode(player);
        if (error != null) {
            player.sendMessage(error);
        }
    }

    // ---------------------------------------------------------------- /mods

    private void handleRoster(CommandSender sender, Role role, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            Msg.info(sender, "Staff:");
            // Operators are owners without ever being appointed; show them so the list is
            // the whole truth.
            for (org.bukkit.OfflinePlayer op : Bukkit.getOperators()) {
                if (op.getName() != null) {
                    Msg.info(sender, "  " + op.getName() + ": owner");
                }
            }
            for (Map.Entry<String, Role> entry : staff.roles().all().entrySet()) {
                Msg.info(sender, "  " + entry.getKey() + ": "
                        + entry.getValue().name().toLowerCase(Locale.ROOT));
            }
            return;
        }

        // Appointing: /mods mod <player> and /mods trainee <player>. Owners are never made
        // here — /op at the console is the only door to owner.
        Role appointed = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "mod" -> Role.MOD;
            case "trainee", "training", "mit" -> Role.TRAINEE;
            default -> null;
        };
        if (appointed != null && args.length >= 2) {
            if (!role.canManage(appointed)) {
                Msg.error(sender, "You cannot appoint a "
                        + appointed.name().toLowerCase(Locale.ROOT) + ".");
                return;
            }
            Role current = staff.roles().roleOf(args[1]);
            if (current != null && !role.canManage(current)) {
                Msg.error(sender, args[1] + " is a " + current.name().toLowerCase(Locale.ROOT)
                        + ", which is not yours to change.");
                return;
            }
            staff.roles().appoint(args[1], appointed);
            Msg.success(sender, args[1] + " is now "
                    + appointed.name().toLowerCase(Locale.ROOT) + ".");
            staff.notifyStaff(sender.getName() + " made " + args[1] + " "
                    + appointed.name().toLowerCase(Locale.ROOT) + ".");
            staff.applyVisibility();
            return;
        }

        if (args[0].equalsIgnoreCase("remove") && args.length >= 2) {
            Role current = staff.roles().roleOf(args[1]);
            if (current == null) {
                Msg.error(sender, args[1] + " holds no appointed role. "
                        + "Owners are ops, deop them at the console.");
                return;
            }
            if (!role.canManage(current)) {
                Msg.error(sender, args[1] + " is a " + current.name().toLowerCase(Locale.ROOT)
                        + ", which is not yours to change.");
                return;
            }
            staff.roles().dismiss(args[1]);
            Msg.success(sender, args[1] + " is no longer staff.");
            staff.notifyStaff(sender.getName() + " removed " + args[1] + " from staff.");
            return;
        }
        Msg.error(sender, "Usage: /mods | mod <player> | trainee <player> | remove <player>");
    }

    // ---------------------------------------------------------------- /ban

    private void handleBan(CommandSender sender, Role role, String[] args) {
        if (args.length < 2) {
            Msg.error(sender, "Usage: /ban <player> <reason...>");
            return;
        }
        String target = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (role.canBan()) {
            staff.ban(target, reason, sender.getName());
            return;
        }
        // A trainee's swing lands in the docket, not on the player.
        file(sender, target, reason);
    }

    // ---------------------------------------------------------------- /propose

    private void handlePropose(CommandSender sender, Role role, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("ban")) {
            file(sender, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            return;
        }

        if (args.length >= 2
                && (args[0].equalsIgnoreCase("approve") || args[0].equalsIgnoreCase("deny"))) {
            if (!role.canBan()) {
                Msg.error(sender, "Only mods and the owner rule on proposals.");
                return;
            }
            int id;
            try {
                id = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Msg.error(sender, "That is not a proposal number. /pending lists them.");
                return;
            }
            RolesStore.Proposal proposal = staff.roles().resolve(id);
            if (proposal == null) {
                Msg.error(sender, "No pending proposal " + id + ". /pending lists them.");
                return;
            }
            if (args[0].equalsIgnoreCase("approve")) {
                staff.ban(proposal.target, proposal.reason
                        + ", proposed by " + proposal.proposedBy, sender.getName());
            } else {
                Msg.success(sender, "Denied proposal " + id + " against "
                        + proposal.target + ".");
                staff.notifyStaff(sender.getName() + " denied proposal " + id + " against "
                        + proposal.target + ".");
            }
            return;
        }

        Msg.error(sender, "Usage: /propose ban <player> <reason...> | approve <id> | deny <id>");
    }

    /** Files a numbered proposal, tells the filer its number, and tells the rest of staff. */
    private void file(CommandSender sender, String target, String reason) {
        RolesStore.Proposal proposal = staff.roles().propose(target, reason, sender.getName());
        Msg.success(sender, "Filed proposal " + proposal.id + " against " + target
                + ". A mod or the owner rules on it with /propose approve|deny "
                + proposal.id + ".");
        staff.notifyStaff(proposal.id + ": " + sender.getName() + " proposes ban "
                + target + " reason: " + reason);
    }

    // ---------------------------------------------------------------- /pending

    /** The docket and the record: open proposals first, then every ban already sworn. */
    private void handlePending(CommandSender sender) {
        List<RolesStore.Proposal> docket = staff.roles().pending();
        if (docket.isEmpty()) {
            Msg.info(sender, "No pending ban proposals.");
        } else {
            Msg.info(sender, "Pending proposals:");
            for (RolesStore.Proposal proposal : docket) {
                Msg.info(sender, "  " + proposal.id + ": " + proposal.proposedBy
                        + " proposes ban " + proposal.target + " reason: " + proposal.reason);
            }
            Msg.info(sender, "Rule with /propose approve <id> or /propose deny <id>.");
        }

        var entries = Bukkit.getBanList(BanList.Type.PROFILE).getEntries();
        if (entries.isEmpty()) {
            Msg.info(sender, "The ban hammer has not spoken yet.");
            return;
        }
        Msg.info(sender, "Bans:");
        for (BanEntry<?> entry : entries) {
            Object target = entry.getBanTarget();
            String name = target instanceof PlayerProfile profile && profile.getName() != null
                    ? profile.getName()
                    : String.valueOf(target);
            Msg.info(sender, "  " + (entry.getSource() == null ? "console" : entry.getSource())
                    + " banned " + name + " reason: " + displayReason(entry.getReason()));
        }
    }

    /** The stored reason leads with the ban-screen tagline; the ledger reads better bare. */
    private static String displayReason(String stored) {
        if (stored == null) {
            return "no reason given";
        }
        String tagline = "The ban hammer has spoken.\n";
        return stored.startsWith(tagline) ? stored.substring(tagline.length()) : stored;
    }
}
