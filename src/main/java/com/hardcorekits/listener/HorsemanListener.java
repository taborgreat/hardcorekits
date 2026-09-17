package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.HorsemanKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Horseman's horse.
 *
 * <p>Hatching is taken over from vanilla so the horse arrives finished: tamed to the player,
 * saddled, armored, with the kit's stats rather than a random roll, and with the player
 * already on its back. Vanilla would spawn a wild one and leave the taming minigame.
 *
 * <p>The horse is the rider's alone. A tamed horse is otherwise anyone's to ride, which would
 * make killing a Horseman a way to acquire a horse; the mount event says no to everyone else.
 *
 * <p>Deliberately NOT gated behind the ability lock: the horse's job is getting away from the
 * drop, so it is allowed the moment the match is live, invincibility included. One horse per
 * game. When it dies the kit is spent.
 */
public final class HorsemanListener implements Listener {

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Owner -> their horse, and the reverse, for the two lookups the events need. */
    private final Map<UUID, UUID> horseOf = new HashMap<>();
    private final Map<UUID, UUID> ownerOf = new HashMap<>();

    public HorsemanListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Wiped between matches. */
    public void clear() {
        horseOf.clear();
        ownerOf.clear();
    }

    // ---------------------------------------------------------------- hatching

    @EventHandler
    public void onUseEgg(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.HORSE_SPAWN_EGG) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.hasKit(player, HorsemanKit.ID)) {
            return;
        }
        // Vanilla must not hatch a wild one alongside ours.
        event.setCancelled(true);

        Horse existing = horse(player);
        if (existing != null) {
            player.sendActionBar(Component.text("You already have a horse.", NamedTextColor.GRAY));
            return;
        }
        item.setAmount(item.getAmount() - 1);
        hatch(player);
    }

    private void hatch(Player owner) {
        Horse horse = owner.getWorld().spawn(owner.getLocation(), Horse.class, h -> {
            h.setAdult();
            h.setTamed(true);
            h.setOwner(owner);
            h.setDomestication(h.getMaxDomestication());
            h.setColor(Horse.Color.values()[ThreadLocalRandom.current().nextInt(Horse.Color.values().length)]);
            h.setStyle(Horse.Style.values()[ThreadLocalRandom.current().nextInt(Horse.Style.values().length)]);
            h.customName(Component.text(owner.getName() + "'s horse", NamedTextColor.GOLD));
            h.setCustomNameVisible(true);
        });
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        horse.getInventory().setArmor(new ItemStack(Material.IRON_HORSE_ARMOR));

        set(horse, Attribute.MOVEMENT_SPEED, game.config().horsemanSpeed());
        set(horse, Attribute.JUMP_STRENGTH, game.config().horsemanJumpStrength());
        set(horse, Attribute.MAX_HEALTH, game.config().horsemanHealth());
        horse.setHealth(game.config().horsemanHealth());

        horseOf.put(owner.getUniqueId(), horse.getUniqueId());
        ownerOf.put(horse.getUniqueId(), owner.getUniqueId());

        horse.addPassenger(owner);
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_HORSE_ANGRY, 1.0F, 1.0F);
        owner.sendMessage(Component.text("Your horse is ready. Only you can ride it, and every "
                + "kill heals it.", NamedTextColor.GOLD));
    }

    private static void set(Horse horse, Attribute attribute, double value) {
        AttributeInstance instance = horse.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** The owner's horse if it is still alive and loaded, else null. */
    private Horse horse(Player owner) {
        UUID id = horseOf.get(owner.getUniqueId());
        if (id == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(id);
        return entity instanceof Horse horse && horse.isValid() ? horse : null;
    }

    // ---------------------------------------------------------------- whose horse it is

    /** Nobody but the owner gets on. LOWEST, so it is decided before anything else looks. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        UUID owner = ownerOf.get(event.getMount().getUniqueId());
        if (owner == null || owner.equals(event.getEntity().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (event.getEntity() instanceof Player rider) {
            rider.sendActionBar(Component.text("Not your horse.", NamedTextColor.GRAY));
        }
    }

    // ---------------------------------------------------------------- the heal, and the end

    /** A kill by the rider puts the horse back to full. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !game.state().isLive() || !kits.hasKit(killer, HorsemanKit.ID)) {
            return;
        }
        Horse horse = horse(killer);
        if (horse == null) {
            return;
        }
        AttributeInstance max = horse.getAttribute(Attribute.MAX_HEALTH);
        horse.setHealth(max == null ? game.config().horsemanHealth() : max.getValue());
        killer.sendActionBar(Component.text("Your horse is healed.", NamedTextColor.GOLD));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHorseDeath(EntityDeathEvent event) {
        UUID ownerId = ownerOf.remove(event.getEntity().getUniqueId());
        if (ownerId == null) {
            return;
        }
        horseOf.remove(ownerId);
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            owner.sendMessage(Component.text("Your horse died.", NamedTextColor.RED));
        }
    }
}
