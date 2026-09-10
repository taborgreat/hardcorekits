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
 * Settles it one on one.
 *
 * <p>Right-click an enemy with the iron bars and both of you are taken to the Shadow Game: a
 * sealed bedrock box high over the spot where the challenge was made. For the first minute
 * there is no way out but through the other player; after that the walls and ceiling fall
 * away, and jumping is a legal answer for whoever prefers gravity's judgement.
 *
 * <p>The winner gets a moment to loot and drink, then is dropped back into the world at a
 * random spot near where it all started — never exactly there, because the loser's team
 * camping the return is the oldest trick in the book.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.GladiatorListener}.
 */
public final class GladiatorKit implements Kit {

    public static final String ID = "gladiator";

    /** The challenge. Right-clicked on an enemy to begin. */
    public static final Material BARS = Material.IRON_BARS;

    private static final Component NAME = Component.text("Shadow Game", NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Gladiator";
    }

    @Override
    public String description() {
        return "Right click an enemy with your iron bars to force a 1v1 in a sky arena. "
                + "Sealed for a minute, then the walls drop.";
    }

    @Override
    public void apply(Player player) {
        ItemStack bars = new ItemStack(BARS);
        ItemMeta meta = bars.getItemMeta();
        meta.displayName(NAME);
        bars.setItemMeta(meta);
        player.getInventory().addItem(bars);
    }
}
