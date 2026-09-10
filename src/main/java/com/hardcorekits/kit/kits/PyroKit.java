package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Fire, early and late.
 *
 * <p>The flint and steel is the quiet half: it cooks a cow where it stands, it makes a doorway
 * expensive to walk through, and a burning opponent takes damage they cannot sprint away from.
 * The fire charges are the loud half — right-click to throw one, and whatever it lands near
 * catches.
 *
 * <p>Five charges is the whole magazine. There is no cooldown and no recharge, so the kit is
 * five decisions: opening a fight, breaking one up, or panicking a group at the feast.
 *
 * <p>Note this is <em>not</em> vanilla's fire charge, which merely lights the block you clicked.
 * The throw lives in {@link com.hardcorekits.listener.PyroListener}.
 */
public final class PyroKit implements Kit {

    public static final String ID = "pyro";

    /** The thrown charge. Also what the listener keys the ability to. */
    public static final Material CHARGE = Material.FIRE_CHARGE;

    private static final int CHARGES = 5;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Pyro";
    }

    @Override
    public String description() {
        return "Flint and steel, and 5 fire charges. Right-click a charge to throw it — "
                + "everything near where it lands catches fire.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(Material.FLINT_AND_STEEL));
        player.getInventory().addItem(new ItemStack(CHARGE, CHARGES));
    }
}
