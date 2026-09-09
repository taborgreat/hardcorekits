package gg.hungergames.listener;

import gg.hungergames.game.GameConfig;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.CookiemonsterKit;
import gg.hungergames.util.Interact;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Cookiemonster's cookies.
 *
 * <p>Eating is instant, like soup, and what a cookie does depends entirely on what the eater is
 * short of: hunger first, then health, and Speed II only when there is nothing left to top up.
 * That order is the kit. It means the escape is available exactly when a Cookiemonster is
 * healthy, and taking that away is as simple as hitting them.
 *
 * <p>A cookie in anyone else's hand is an ordinary cookie.
 */
public final class CookiemonsterListener implements Listener {

    /** Vanilla hunger cap. A client-side constant, not a setting. */
    private static final int MAX_FOOD = 20;

    /** Only used if the max-health attribute is somehow missing. */
    private static final double DEFAULT_MAX_HEALTH = 20.0D;

    /** What counts as grass for the drop. */
    private static final Set<Material> GRASS = EnumSet.of(
            Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN);

    private final GameManager game;
    private final KitRegistry kits;

    public CookiemonsterListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    // ---------------------------------------------------------------- the harvest

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!GRASS.contains(block.getType()) || !game.state().isLive()) {
            return;
        }
        Player player = event.getPlayer();
        if (!kits.hasKit(player, CookiemonsterKit.ID)) {
            return; // grass is just grass for everyone else
        }
        if (ThreadLocalRandom.current().nextDouble() >= game.config().cookieGrassChance()) {
            return;
        }

        // Dropped rather than given, so a full inventory is the player's problem to solve.
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.2D, 0.5D),
                new ItemStack(Material.COOKIE));
    }

    // ---------------------------------------------------------------- eating

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COOKIE) {
            return;
        }
        Player player = event.getPlayer();
        if (!kits.hasKit(player, CookiemonsterKit.ID)) {
            return;
        }

        // With cookies in both hands the off-hand pass would eat a second one on a single
        // click, so let the main hand win.
        if (event.getHand() == EquipmentSlot.OFF_HAND
                && player.getInventory().getItemInMainHand().getType() == Material.COOKIE) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && Interact.opensBlock(event)) {
            return;
        }

        // Cancelled unconditionally: a cookie is never chewed, it is swallowed.
        event.setCancelled(true);
        eat(player);
        consume(player, event.getHand(), item);
    }

    /** Hunger, then health, then speed. Something always happens, so a cookie is never wasted. */
    private void eat(Player player) {
        GameConfig config = game.config();

        int food = player.getFoodLevel();
        if (food < MAX_FOOD) {
            player.setFoodLevel(Math.min(MAX_FOOD, food + config.cookieFood()));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.5F, 1.4F);
            return;
        }

        double max = maxHealth(player);
        double health = player.getHealth();
        if (health < max) {
            player.setHealth(Math.min(max, health + config.cookieHeal()));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.5F, 1.4F);
            return;
        }

        int seconds = config.cookieSpeedSeconds();
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                seconds * 20, config.cookieSpeedLevel() - 1));
        player.sendMessage(Component.text("Sugar rush. Speed for " + seconds + "s.",
                NamedTextColor.GOLD));
    }

    private double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? DEFAULT_MAX_HEALTH : attribute.getValue();
    }

    /** Takes the one cookie that was eaten, leaving the rest of the stack alone. */
    private void consume(Player player, EquipmentSlot hand, ItemStack cookies) {
        PlayerInventory inv = player.getInventory();
        ItemStack left = cookies.clone();
        left.setAmount(left.getAmount() - 1);
        ItemStack remaining = left.getAmount() <= 0 ? null : left;

        if (hand == EquipmentSlot.OFF_HAND) {
            inv.setItemInOffHand(remaining);
        } else {
            inv.setItemInMainHand(remaining);
        }
    }
}
