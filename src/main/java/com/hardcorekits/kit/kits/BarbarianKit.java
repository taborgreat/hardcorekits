package com.hardcorekits.kit.kits;

import com.hardcorekits.game.GameConfig;
import com.hardcorekits.game.GameConfig.SwordTier;
import com.hardcorekits.kit.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tyrfing, the sword that grows with its owner.
 *
 * <p>The Barbarian starts with a wooden sword and nothing else. Every point of experience they
 * earn is remembered — mob drops, ore, bottles, furnace output, all of it — and at fixed totals
 * the sword is reforged in place, wood to stone to iron to diamond and on into sharpness.
 * Kills are the fast lane rather than the only lane: the first is worth a bounty of XP and
 * every kill after that is worth more than the last, so a Barbarian who opens with two kills is
 * carrying iron while a quiet one is still grinding towards stone.
 *
 * <p>What matters is XP <i>earned</i>, not the XP bar being carried — spending levels on the
 * feast enchanting table never costs an edge.
 *
 * <p>The sword is unbreakable. The kit is famous for its sword appearing to break at the low
 * tiers, which was always a durability artefact rather than a feature.
 *
 * <p>The levelling lives in {@link com.hardcorekits.listener.BarbarianListener}.
 */
public final class BarbarianKit implements Kit {

    public static final String ID = "barbarian";

    /**
     * Stamped into Tyrfing's item data with the owner's UUID.
     *
     * <p>Two things fall out of that. The listener can find the sword wherever it has been
     * moved to in the inventory, and a Tyrfing looted off a dead Barbarian stays frozen at the
     * tier it died on — it is a good sword, but it is not <i>your</i> sword and it will never
     * grow again.
     *
     * <p>Namespace is spelled out rather than taken from the plugin because the kit is built
     * before there is a plugin handle to hand it; it resolves to the same key either way.
     */
    private static final NamespacedKey OWNER = new NamespacedKey("hardcoregames", "tyrfing-owner");

    private static final Component NAME =
            Component.text("Tyrfing", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false);

    private final GameConfig config;

    public BarbarianKit(GameConfig config) {
        this.config = config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Barbarian";
    }

    @Override
    public String description() {
        return "Start with Tyrfing, a wooden sword that reforges itself as you earn XP. Kills are worth the most, "
                + "and each one more than the last.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(forge(config.barbarianTiers(), 0, player));
    }

    // ---------------------------------------------------------------- the sword

    /** Tyrfing at the given rung of the ladder, stamped for its owner. */
    public static ItemStack forge(List<SwordTier> tiers, int index, Player owner) {
        SwordTier tier = tiers.get(index);
        SwordTier next = index + 1 < tiers.size() ? tiers.get(index + 1) : null;

        ItemStack sword = new ItemStack(tier.material());
        ItemMeta meta = sword.getItemMeta();
        meta.displayName(NAME);

        List<Component> lore = new ArrayList<>();
        lore.add(lore(edgeOf(tier), NamedTextColor.GRAY));
        lore.add(lore(next == null
                ? "Fully forged."
                : "Next edge at " + next.xp() + " XP earned.", NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        if (tier.sharpness() > 0) {
            meta.addEnchant(Enchantment.SHARPNESS, tier.sharpness(), true);
        }

        // Never let the sword wear out — the classic kit's "it broke but it didn't" bug was
        // durability, and there is nothing to gain by reproducing it.
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        meta.getPersistentDataContainer()
                .set(OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
        sword.setItemMeta(meta);
        return sword;
    }

    /** True only for the sword this player was handed at the start of the match. */
    public static boolean isTyrfingOf(ItemStack stack, Player owner) {
        if (stack == null) {
            return false;
        }
        String stamped = stack.getPersistentDataContainer().get(OWNER, PersistentDataType.STRING);
        return owner.getUniqueId().toString().equals(stamped);
    }

    /** "Iron edge" / "Diamond edge, sharpness II" — used in the lore and the upgrade line. */
    public static String edgeOf(SwordTier tier) {
        String material = tier.material().name().toLowerCase(Locale.ROOT).replace("_sword", "").replace('_', ' ');
        String edge = Character.toUpperCase(material.charAt(0)) + material.substring(1) + " edge";
        return tier.sharpness() > 0 ? edge + ", sharpness " + roman(tier.sharpness()) : edge;
    }

    private static Component lore(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }
}
