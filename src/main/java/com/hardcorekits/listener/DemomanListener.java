package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.DemomanKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Demoman's mines.
 *
 * <p>Arming is a registry entry, not a block change: the world still contains plain gravel with
 * a plain pressure plate on top, so a trap is indistinguishable from scenery until it goes off.
 * That also means only plates a Demoman placed are live — anyone else gets vanilla behaviour.
 */
public final class DemomanListener implements Listener {

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Pressure-plate location -> whoever armed it. */
    private final Map<Location, UUID> armed = new HashMap<>();

    public DemomanListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Wiped between matches, so last game's mines do not persist into the next. */
    public void clearTraps() {
        armed.clear();
    }

    public int armedCount() {
        return armed.size();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (placed.getType() != Material.STONE_PRESSURE_PLATE || !game.state().isLive()) {
            return;
        }
        Player player = event.getPlayer();
        if (!kits.hasKit(player, DemomanKit.ID)) {
            return; // another kit just placed an ordinary pressure plate
        }
        if (placed.getRelative(BlockFace.DOWN).getType() != Material.GRAVEL) {
            return;
        }

        armed.put(placed.getLocation(), player.getUniqueId());
        player.sendMessage(Component.text("Mine armed.", NamedTextColor.DARK_GREEN));
    }

    /**
     * {@link Action#PHYSICAL} is what a pressure plate being stepped on looks like.
     *
     * <p>Deliberately NOT {@code ignoreCancelled}: a PHYSICAL PlayerInteractEvent reports
     * itself cancelled whenever the block's interaction is not "used", which is the normal
     * case for a plate. Filtering on that silently swallows every detonation.
     */
    @EventHandler
    public void onStep(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.STONE_PRESSURE_PLATE) {
            return;
        }
        if (!armed.containsKey(block.getLocation())) {
            return;
        }
        detonate(block);
    }

    /** Digging up either half of a mine defuses it rather than leaving a ghost entry. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        armed.remove(block.getLocation());
        armed.remove(block.getRelative(BlockFace.UP).getLocation());
    }

    /**
     * Blows the mine a tick after it is stepped on.
     *
     * <p>The delay is not cosmetic. This is triggered from a PHYSICAL interact event, and
     * vanilla writes the pressure plate's powered state back <em>after</em> the event returns —
     * so a plate cleared inside the handler reappears, leaving a live-looking plate floating
     * over the crater. Waiting a tick lets vanilla finish before the block is taken away.
     */
    private void detonate(Block plate) {
        armed.remove(plate.getLocation());

        Bukkit.getScheduler().runTask(plugin, () -> {
            Block gravel = plate.getRelative(BlockFace.DOWN);
            Location center = gravel.getLocation().add(0.5D, 0.5D, 0.5D);

            // Clear both blocks first so the explosion does not drop them back as items.
            if (plate.getType() == Material.STONE_PRESSURE_PLATE) {
                plate.setType(Material.AIR);
            }
            if (gravel.getType() == Material.GRAVEL) {
                gravel.setType(Material.AIR);
            }

            center.getWorld().createExplosion(center,
                    (float) game.config().demomanExplosionPower(),
                    false,
                    game.config().demomanBreaksBlocks());
        });
    }
}
