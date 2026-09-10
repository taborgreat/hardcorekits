package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.GamblerKit;
import com.hardcorekits.util.CooldownBar;
import com.hardcorekits.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Gambler's wager.
 *
 * <p>The button is a placement registry like the Demoman's gravel: only a button a Gambler
 * placed pays out, and only to a Gambler pressing it — anyone else pressing it is just a
 * person pressing a button. Effects run half a minute; the two long shots are exactly the
 * thousand-to-one the legend says.
 *
 * <p>A short per-player cooldown keeps it a wager rather than a slot machine being fed until
 * the diamond comes out.
 */
public final class GamblerListener implements Listener {

    /** Ordinary effect duration, in seconds. */
    private static final int EFFECT_SECONDS = 30;

    /** One row of the payout table. */
    private record Prize(int weight, String label, java.util.function.Consumer<Player> grant) {
    }

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Buttons placed by Gamblers — the only ones that pay. */
    private final Set<Location> tables = new HashSet<>();
    /** When each Gambler may roll again. */
    private final Map<UUID, Long> nextRoll = new HashMap<>();

    private final java.util.List<Prize> prizes = java.util.List.of(
            new Prize(124, "Speed", p -> effect(p, PotionEffectType.SPEED, 0)),
            new Prize(124, "Strength", p -> effect(p, PotionEffectType.STRENGTH, 0)),
            new Prize(124, "Regeneration", p -> effect(p, PotionEffectType.REGENERATION, 0)),
            new Prize(124, "a full stomach", p -> {
                p.setFoodLevel(20);
                p.setSaturation(10.0F);
            }),
            new Prize(124, "Slowness", p -> effect(p, PotionEffectType.SLOWNESS, 0)),
            new Prize(124, "Poison", p -> effect(p, PotionEffectType.POISON, 0)),
            new Prize(124, "Hunger", p -> effect(p, PotionEffectType.HUNGER, 1)),
            new Prize(124, "Weakness", p -> effect(p, PotionEffectType.WEAKNESS, 0)),
            new Prize(1, "FULL DIAMOND ARMOUR", GamblerListener::diamonds),
            new Prize(1, "DEATH", p -> p.setHealth(0.0D)));

    private final int totalWeight = prizes.stream().mapToInt(Prize::weight).sum();

    public GamblerListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset like every other kit's. */
    public void clearTables() {
        tables.clear();
        nextRoll.clear();
    }

    // ---------------------------------------------------------------- the table

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == GamblerKit.BUTTON
                && game.state().isLive()
                && kits.hasKit(event.getPlayer(), GamblerKit.ID)) {
            tables.add(event.getBlockPlaced().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        tables.remove(event.getBlock().getLocation());
    }

    // ---------------------------------------------------------------- the roll

    @EventHandler(ignoreCancelled = true)
    public void onPress(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getClickedBlock().getType() != GamblerKit.BUTTON
                || !tables.contains(event.getClickedBlock().getLocation())) {
            return;
        }
        Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(player, GamblerKit.ID)) {
            return; // someone else's finger buys nothing
        }

        long now = System.currentTimeMillis();
        Long until = nextRoll.get(player.getUniqueId());
        if (until != null && now < until) {
            player.sendActionBar(Component.text("The house needs "
                    + ((until - now) / 1000L + 1) + "s.", NamedTextColor.GOLD));
            return;
        }
        int cooldown = game.config().gamblerCooldownSeconds();
        nextRoll.put(player.getUniqueId(), now + cooldown * 1000L);
        CooldownBar.show(plugin, game, player, cooldown);

        roll(player);
    }

    private void roll(Player player) {
        int pick = ThreadLocalRandom.current().nextInt(totalWeight);
        for (Prize prize : prizes) {
            pick -= prize.weight();
            if (pick >= 0) {
                continue;
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0F, 1.4F);
            player.sendMessage(Component.text("The wager pays: ", NamedTextColor.GOLD)
                    .append(Component.text(prize.label(), NamedTextColor.AQUA)));
            if (prize.weight() == 1) {
                // A thousand-to-one deserves witnesses, whichever way it went.
                Msg.notice(player.getName() + " gambled and won " + prize.label() + "!");
            }
            prize.grant().accept(player);
            return;
        }
    }

    private static void effect(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, EFFECT_SECONDS * 20, amplifier));
    }

    /** The jackpot. Existing armour is dropped rather than deleted — winnings replace, on top. */
    private static void diamonds(Player player) {
        ItemStack[] worn = {new ItemStack(Material.DIAMOND_BOOTS),
                new ItemStack(Material.DIAMOND_LEGGINGS),
                new ItemStack(Material.DIAMOND_CHESTPLATE),
                new ItemStack(Material.DIAMOND_HELMET)};
        ItemStack[] current = player.getInventory().getArmorContents();
        for (ItemStack piece : current) {
            if (piece != null && !piece.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), piece);
            }
        }
        player.getInventory().setArmorContents(worn);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
    }
}
