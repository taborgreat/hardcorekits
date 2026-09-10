package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The walking stick, and everyone else does the walking.
 *
 * <p>One stick with Knockback II. It does no damage worth the name, so it is not a weapon —
 * it is a way to decide where a fight happens. On hilly ground a swing sends whoever came for
 * you off the ledge to finish the job themselves; on flat ground it buys the seconds to drink
 * soup or sort an inventory, and it cuts off anyone trying to run.
 *
 * <p>The enchantment is hidden, glint and all, exactly as the original kit had it: the stick
 * looks like every other stick in the world. There is only one, nothing marks it, and dropping
 * it into a pile of firewood is how a Grandpa loses the kit.
 *
 * <p>No listener — the enchantment is the whole ability.
 */
public final class GrandpaKit implements Kit {

    public static final String ID = "grandpa";

    private static final int KNOCKBACK_LEVEL = 2;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Grandpa";
    }

    @Override
    public String description() {
        return "A plain looking stick with Knockback II. Hits send people flying, off cliffs if you pick "
                + "your ground. There is only one, so do not lose it.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(stick());
    }

    private static ItemStack stick() {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        meta.addEnchant(Enchantment.KNOCKBACK, KNOCKBACK_LEVEL, true);

        // Indistinguishable from firewood: no enchantment line in the tooltip, and no glint.
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(false);

        stick.setItemMeta(meta);
        return stick;
    }
}
