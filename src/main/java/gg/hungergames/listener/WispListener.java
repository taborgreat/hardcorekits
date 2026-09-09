package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.WispKit;
import gg.hungergames.util.Interact;
import gg.hungergames.util.Phases;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Wisp's decoys.
 *
 * <p>Each magma cream throws a handful of copies onto the field — mannequins wearing the
 * Wisp's own <em>skin</em>, name and armour, each gliding off in its own direction. A
 * mannequin has no AI, so the wandering is driven by hand: a little push along a drifting
 * heading a few times a second, faced the way it moves. They never swing, which is the
 * published counter: the one that fights back is real.
 *
 * <p>Killing one is a small punishment and no reward: a heart of damage to whoever landed the
 * blow, no drops, no XP. They expire on their own so a cream spent early does not leave the
 * map haunted by copies of somebody for the rest of the hour.
 */
public final class WispListener implements Listener {

    /** Marks a decoy for the death/target handlers. */
    private static final NamespacedKey CLONE_KEY = new NamespacedKey("hungergames", "wisp_clone");

    /** The scatter shove. Direction is random per clone; this is how hard. */
    private static final double SCATTER_SPEED = 0.4D;

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Decoys currently on the field, so a reset can clear the stage. */
    private final Set<UUID> clones = new HashSet<>();
    /** Decoy -> the Wisp who cast it, so popping your own is free. */
    private final Map<UUID, UUID> cloneOwners = new HashMap<>();

    public WispListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Removes every decoy still standing. Wired into the game's reset hooks. */
    public void clearClones(){
        for (UUID uuid : clones) {
            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        clones.clear();
        cloneOwners.clear();
    }

    // ---------------------------------------------------------------- the cream

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != WispKit.CREAM) {
            return;
        }
        Player wisp = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(wisp, WispKit.ID)) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND
                && wisp.getInventory().getItemInMainHand().getType() == WispKit.CREAM) {
            return; // one click, one cream
        }
        if (action == Action.RIGHT_CLICK_BLOCK && Interact.opensBlock(event)) {
            return;
        }

        event.setCancelled(true);
        consume(wisp, event.getHand(), item);

        int count = game.config().wispClonesPerCream();
        for (int i = 0; i < count; i++) {
            spawnClone(wisp);
        }
        wisp.getWorld().playSound(wisp.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE,
                1.0F, 1.2F);
    }

    private void spawnClone(Player wisp) {
        Location at = wisp.getLocation();
        Mannequin clone = at.getWorld().spawn(at, Mannequin.class, mannequin -> {
            // The player's actual profile: their skin, on their silhouette. This is the whole
            // reason these are mannequins and not mobs in a costume.
            mannequin.setProfile(ResolvableProfile.resolvableProfile(wisp.getPlayerProfile()));
            mannequin.setImmovable(false);
            mannequin.customName(Component.text(wisp.getName()));
            mannequin.setCustomNameVisible(true);
            mannequin.setDescription(Component.empty());
            mannequin.setSilent(true);
            mannequin.getPersistentDataContainer().set(CLONE_KEY, PersistentDataType.BYTE,
                    (byte) 1);

            EntityEquipment gear = mannequin.getEquipment();
            PlayerInventory wardrobe = wisp.getInventory();
            gear.setHelmet(copy(wardrobe.getHelmet()));
            gear.setChestplate(copy(wardrobe.getChestplate()));
            gear.setLeggings(copy(wardrobe.getLeggings()));
            gear.setBoots(copy(wardrobe.getBoots()));
            gear.setItemInMainHand(new ItemStack(WispKit.CREAM));
            // No drop-chance calls here: a mannequin is a LivingEntity but not a Mob, and
            // setDropChance throws for non-mobs. "Nothing a decoy wears is ever loot" is
            // enforced where it dies instead — the death handler clears every drop.
        });

        UUID uuid = clone.getUniqueId();
        clones.add(uuid);
        cloneOwners.put(uuid, wisp.getUniqueId());
        drift(clone);
        Phases.delayed(plugin, game.config().wispCloneLifetimeSeconds(), () -> {
            clones.remove(uuid);
            cloneOwners.remove(uuid);
            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        });
    }

    /**
     * Hand-driven wandering: a push along a slowly drifting heading, faced the way it moves.
     * Ends itself the moment the mannequin is gone, killed or expired alike.
     */
    private void drift(Mannequin clone) {
        new BukkitRunnable() {
            private double heading = ThreadLocalRandom.current().nextDouble() * 2.0D * Math.PI;

            @Override
            public void run() {
                if (!clone.isValid() || !clones.contains(clone.getUniqueId())) {
                    cancel();
                    return;
                }
                heading += ThreadLocalRandom.current().nextDouble(-0.6D, 0.6D);
                Vector push = new Vector(Math.cos(heading) * SCATTER_SPEED,
                        clone.getVelocity().getY(), Math.sin(heading) * SCATTER_SPEED);
                clone.setVelocity(push);
                clone.setRotation((float) Math.toDegrees(heading) - 90.0F, 0.0F);
            }
        }.runTaskTimer(plugin, 1L, 8L);
    }

    private static ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }

    /** Takes the one cream that was used, leaving the rest of the stack alone. */
    private void consume(Player player, EquipmentSlot hand, ItemStack creams) {
        ItemStack left = creams.clone();
        left.setAmount(left.getAmount() - 1);
        ItemStack remaining = left.getAmount() <= 0 ? null : left;
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(remaining);
        } else {
            player.getInventory().setItemInMainHand(remaining);
        }
    }

    // ---------------------------------------------------------------- being a decoy

    /** A decoy is an illusion: any hit at all, from anyone or anything, bursts it. */
    @EventHandler(ignoreCancelled = true)
    public void onCloneHit(EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(CLONE_KEY,
                PersistentDataType.BYTE)) {
            event.setDamage(1000.0D);
        }
    }

    /**
     * Killing a decoy costs a heart and pays nothing — unless the killer is the Wisp who cast
     * it, who may clear their own stage for free.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(CLONE_KEY,
                PersistentDataType.BYTE)) {
            return;
        }
        UUID uuid = event.getEntity().getUniqueId();
        clones.remove(uuid);
        UUID owner = cloneOwners.remove(uuid);
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player killer = event.getEntity().getKiller();
        if (killer != null && game.state().isLive()
                && !killer.getUniqueId().equals(owner)) {
            killer.damage(game.config().wispKillerDamage());
        }
    }
}
