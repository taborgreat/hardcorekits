package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Curses with the scythe.
 *
 * <p>Hits with the wooden hoe apply Wither: damage over time, and — the nastier half — hearts
 * that render black, so the victim cannot read their own health and panics into their soup
 * early. The hoe itself hits like the farm tool it is, which is the balance: a Reaper swing
 * trades sword damage for the curse.
 *
 * <p>Behaviour lives in {@link gg.hungergames.listener.ReaperListener}.
 */
public final class ReaperKit implements Kit {

    public static final String ID = "reaper";

    /** The scythe. Any wooden hoe curses in a Reaper's hands. */
    public static final Material SCYTHE = Material.WOODEN_HOE;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Reaper";
    }

    @Override
    public String description() {
        return "Hits with your wooden hoe wither players — damage over time, and hearts they "
                + "can't read.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(SCYTHE));
    }
}
