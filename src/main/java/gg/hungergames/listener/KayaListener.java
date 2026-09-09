package gg.hungergames.listener;

import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.KayaKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Keyed;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kaya's collapsing grass.
 *
 * <p>Like the Demoman's mines, a trap is a registry entry rather than a special block: the
 * world holds an ordinary grass block, so it is indistinguishable from the ground around it
 * and grass placed by anyone else behaves normally.
 *
 * <p>The owner is stored with each trap because a Kaya must be able to cross their own field —
 * the whole point is laying it around ground you intend to keep using.
 */
public final class KayaListener implements Listener {

    private final GameManager game;
    private final KitRegistry kits;
    private final NamespacedKey recipeKey;

    /** Grass block location -> whoever laid it. */
    private final Map<Location, UUID> traps = new HashMap<>();

    public KayaListener(Plugin plugin, GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
        this.recipeKey = recipeKey(plugin);
    }

    public void clearTraps() {
        traps.clear();
    }

    public int trapCount() {
        return traps.size();
    }

    // ---------------------------------------------------------------- crafting

    private static NamespacedKey recipeKey(Plugin plugin) {
        return new NamespacedKey(plugin, "kaya_grass");
    }

    /** Dirt plus seeds makes grass, so a Kaya can keep making traps from scavenged material. */
    public static void registerRecipe(Plugin plugin) {
        NamespacedKey key = recipeKey(plugin);
        Bukkit.removeRecipe(key); // a /reload would otherwise trip the duplicate-key check
        ShapelessRecipe recipe = new ShapelessRecipe(key, new ItemStack(Material.GRASS_BLOCK));
        recipe.addIngredient(1, Material.DIRT);
        recipe.addIngredient(1, Material.WHEAT_SEEDS);
        Bukkit.addRecipe(recipe);
    }

    public static void unregisterRecipe(Plugin plugin) {
        Bukkit.removeRecipe(recipeKey(plugin));
    }

    /** The recipe is global once registered, so gate the result to Kaya here. */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !recipeKey.equals(keyed.getKey())) {
            return;
        }
        HumanEntity crafter = event.getView().getPlayer();
        if (!(crafter instanceof Player player) || !kits.hasKit(player, KayaKit.ID)) {
            event.getInventory().setResult(null);
        }
    }

    // ---------------------------------------------------------------- laying and springing

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (placed.getType() != Material.GRASS_BLOCK || !game.state().isLive()) {
            return;
        }
        Player player = event.getPlayer();
        if (!kits.hasKit(player, KayaKit.ID)) {
            return; // ordinary grass for anyone else
        }
        traps.put(placed.getLocation(), player.getUniqueId());
    }

    /**
     * Springs the trap when someone other than its owner stands on it.
     *
     * <p>Only checked when the player actually changes block, and skipped entirely while no
     * traps exist — this runs on every movement packet otherwise.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (traps.isEmpty() || !game.state().isLive()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Block underfoot = to.getBlock().getRelative(BlockFace.DOWN);
        if (underfoot.getType() != Material.GRASS_BLOCK) {
            return;
        }

        UUID owner = traps.get(underfoot.getLocation());
        Player walker = event.getPlayer();
        if (owner == null || owner.equals(walker.getUniqueId())) {
            return; // untrapped ground, or the Kaya crossing their own field
        }

        traps.remove(underfoot.getLocation());
        underfoot.setType(Material.AIR);
        underfoot.getWorld().playSound(underfoot.getLocation(),
                Sound.BLOCK_GRASS_BREAK, 1.0F, 0.8F);

        Player kaya = Bukkit.getPlayer(owner);
        if (kaya != null && kaya.isOnline()) {
            kaya.sendMessage(Component.text(walker.getName() + " fell through your grass.",
                    NamedTextColor.GREEN));
        }
    }

    /** Digging a trap up disarms it rather than leaving a ghost entry behind. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        traps.remove(event.getBlock().getLocation());
    }
}
