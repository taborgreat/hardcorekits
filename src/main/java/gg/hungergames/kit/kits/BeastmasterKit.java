package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Brings a pack.
 *
 * <p>Three wolf eggs and four bones, and every bone works — vanilla's one-in-three taming roll
 * is replaced with a certainty, so no egg is ever wasted on a wolf that refuses. Each wolf that
 * joins you is buffed with either speed or regeneration.
 *
 * <p>The pack fights the way vanilla wolves already do: hit someone once and they pile on,
 * which buys you the seconds to heal or leave. The counter is to go through the owner rather
 * than the dogs.
 *
 * <p>Timing matters — taming during the invincibility phase mostly means handing the other
 * players free wolf kills.
 *
 * <p>Behaviour lives in {@link gg.hungergames.listener.BeastmasterListener}.
 */
public final class BeastmasterKit implements Kit {

    public static final String ID = "beastmaster";

    private static final int WOLF_EGGS = 3;
    private static final int BONES = 4;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Beastmaster";
    }

    @Override
    public String description() {
        return "3 wolf eggs, 4 bones, and every bone tames. Your wolves get speed or regeneration.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(Material.WOLF_SPAWN_EGG, WOLF_EGGS));
        player.getInventory().addItem(new ItemStack(Material.BONE, BONES));
    }
}
