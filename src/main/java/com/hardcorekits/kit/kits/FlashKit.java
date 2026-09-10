package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Is suddenly somewhere else.
 *
 * <p>Right-click the redstone torch and you are teleported to the spot you are looking at —
 * a tower top, the far side of a river, directly in front of whoever thought they were
 * getting away. The arrival is announced: lightning cracks over the landing and a trail of
 * portal sparks draws the line you travelled, so everyone knows where the Flash went.
 *
 * <p>The bill comes as Weakness — one second per two blocks travelled — and a long cooldown,
 * drawn on both the torch and the XP bar. Flashing in is easy; being worth anything when you
 * arrive is the skill.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.FlashListener}.
 */
public final class FlashKit implements Kit {

    public static final String ID = "flash";

    /** The torch the flash is keyed to. */
    public static final Material TORCH = Material.REDSTONE_TORCH;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Flash";
    }

    @Override
    public String description() {
        return "Swing (left click) your redstone torch to teleport where you're looking. Costs "
                + "Weakness (1s per 2 blocks) and a long cooldown.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(TORCH));
    }
}
