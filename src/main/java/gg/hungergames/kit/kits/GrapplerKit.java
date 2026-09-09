package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Goes where the hook goes.
 *
 * <p>Cast the Grappling Hook like a fishing rod; once it bites — terrain, mob or player —
 * reeling launches <em>you</em> at it. Up a tower in one pull, across a ravine, or out of a
 * fall just before the ground settles it: pulling wipes your fall distance, which is the
 * advertised save.
 *
 * <p>The mirror-image kit already exists: a Fisherman reels others in; a Grappler reels
 * themselves.
 *
 * <p>Behaviour lives in {@link gg.hungergames.listener.GrapplerListener}.
 */
public final class GrapplerKit implements Kit {

    public static final String ID = "grappler";

    /** The hook. Any fishing rod grapples in a Grappler's hands. */
    public static final Material HOOK = Material.FISHING_ROD;

    private static final Component NAME = Component.text("Grappling Hook", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Grappler";
    }

    @Override
    public String description() {
        return "Cast your hook at terrain, mobs or players and reel to launch yourself "
                + "towards it. Pulling saves you from falls.";
    }

    @Override
    public void apply(Player player) {
        ItemStack hook = new ItemStack(HOOK);
        ItemMeta meta = hook.getItemMeta();
        meta.displayName(NAME);
        hook.setItemMeta(meta);
        player.getInventory().addItem(hook);
    }
}
