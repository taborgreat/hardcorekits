package com.hardcorekits.kit.kits;

import com.hardcorekits.game.GameConfig;
import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Floor that is not there.
 *
 * <p>Grass you place vanishes the instant an enemy stands on it, so a sheet of it over a
 * ravine, a lava pool or a pit you dug yourself is a kill nobody sees coming. Your own weight
 * never triggers it, which is what makes it safe to lay traps around your own base and walk
 * through them afterwards.
 *
 * <p>You start with a handful and craft more from dirt and seeds, so the trap count is limited
 * by what you can scavenge rather than by a cooldown.
 *
 * <p>The counter is simply to walk, not sprint — a careful player has time to notice the ground
 * going missing and step back.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.KayaListener}.
 */
public final class KayaKit implements Kit {

    public static final String ID = "kaya";

    private final GameConfig config;

    public KayaKit(GameConfig config) {
        this.config = config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Kaya";
    }

    @Override
    public String description() {
        return "Grass you place vanishes under enemies. Craft more from dirt and seeds.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(
                new ItemStack(Material.GRASS_BLOCK, config.kayaStartingBlocks()));
    }
}
