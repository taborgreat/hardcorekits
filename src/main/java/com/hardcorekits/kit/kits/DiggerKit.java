package com.hardcorekits.kit.kits;

import com.hardcorekits.game.GameConfig;
import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Takes the floor away.
 *
 * <p>Place a dragon egg and a second and a half later a 5x5 shaft opens straight down under it.
 * The delay is the whole kit: long enough that the egg is a visible warning, short enough that
 * someone chasing you runs into it anyway. Six eggs is six holes, and no more.
 *
 * <p>It answers being chased — drop one behind you and whoever is on your heels is suddenly at
 * the bottom of a pit — and it opens fights that were not going to happen, because a player at
 * the bottom of a shaft can be shot at, poured onto, or simply left there while you leave.
 *
 * <p>The counters are in plain sight. The egg is a bright block sitting on the ground with a
 * fuse hissing, so watch the hands of anyone holding one; and a hole is only a trap for as long
 * as the person in it has nothing to build with.
 *
 * <p>An egg placed by any other kit is an ordinary dragon egg. The wiring is what the kit
 * provides, exactly as with a Demoman's gravel.
 *
 * <p>The dig lives in {@link com.hardcorekits.listener.DiggerListener}.
 */
public final class DiggerKit implements Kit {

    public static final String ID = "digger";

    /** What arms a hole. Chosen because it is impossible to mistake for scenery. */
    public static final Material EGG = Material.DRAGON_EGG;

    private final GameConfig config;

    public DiggerKit(GameConfig config) {
        this.config = config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Digger";
    }

    @Override
    public String description() {
        return "Place a dragon egg and a second later a 5x5 shaft drops out under it. You "
                + "start with six.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(EGG, config.diggerEggs()));
    }
}
