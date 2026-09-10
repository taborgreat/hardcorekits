package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The Hammer: a stone axe that takes the whole column with it.
 *
 * <p>Break a block and everything above it goes too, one block at a time, to the sky limit.
 * Aim has nothing to do with it — the block you broke is the aim.
 *
 * <p>It only ever digs up, never down, so it is a tool for taking ground out from under people
 * rather than for burrowing. Anyone perched on a single block loses the block; the counter is a
 * platform wider than one, and not standing still on it.
 *
 * <p>The column costs a swing, and the swings run out: after five the hammer needs a rest.
 *
 * <p>The drilling lives in {@link com.hardcorekits.listener.JackhammerListener}.
 */
public final class JackhammerKit implements Kit {

    public static final String ID = "jackhammer";

    /** The tool the column-break is keyed to. Any stone axe works in a Jackhammer's hands. */
    public static final Material HAMMER = Material.STONE_AXE;

    private static final Component NAME =
            Component.text("Hammer", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Jackhammer";
    }

    @Override
    public String description() {
        return "A stone hammer. Break a block and the whole column above it comes down too. "
                + "Five swings, then it needs a moment to cool down.";
    }

    @Override
    public void apply(Player player) {
        ItemStack hammer = new ItemStack(HAMMER);
        ItemMeta meta = hammer.getItemMeta();
        meta.displayName(NAME);
        hammer.setItemMeta(meta);
        player.getInventory().addItem(hammer);
    }
}
