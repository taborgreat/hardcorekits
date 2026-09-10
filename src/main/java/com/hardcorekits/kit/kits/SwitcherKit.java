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
 * Trades places with whatever it hits.
 *
 * <p>Land a Switcher Ball on a player or mob and the two of you swap positions on the spot —
 * the tower camper is suddenly on the ground looking up at you, and their tower is yours to
 * disarm from above.
 *
 * <p>The balls are the budget: ten for the whole match, and ordinary snowballs are just
 * snow — the balls carry a tag, so picking up more snowballs replenishes nothing. A throw also
 * starts a cooldown, so the counter is dodging: every ball dodged is a tenth of the kit gone.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.SwitcherListener}.
 */
public final class SwitcherKit implements Kit {

    public static final String ID = "switcher";

    /** Marks the real Switcher Balls — on the items, and on the snowballs they launch. */
    public static final NamespacedKey BALL_KEY = new NamespacedKey("hardcoregames", "switcher_ball");

    private static final int BALLS = 10;

    private static final Component NAME = Component.text("Switcher Ball", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Switcher";
    }

    @Override
    public String description() {
        return "10 Switcher Balls. Land one on a player or mob and you swap places. "
                + "Ordinary snowballs don't count.";
    }

    @Override
    public void apply(Player player) {
        ItemStack balls = new ItemStack(Material.SNOWBALL, BALLS);
        ItemMeta meta = balls.getItemMeta();
        meta.displayName(NAME);
        meta.getPersistentDataContainer().set(BALL_KEY, PersistentDataType.BYTE, (byte) 1);
        balls.setItemMeta(meta);
        player.getInventory().addItem(balls);
    }
}
