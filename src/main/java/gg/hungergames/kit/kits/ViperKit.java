package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Venom on every blade.
 *
 * <p>A third of your hits on players leave Poison behind — steady chip damage that cannot
 * kill on its own (poison stops at half a heart) but makes running, towering and re-souping
 * all cost more than they should. The panic it causes is the real weapon.
 *
 * <p>Behaviour lives in {@link gg.hungergames.listener.ViperListener}.
 */
public final class ViperKit implements Kit {

    public static final String ID = "viper";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Viper";
    }

    @Override
    public String description() {
        return "Your hits on players have a 1 in 3 chance to poison them.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment — the venom rides on whatever you swing.
    }
}
