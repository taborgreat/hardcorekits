package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Turns a fall into a weapon.
 *
 * <p>A Stomper never takes more than two hearts of fall damage, and everything the fall would
 * have done to them is dealt to whoever they land on instead. Landing on someone's own block
 * transfers the lot; the further off the mark, the less lands. Crouching braces for it and
 * caps the hit at two hearts.
 *
 * <p>Ladders come in the loadout because the ability needs height, and height is not always
 * lying around.
 *
 * <p>The stomp itself lives in {@link com.hardcorekits.listener.StomperListener}.
 */
public final class StomperKit implements Kit {

    public static final String ID = "stomper";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Stomper";
    }

    @Override
    public String description() {
        return "Fall damage is capped at two hearts and dumped on whoever you land next to. Starts with ladders.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(Material.LADDER, 16));
    }
}
