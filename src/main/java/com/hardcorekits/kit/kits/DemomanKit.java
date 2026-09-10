package com.hardcorekits.kit.kits;

import com.hardcorekits.game.GameConfig;
import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Landmines disguised as terrain.
 *
 * <p>Placing a stone pressure plate on top of a gravel block arms it — for a Demoman only.
 * Anyone stepping on it, the Demoman included, sets it off. Another kit doing the same thing
 * gets an ordinary pressure plate on ordinary gravel; the wiring is what the kit provides.
 *
 * <p>You start with exactly one of each, so more traps mean scavenging more gravel and
 * plates during the match.
 *
 * <p>The arming and detonation live in {@link com.hardcorekits.listener.DemomanListener}.
 */
public final class DemomanKit implements Kit {

    public static final String ID = "demoman";

    private final GameConfig config;

    public DemomanKit(GameConfig config) {
        this.config = config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Demoman";
    }

    @Override
    public String description() {
        return "Put a stone pressure plate on gravel and it becomes a hidden mine. You start "
                + "with two of each, so pick your spots.";
    }

    @Override
    public void apply(Player player) {
        int mines = config.demomanMines();
        player.getInventory().addItem(new ItemStack(Material.GRAVEL, mines));
        player.getInventory().addItem(new ItemStack(Material.STONE_PRESSURE_PLATE, mines));
    }
}
