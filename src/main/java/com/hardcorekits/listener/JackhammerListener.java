package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.JackhammerKit;
import com.hardcorekits.util.CooldownBar;
import com.hardcorekits.util.Phases;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Jackhammer's column.
 *
 * <p>The column always runs upwards: break a block and everything above it goes too, to the sky
 * limit. Where the player is looking has nothing to do with it — the block they broke is the
 * whole aim. Nothing else about mining changes.
 *
 * <p>The column is animated rather than instant — one block per interval — because that is what
 * makes it a threat you can watch coming and, if you are the one standing on the block, just
 * about outrun.
 */
public final class JackhammerListener implements Listener {

    /** The column stops dead at these. Everything else in the way is fair game. */
    private static final Set<Material> UNBREAKABLE =
            EnumSet.of(Material.BEDROCK, Material.BARRIER, Material.END_PORTAL_FRAME);

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Swings spent this cycle. */
    private final Map<UUID, Integer> spent = new HashMap<>();
    /** Hammers currently cooling. */
    private final Set<UUID> cooling = new HashSet<>();

    public JackhammerListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Wiped between matches, like every other per-match kit state. */
    public void clearCooldowns() {
        spent.clear();
        cooling.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(player, JackhammerKit.ID)) {
            return;
        }
        if (player.getInventory().getItemInMainHand().getType() != JackhammerKit.HAMMER) {
            return; // the ability is the hammer's, not the player's
        }

        UUID uuid = player.getUniqueId();
        if (cooling.contains(uuid)) {
            player.sendMessage(Component.text("Your hammer is still cooling.", NamedTextColor.GRAY));
            return;
        }

        drill(player, event.getBlock());
        spendSwing(player);
    }

    /**
     * Eats the column one block per interval.
     *
     * <p>Air and liquid are stepped over for free — only a real block costs a tick — so a
     * column that starts under open sky does not spend a minute chewing on nothing.
     */
    private void drill(Player player, Block origin) {
        World world = origin.getWorld();
        int x = origin.getX();
        int z = origin.getZ();
        int limit = world.getMaxHeight() - 1;
        boolean drops = game.config().jackhammerDropsBlocks();
        // Snapshot: the hammer may be dropped or destroyed while the column is still running.
        ItemStack tool = player.getInventory().getItemInMainHand().clone();
        long interval = game.config().jackhammerIntervalTicks();

        new BukkitRunnable() {
            private int y = origin.getY();

            @Override
            public void run() {
                if (!game.state().isLive()) {
                    cancel();
                    return;
                }
                while (true) {
                    y++;
                    if (y > limit) {
                        cancel();
                        return;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir() || block.isLiquid()) {
                        continue;
                    }
                    if (UNBREAKABLE.contains(block.getType())) {
                        cancel();
                        return;
                    }
                    if (drops) {
                        block.breakNaturally(tool, true);
                    } else {
                        block.setType(Material.AIR);
                    }
                    return;
                }
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    /** Counts the swing, and starts the cooldown once they are all spent. */
    private void spendSwing(Player player) {
        UUID uuid = player.getUniqueId();
        int allowance = game.config().jackhammerUses();
        int used = spent.merge(uuid, 1, Integer::sum);

        if (used < allowance) {
            player.sendMessage(Component.text("Hammer: " + (allowance - used) + " left.",
                    NamedTextColor.GRAY));
            return;
        }

        int cooldown = game.config().jackhammerCooldownSeconds();
        spent.remove(uuid);
        cooling.add(uuid);
        player.sendMessage(Component.text("Your hammer is spent. " + cooldown + "s to cool.",
                NamedTextColor.GRAY));
        // The rest, drawn draining on the XP bar. The fill only — the level stays kills.
        CooldownBar.show(plugin, game, player, cooldown);

        Phases.delayed(plugin, cooldown, () -> {
            cooling.remove(uuid);
            Player rested = Bukkit.getPlayer(uuid);
            if (rested != null && rested.isOnline()) {
                rested.sendMessage(Component.text("Your hammer has cooled.", NamedTextColor.GRAY));
            }
        });
    }
}
