package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Eats the fight.
 *
 * <p>Every hit you land on another player feeds you and leaves them with Hunger, the same
 * effect rotten flesh gives. You never starve as long as you keep finding people, and whoever
 * you have been hitting is on the way to starving whether they get away or not.
 *
 * <p>The raw cod is both a meal and the ocelot's. Creepers keep their distance from ocelots,
 * so the egg is a walking answer to being blown up mid-fight — note that ocelots have not been
 * tameable since 1.14: feeding it cod earns its trust so it stops fleeing, but it will never
 * follow you the way a wolf does. The creeper-scaring works either way, which is the point of
 * carrying it.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.CannibalListener}.
 */
public final class CannibalKit implements Kit {

    public static final String ID = "cannibal";

    private static final int RAW_FISH = 1;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Cannibal";
    }

    @Override
    public String description() {
        return "Hitting a player feeds you and gives them Hunger. You start with a raw cod and "
                + "an ocelot egg.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(Material.COD, RAW_FISH));
        player.getInventory().addItem(new ItemStack(Material.OCELOT_SPAWN_EGG));
    }
}
