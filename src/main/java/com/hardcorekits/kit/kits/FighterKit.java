package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * A wooden sword and two steak, and nothing else.
 *
 * <p>The plain option, for players who would rather have gear than an ability. Note it is not
 * a fallback — picking nothing gets you nothing, not a Fighter.
 */
public final class FighterKit implements Kit {

    public static final String ID = "fighter";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Fighter";
    }

    @Override
    public String description() {
        return "A wooden sword and two steak. No abilities.";
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.addItem(new ItemStack(Material.WOODEN_SWORD));
        inv.addItem(new ItemStack(Material.COOKED_BEEF, 2));
    }
}
