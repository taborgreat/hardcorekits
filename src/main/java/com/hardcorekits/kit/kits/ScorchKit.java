package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Burns where it runs.
 *
 * <p>Wearing the Flame Boots with the blaze powder in your main hand, every block you leave
 * catches fire behind you. The powder is the switch: swap to your sword and the trail stops,
 * swap back and it starts — so a chase becomes a choice between fighting and torching the
 * ground the chaser needs.
 *
 * <p>The fire is real fire. It spreads, and it burns you exactly as readily as them — stop
 * moving on your own trail and the kit kills its owner.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.ScorchListener}.
 */
public final class ScorchKit implements Kit {

    public static final String ID = "scorch";

    /** Marks the Flame Boots — the trail only burns while these are on your feet. */
    public static final NamespacedKey BOOTS_KEY = new NamespacedKey("hardcoregames", "flame_boots");

    /** The switch item. Trail on while held in the main hand, off otherwise. */
    public static final Material POWDER = Material.BLAZE_POWDER;

    private static final Component NAME = Component.text("Flame Boots", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Scorch";
    }

    @Override
    public String description() {
        return "Flame Boots and blaze powder. Wear the boots and hold the powder to leave a "
                + "trail of fire behind you. It burns you too.";
    }

    @Override
    public void apply(Player player) {
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        ItemMeta meta = boots.getItemMeta();
        meta.displayName(NAME);
        meta.getPersistentDataContainer().set(BOOTS_KEY, PersistentDataType.BYTE, (byte) 1);
        boots.setItemMeta(meta);
        player.getInventory().addItem(boots);
        player.getInventory().addItem(new ItemStack(POWDER));
    }
}
