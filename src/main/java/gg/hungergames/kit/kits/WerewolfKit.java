package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Strong at night, weak by day.

 * <p>After dark: Strength I, Speed I and Night Vision, constantly. In daylight: Weakness I,
 * constantly. The clock is the whole skill of the kit — it tells you whether the fight in
 * front of you is one you dominate or one you should be digging away from. Wolves never turn
 * on a Werewolf, whoever tamed them.
 *
 * <p>The classic kit also turned you into an actual wolf at night. That is a client-side
 * disguise, which a plain server cannot send without a protocol library — so the buffs, the
 * debuff and the wolf truce are all here, and the small hitbox is not.
 *
 * <p>Behaviour lives in {@link gg.hungergames.listener.WerewolfListener}.
 */
public final class WerewolfKit implements Kit {

    public static final String ID = "werewolf";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Werewolf";
    }

    @Override
    public String description() {
        return "Strength, Speed and Night Vision at night; Weakness by day. Wolves never "
                + "attack you. Starts with a clock.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(Material.CLOCK));
    }
}
