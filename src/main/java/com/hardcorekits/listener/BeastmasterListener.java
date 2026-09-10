package com.hardcorekits.listener;

import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.BeastmasterKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The Beastmaster's pack.
 *
 * <p>Vanilla taming is a one-in-three roll per bone, which would make three eggs and four bones
 * a coin flip. So the interaction is intercepted and resolved by hand instead: the bone is
 * spent, the wolf is tamed, and that is that.
 *
 * <p>Once tamed, each wolf gets speed or regeneration for the rest of the match. Everything
 * after that — piling onto whoever the owner hits, going idle when the owner dies — is vanilla
 * pack behaviour and deliberately left alone.
 */
public final class BeastmasterListener implements Listener {

    /** Long enough to outlast any match; wolves keep the buff for good. */
    private static final int BUFF_DURATION_TICKS = 20 * 60 * 60;

    private final GameManager game;
    private final KitRegistry kits;

    public BeastmasterListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTameAttempt(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // fires once per hand otherwise
        }
        if (!(event.getRightClicked() instanceof Wolf wolf) || !game.state().isLive()) {
            return;
        }
        Player player = event.getPlayer();
        if (!kits.canUseAbility(player, BeastmasterKit.ID) || wolf.isTamed()) {
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.BONE) {
            return;
        }

        // Take over from vanilla so the one-in-three roll never happens.
        event.setCancelled(true);
        held.setAmount(held.getAmount() - 1);

        tame(wolf, player);
    }

    private void tame(Wolf wolf, Player owner) {
        wolf.setTamed(true);
        wolf.setOwner(owner);
        wolf.setAngry(false);
        wolf.setCollarColor(DyeColor.RED);

        boolean swift = ThreadLocalRandom.current().nextBoolean();
        PotionEffectType buff = swift ? PotionEffectType.SPEED : PotionEffectType.REGENERATION;
        wolf.addPotionEffect(new PotionEffect(buff, BUFF_DURATION_TICKS, 0, true, false));

        wolf.getWorld().spawnParticle(Particle.HEART, wolf.getLocation().add(0.0D, 1.0D, 0.0D),
                5, 0.4D, 0.4D, 0.4D);
        wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_WHINE, 1.0F, 1.0F);

        owner.sendMessage(Component.text("Wolf tamed, " + (swift ? "swift" : "hardy") + ".",
                NamedTextColor.GREEN));
    }
}
