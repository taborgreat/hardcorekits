package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Fire cannot touch it, and neither can the sky.
 *
 * <p>Flames, lava and lightning all do nothing — which makes a Thor's strike a wasted move and
 * turns a lava pool into cover rather than a wall. Anyone chasing you into one does not come
 * out.
 *
 * <p>Lava is not a permanent home, though: you still hold your breath in it like anyone else,
 * so a long swim drowns you even while the heat does nothing. That is the natural limit on the
 * kit rather than a timer bolted on top.
 *
 * <p>The water bucket is the other half. It is a ladder up a cliff, a safe way down a long
 * drop, and — once poured out and refilled with lava — a weapon.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.FiremanListener}.
 */
public final class FiremanKit implements Kit {

    public static final String ID = "fireman";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Fireman";
    }

    @Override
    public String description() {
        return "Immune to fire, lava and lightning. Starts with a water bucket. You can still drown.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));
    }
}
