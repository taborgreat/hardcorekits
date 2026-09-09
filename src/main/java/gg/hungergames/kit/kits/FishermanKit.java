package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A rod that reels in people.
 *
 * <p>Cast at someone, and the second right-click drags them to <i>your</i> feet rather than
 * nudging them the way vanilla does. That single change is the whole kit: it answers running
 * away, it answers towering — you pull them off it, or off the edge you are standing on — and
 * the classic dirty trick is to dig a 3x1 hole, stand across it, and reel someone straight down
 * into it.
 *
 * <p>The costs are the rod's own. Hooking an entity eats five durability, so a Fisherman gets
 * roughly a dozen pulls out of the starting rod and then needs string and sticks. The line has
 * a maximum range, the flight is an arc rather than a teleport, and the victim keeps their own
 * momentum on the way in — so it can be seen coming, fought slightly, and landed badly.
 *
 * <p>The reel lives in {@link gg.hungergames.listener.FishermanListener}.
 */
public final class FishermanKit implements Kit {

    public static final String ID = "fisherman";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Fisherman";
    }

    @Override
    public String description() {
        return "Your fishing rod reels players to your feet, off towers, off ledges, into whatever you dug.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(Material.FISHING_ROD));
    }
}
