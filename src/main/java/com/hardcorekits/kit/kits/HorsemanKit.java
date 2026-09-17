package com.hardcorekits.kit.kits;

import com.hardcorekits.game.GameConfig;
import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A horse of your own.
 *
 * <p>The egg hatches a horse that is already yours: tamed, saddled, in iron armor, and only
 * you can ride it. It is allowed from the moment the match starts, since its whole point is
 * getting away from the drop. Every player you kill heals it to full, the hay bales heal it
 * by hand, and when it dies the kit is spent.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.HorsemanListener}.
 */
public final class HorsemanKit implements Kit {

    public static final String ID = "horseman";

    private final GameConfig config;

    public HorsemanKit(GameConfig config) {
        this.config = config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Horseman";
    }

    @Override
    public String description() {
        return "A horse egg and 2 hay bales. The egg hatches a saddled, armored horse only you can "
                + "ride, ready from the start. Every player you kill heals it to full.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(Material.HORSE_SPAWN_EGG));
        player.getInventory().addItem(new ItemStack(Material.HAY_BLOCK, config.horsemanHayBales()));
    }
}
