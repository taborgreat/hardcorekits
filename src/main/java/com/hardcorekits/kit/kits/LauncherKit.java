package com.hardcorekits.kit.kits;

import com.hardcorekits.game.GameConfig;
import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Sponges that throw whoever stands on them.
 *
 * <p>A Launcher starts with a stack of sponges and no other trick. Anyone who steps on one goes
 * up, the Launcher included, and lands without taking the fall for it.
 *
 * <p>The pad's shape is the aim. A flat, even pad throws straight up; a lopsided one — a
 * diagonal line, an edge, a stripe — throws sideways, away from the weight of the sponges. And
 * sponges stacked on top of each other throw harder, so height is built rather than tuned.
 *
 * <p>Only sponges a Launcher placed are live, exactly like a Demoman's mines: a sponge looted
 * off a dead Launcher and put down by someone else is a sponge.
 *
 * <p>The launching lives in {@link com.hardcorekits.listener.LauncherListener}.
 */
public final class LauncherKit implements Kit {

    public static final String ID = "launcher";

    private final GameConfig config;

    public LauncherKit(GameConfig config) {
        this.config = config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Launcher";
    }

    @Override
    public String description() {
        return "Sponges that throw anyone who steps on them, you included, with no fall damage on the "
                + "way down. Stack them for height, lay them lopsided to throw sideways.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(Material.SPONGE, config.launcherSponges()));
    }
}
