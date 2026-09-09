package gg.hungergames.listener;

import gg.hungergames.game.GameConfig;
import gg.hungergames.game.GameManager;
import gg.hungergames.util.Interact;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

/**
 * Soup, the classic Hunger Games healing item.
 *
 * <p>Mushroom stew skips the vanilla eat animation entirely — one right click and it is gone.
 * A soup tops up health first (overflow past full is simply lost) and only feeds the hunger
 * bar once health is already full, so soup is a combat item rather than food.
 *
 * <p>Also owns the bowl + 2 cactus recipe, which is what keeps soup renewable in a match with
 * no mushroom biome in the border.
 */
public final class SoupListener implements Listener {

    /** Vanilla hunger cap. Not configurable — it is a client-side constant. */
    private static final int MAX_FOOD = 20;

    /** Only used if the max-health attribute is somehow missing. */
    private static final double DEFAULT_MAX_HEALTH = 20.0D;

    private final GameManager game;

    public SoupListener(GameManager game) {
        this.game = game;
    }

    // ---------------------------------------------------------------- eating

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.MUSHROOM_STEW
                || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();

        // The event fires once per hand. With stew in both hands the off-hand pass would drink
        // a second bowl off a single click, so let the main hand win.
        if (event.getHand() == EquipmentSlot.OFF_HAND
                && player.getInventory().getItemInMainHand().getType() == Material.MUSHROOM_STEW) {
            return;
        }

        // Right-clicking a chest or a crafting table with soup in hand should still open it.
        if (action == Action.RIGHT_CLICK_BLOCK && Interact.opensBlock(event)) {
            return;
        }

        // Cancel unconditionally: even a soup that heals nothing must not start the 1.6s
        // vanilla eat animation.
        event.setCancelled(true);
        if (!drink(player)) {
            return;
        }
        replaceWithBowl(player, event.getHand(), item);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.5F, 1.0F);
    }

    /**
     * Applies one soup. Health first, hunger only at full health.
     *
     * @return false if the player was full on both, in which case they keep the soup
     */
    private boolean drink(Player player) {
        GameConfig config = game.config();

        double max = maxHealth(player);
        double health = player.getHealth();
        if (health < max) {
            player.setHealth(Math.min(max, health + config.soupHeal()));
            return true;
        }

        int food = player.getFoodLevel();
        if (food >= MAX_FOOD) {
            return false;
        }
        player.setFoodLevel(Math.min(MAX_FOOD, food + config.soupFood()));
        // Vanilla never lets saturation exceed the hunger bar itself.
        player.setSaturation(Math.min(player.getFoodLevel(),
                player.getSaturation() + config.soupSaturation()));
        return true;
    }

    private double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? DEFAULT_MAX_HEALTH : attribute.getValue();
    }

    /** Swaps the drunk stew for its bowl, dropping the bowl only if the inventory is full. */
    private void replaceWithBowl(Player player, EquipmentSlot hand, ItemStack stew) {
        PlayerInventory inv = player.getInventory();
        ItemStack bowl = new ItemStack(Material.BOWL);

        if (stew.getAmount() > 1) {
            ItemStack remaining = stew.clone();
            remaining.setAmount(remaining.getAmount() - 1);
            setHand(inv, hand, remaining);
            for (ItemStack leftover : inv.addItem(bowl).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            return;
        }
        setHand(inv, hand, bowl);
    }

    private void setHand(PlayerInventory inv, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) {
            inv.setItemInOffHand(item);
        } else {
            inv.setItemInMainHand(item);
        }
    }

    // ---------------------------------------------------------------- recipe

    /** bowl + 2 cactus -> mushroom stew. */
    public static void registerRecipe(Plugin plugin) {
        NamespacedKey key = recipeKey(plugin);
        Bukkit.removeRecipe(key); // a /reload would otherwise trip the duplicate-key check
        ShapelessRecipe recipe = new ShapelessRecipe(key, new ItemStack(Material.MUSHROOM_STEW));
        recipe.addIngredient(1, Material.BOWL);
        recipe.addIngredient(2, Material.CACTUS);
        Bukkit.addRecipe(recipe);
    }

    public static void unregisterRecipe(Plugin plugin) {
        Bukkit.removeRecipe(recipeKey(plugin));
    }

    private static NamespacedKey recipeKey(Plugin plugin) {
        return new NamespacedKey(plugin, "cactus_stew");
    }
}
