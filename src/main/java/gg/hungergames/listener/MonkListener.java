package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.MonkKit;
import gg.hungergames.util.CooldownBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Monk's touch.
 *
 * <p>The held item leaves the victim's hotbar for the depths of their inventory — first empty
 * backpack slot, or a swap with a random one when nothing is free, so the disarm never
 * destroys anything. The pain is entirely the fumble: three menus between them and their
 * sword, in the middle of a fight.
 *
 * <p>An empty hand is not a target and does not spend the cooldown — the touch is for
 * disarming, not for tapping people on the shoulder.
 */
public final class MonkListener implements Listener {

    /** Backpack rows are slots 9..35; 0..8 is the hotbar the item is exiled from. */
    private static final int BACKPACK_START = 9;
    private static final int BACKPACK_END = 35;

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** When each Monk may touch again. */
    private final Map<UUID, Long> nextTouch = new HashMap<>();

    public MonkListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset like every other kit's. */
    public void clearCooldowns() {
        nextTouch.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onTouch(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player monk = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(monk, MonkKit.ID)) {
            return;
        }
        if (monk.getInventory().getItemInMainHand().getType() != MonkKit.ROD) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player victim) || victim.equals(monk)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long until = nextTouch.get(monk.getUniqueId());
        if (until != null && now < until) {
            monk.sendActionBar(Component.text("Your touch has "
                    + ((until - now) / 1000L + 1) + "s cooldown left!", NamedTextColor.RED));
            return;
        }

        PlayerInventory bag = victim.getInventory();
        int held = bag.getHeldItemSlot();
        ItemStack item = bag.getItem(held);
        if (item == null || item.getType().isAir()) {
            monk.sendActionBar(Component.text("They are holding nothing.", NamedTextColor.GRAY));
            return; // no disarm, no cooldown spent
        }

        bury(bag, held, item);

        int cooldown = game.config().monkCooldownSeconds();
        nextTouch.put(monk.getUniqueId(), now + cooldown * 1000L);
        CooldownBar.show(plugin, game, monk, cooldown);

        victim.sendMessage(Component.text("A Monk has knocked the "
                + item.getType().name().toLowerCase().replace('_', ' ')
                + " from your hand!", NamedTextColor.RED));
        monk.playSound(monk.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.7F, 1.6F);
    }

    /** Out of the hotbar: first free backpack slot, or a swap with a random full one. */
    private void bury(PlayerInventory bag, int heldSlot, ItemStack item) {
        for (int slot = BACKPACK_START; slot <= BACKPACK_END; slot++) {
            ItemStack occupant = bag.getItem(slot);
            if (occupant == null || occupant.getType().isAir()) {
                bag.setItem(slot, item);
                bag.setItem(heldSlot, null);
                return;
            }
        }
        int slot = ThreadLocalRandom.current().nextInt(BACKPACK_START, BACKPACK_END + 1);
        bag.setItem(heldSlot, bag.getItem(slot));
        bag.setItem(slot, item);
    }
}
