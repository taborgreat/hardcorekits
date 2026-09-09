package gg.hungergames.listener;

import gg.hungergames.game.GameConfig;
import gg.hungergames.game.GameConfig.SwordTier;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.BarbarianKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tyrfing's levelling.
 *
 * <p>Two things feed the sword, and the whole point of the kit is that it is two rather than
 * one. Any XP the Barbarian earns counts — mobs, ore, bottles, fishing — so a Barbarian who
 * never finds a fight still slowly out-arms the field. Kills count for far more, and escalate:
 * the first is worth {@code kill-xp}, and every kill after that is worth {@code kill-xp-step}
 * more than the one before, which is what makes an early snowball feel like one.
 *
 * <p>The running total is kept here rather than read off the player, because the player's XP
 * bar is spendable — an enchanting table at the feast would otherwise melt the sword back down.
 *
 * <p>Reforging replaces the sword in the slot it is sitting in. If the Barbarian has managed to
 * lose it — dropped, or in a chest — the upgrade is simply skipped; the XP still counts, so
 * getting it back and earning one more rung catches the sword up.
 */
public final class BarbarianListener implements Listener {

    private final GameManager game;
    private final KitRegistry kits;

    /** Total XP earned this match, per Barbarian. Never spent, only added to. */
    private final Map<UUID, Integer> earned = new HashMap<>();
    /** Player kills this match, per Barbarian — the bounty grows with it. */
    private final Map<UUID, Integer> kills = new HashMap<>();

    public BarbarianListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    /** Dropped on reset, like any other per-match state. */
    public void clearProgress() {
        earned.clear();
        kills.clear();
    }

    // ---------------------------------------------------------------- xp sources

    /** Ambient XP: mobs, ore, bottles, furnaces, fishing. */
    @EventHandler
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (event.getAmount() <= 0
                || !game.state().isLive() || !kits.hasKit(player, BarbarianKit.ID)) {
            return;
        }
        gain(player, event.getAmount());
    }

    /** A kill is worth a bounty on top of whatever the corpse drops, and it escalates. */
    @EventHandler
    public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())
                || !game.state().isLive() || !kits.hasKit(killer, BarbarianKit.ID)) {
            return;
        }

        GameConfig config = game.config();
        int count = kills.merge(killer.getUniqueId(), 1, Integer::sum);
        int bounty = config.barbarianKillXp() + config.barbarianKillXpStep() * (count - 1);
        if (bounty <= 0) {
            return;
        }

        // Counted, not handed over: the XP bar is the kill counter now, so paying the bounty
        // in real XP would only scribble on it. Tyrfing keeps its own ledger regardless.

        killer.sendMessage(Component.text("Tyrfing drinks deep. Kill " + count
                + " is worth " + bounty + " XP.", NamedTextColor.GOLD));
        gain(killer, bounty);
    }

    /**
     * A mob is worth a fraction of a player, on the same ledger.
     *
     * <p>Seven mobs to a player kill is the exchange rate, which makes hunting a real way to
     * grow the sword rather than a rounding error — a Barbarian who never finds a fight can
     * still arrive at the feast holding iron. It stays the slow lane, because player kills
     * escalate and mobs do not: by the fourth kill one player is worth two dozen mobs.
     *
     * <p>{@code PlayerDeathEvent} extends this one, so players are handed straight back to the
     * escalating bounty rather than being paid twice.
     */
    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null || !game.state().isLive() || !kits.hasKit(killer, BarbarianKit.ID)) {
            return;
        }
        int bounty = game.config().barbarianMobKillXp();
        if (bounty > 0) {
            gain(killer, bounty);
        }
    }

    // ---------------------------------------------------------------- levelling

    private void gain(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int before = earned.getOrDefault(uuid, 0);
        int after = before + amount;
        earned.put(uuid, after);

        List<SwordTier> tiers = game.config().barbarianTiers();
        int reached = tierIndex(tiers, after);
        if (reached > tierIndex(tiers, before)) {
            reforge(player, tiers, reached);
        }
    }

    /** The highest rung whose threshold has been paid for. */
    private static int tierIndex(List<SwordTier> tiers, int xp) {
        int index = 0;
        for (int i = 1; i < tiers.size(); i++) {
            if (xp >= tiers.get(i).xp()) {
                index = i;
            }
        }
        return index;
    }

    /** Swaps the sword for its next form, in whatever slot it is sitting in. */
    private void reforge(Player player, List<SwordTier> tiers, int index) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean reforged = false;

        for (int slot = 0; slot < contents.length; slot++) {
            if (!BarbarianKit.isTyrfingOf(contents[slot], player)) {
                continue;
            }
            inventory.setItem(slot, BarbarianKit.forge(tiers, index, player));
            reforged = true;
        }

        if (!reforged) {
            return; // they are not carrying it — the XP still counts for next time
        }

        player.sendMessage(Component.text("Tyrfing reforged. " + BarbarianKit.edgeOf(tiers.get(index)) + ".",
                NamedTextColor.GOLD));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0F, 1.2F);
    }
}
