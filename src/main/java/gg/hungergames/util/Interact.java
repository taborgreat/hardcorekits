package gg.hungergames.util;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;

/** Shared right-click plumbing for kits and items that answer a click in the hand. */
public final class Interact {

    private Interact() {
    }

    /**
     * Whether a click lands on the block rather than the item, exactly as vanilla decides it
     * for food — so a chest or a crafting table still opens instead of eating the click.
     *
     * <p>{@code isInteractable} is deprecated for being a coarse approximation, and Paper
     * offers nothing finer. The miss cases are obscure blocks, so the worst outcome is an
     * ability firing a moment early.
     */
    @SuppressWarnings("deprecation")
    public static boolean opensBlock(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        return !event.getPlayer().isSneaking()
                && event.useInteractedBlock() != Event.Result.DENY
                && clicked != null
                && clicked.getType().isInteractable();
    }
}
