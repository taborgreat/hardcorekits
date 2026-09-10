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
 * One rocket, and it never runs out.
 *
 * <p>Right-clicking it throws the Kangaroo into the air in whatever direction they are facing —
 * out of a hole, up a hillside, over a wall, or onto someone's head for the crit. The rocket is
 * not spent; it goes on the vanilla item cooldown instead, so the sweep on the item is the
 * honest picture of when the next hop is available.
 *
 * <p>Landing a hit on another player switches off fall damage for eight seconds. That is the
 * other half of the kit: it turns a ravine into a shortcut and lets a Kangaroo chase someone
 * off a cliff without paying for the landing.
 *
 * <p>The hop lives in {@link com.hardcorekits.listener.KangarooListener}.
 */
public final class KangarooKit implements Kit {

    public static final String ID = "kangaroo";

    /** The rocket is the ability; without it in hand there is no hop. */
    public static final Material ROCKET = Material.FIREWORK_ROCKET;

    private static final Component NAME =
            Component.text("Rocket", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Kangaroo";
    }

    @Override
    public String description() {
        return "A rocket that never runs out: right click to hurl yourself through the air. Landing a hit "
                + "on someone switches off your fall damage for eight seconds.";
    }

    @Override
    public void apply(Player player) {
        ItemStack rocket = new ItemStack(ROCKET);
        ItemMeta meta = rocket.getItemMeta();
        meta.displayName(NAME);
        rocket.setItemMeta(meta);
        player.getInventory().addItem(rocket);
    }
}
