package gg.hungergames.listener;

import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.ScorchKit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Scorch's trail.
 *
 * <p>The fire runs a few steps behind the runner, not at their heels: each block left goes
 * into a short queue, and only the block a few moves back is lit — and even then only once
 * it is genuinely clear of the Scorch. Momentum outruns the trail; hesitation stands in it.
 * A Scorch who doubles back through their own fire still burns like anyone else, which is
 * the kit's whole balance and stays.
 *
 * <p>Both conditions are checked live on every step — the tagged boots on the feet, the
 * powder in the main hand — and either lapsing clears the queue outright, so re-arming never
 * ignites stale ground from half a fight ago.
 */
public final class ScorchListener implements Listener {

    /** Steps between the runner and the newest flame. */
    private static final int TRAIL_LAG_STEPS = 2;
    /** And never lit while the Scorch is still this close, whatever the queue says. */
    private static final double MIN_IGNITE_DISTANCE = 1.8D;

    private final GameManager game;
    private final KitRegistry kits;

    /** The last few blocks each active Scorch stepped off, oldest first. */
    private final Map<UUID, Deque<Block>> trails = new HashMap<>();

    public ScorchListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset like every other kit's. */
    public void clearTrails() {
        trails.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(player, ScorchKit.ID)) {
            return;
        }
        if (player.getInventory().getItemInMainHand().getType() != ScorchKit.POWDER
                || !wearingFlameBoots(player)) {
            trails.remove(player.getUniqueId());
            return;
        }

        Deque<Block> trail = trails.computeIfAbsent(player.getUniqueId(),
                ignored -> new ArrayDeque<>());
        trail.addLast(event.getFrom().getBlock());
        if (trail.size() <= TRAIL_LAG_STEPS) {
            return; // still building the gap
        }

        Block due = trail.removeFirst();
        // Only where fire can honestly sit, and only once the runner is clear of it.
        if (due.getType().isAir()
                && due.getRelative(0, -1, 0).getType().isSolid()
                && due.getLocation().add(0.5D, 0.5D, 0.5D)
                        .distance(player.getLocation()) >= MIN_IGNITE_DISTANCE) {
            due.setType(Material.FIRE);
        }
    }

    private boolean wearingFlameBoots(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        return boots != null && boots.hasItemMeta()
                && boots.getItemMeta().getPersistentDataContainer()
                        .has(ScorchKit.BOOTS_KEY, PersistentDataType.BYTE);
    }
}
