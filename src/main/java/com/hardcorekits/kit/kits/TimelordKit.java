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
 * A pocket watch that stops the clock for everyone but its owner.
 *
 * <p>Right-clicking it roots every other player within six blocks for ten seconds. They keep
 * their swing and their aim — this is not a free kill — but they cannot walk, jump, dig out or
 * pillar away, which is enough to take a team apart one member at a time instead of all at
 * once. Hitting a frozen player releases them, so the Timelord chooses who gets to move.
 *
 * <p>Animals and mobs stop too, which makes a bad spawn survivable.
 *
 * <p>The spell lives in {@link com.hardcorekits.listener.TimelordListener}.
 */
public final class TimelordKit implements Kit {

    public static final String ID = "timelord";

    /** The watch is the ability; there is nothing to cast without it in hand. */
    public static final Material WATCH = Material.CLOCK;

    private static final Component NAME = Component.text("Pocket Watch", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Timelord";
    }

    @Override
    public String description() {
        return "A pocket watch that freezes everyone within six blocks for ten seconds. They can still "
                + "swing, and hitting one releases it.";
    }

    @Override
    public void apply(Player player) {
        ItemStack watch = new ItemStack(WATCH);
        ItemMeta meta = watch.getItemMeta();
        meta.displayName(NAME);
        watch.setItemMeta(meta);
        player.getInventory().addItem(watch);
    }
}
