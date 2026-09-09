package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.HadesKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hades' army.
 *
 * <p>A minion is the mob it always was, wearing a name — its health, its damage and its death
 * are untouched, which is the kit's published counter. What changes is its loyalty: it never
 * targets its owner or a fellow minion, it piles onto whoever its owner fights (both
 * directions), and between fights it walks to heel on Paper's pathfinder, which is what lets
 * even a cow be drafted — anything with legs can follow, and anything with an attack of its
 * own will use it.
 *
 * <p>Losing the owner disbands the army rather than deleting it: names come off, tags come
 * off, and the mobs go back to being weather.
 */
public final class HadesListener implements Listener {

    private static final long FOLLOW_SWEEP_TICKS = 30L;
    /** Closer than this and a minion stands at ease instead of pathing. */
    private static final double HEEL_DISTANCE = 6.0D;
    private static final double FOLLOW_SPEED = 1.2D;

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Owner -> their minions. */
    private final Map<UUID, Set<UUID>> armies = new HashMap<>();
    /** Minion -> owner, for the fast checks in the target handler. */
    private final Map<UUID, UUID> owners = new HashMap<>();

    public HadesListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Started once at enable; walks every army to heel a few times a second. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::followSweep,
                FOLLOW_SWEEP_TICKS, FOLLOW_SWEEP_TICKS);
    }

    /** Disbands every army. Wired into the game's reset hooks. */
    public void clearMinions() {
        for (UUID minionId : owners.keySet()) {
            Entity entity = Bukkit.getEntity(minionId);
            if (entity != null) {
                entity.customName(null);
                entity.setCustomNameVisible(false);
            }
        }
        armies.clear();
        owners.clear();
    }

    // ---------------------------------------------------------------- recruitment

    @EventHandler(ignoreCancelled = true)
    public void onTame(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player hades = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(hades, HadesKit.ID)) {
            return;
        }
        if (hades.getInventory().getItemInMainHand().getType() != HadesKit.WAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Mob mob) || owners.containsKey(mob.getUniqueId())) {
            return;
        }

        Set<UUID> army = armies.computeIfAbsent(hades.getUniqueId(), ignored -> new HashSet<>());
        if (army.size() >= game.config().hadesMaxMinions()) {
            hades.sendActionBar(Component.text("Your army is full ("
                    + game.config().hadesMaxMinions() + ").", NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);
        army.add(mob.getUniqueId());
        owners.put(mob.getUniqueId(), hades.getUniqueId());

        mob.customName(Component.text(hades.getName() + "'s Minion", NamedTextColor.DARK_PURPLE));
        mob.setCustomNameVisible(true);
        mob.setTarget(null);
        mob.setRemoveWhenFarAway(false);

        hades.sendActionBar(Component.text("Minion recruited (" + army.size() + "/"
                + game.config().hadesMaxMinions() + ").", NamedTextColor.LIGHT_PURPLE));
        hades.playSound(mob.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.7F, 1.2F);
    }

    // ---------------------------------------------------------------- loyalty

    /**
     * A minion's damage never reaches its owner — the hard guarantee behind the target
     * cancellation below, because targeting is not the only way a mob hurts you: a drafted
     * creeper that detonates for any reason, at anything, must not take Hades with it.
     * LOWEST, so nothing downstream even sees the hit.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player owner)) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (owner.getUniqueId().equals(owners.get(damager.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    /** A minion never turns on its owner, or on the rest of the army. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        UUID owner = owners.get(event.getEntity().getUniqueId());
        if (owner == null || event.getTarget() == null) {
            return;
        }
        UUID target = event.getTarget().getUniqueId();
        if (target.equals(owner) || owner.equals(owners.get(target))) {
            event.setCancelled(true);
        }
    }

    /** The owner's fights are the army's fights, in both directions. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerCombat(EntityDamageByEntityEvent event) {
        if (!game.state().isLive()) {
            return;
        }
        if (event.getDamager() instanceof Player owner
                && event.getEntity() instanceof LivingEntity victim) {
            rally(owner, victim);
        }
        if (event.getEntity() instanceof Player owner
                && event.getDamager() instanceof LivingEntity attacker) {
            rally(owner, attacker);
        }
    }

    private void rally(Player owner, LivingEntity quarry) {
        Set<UUID> army = armies.get(owner.getUniqueId());
        if (army == null || quarry.getUniqueId().equals(owner.getUniqueId())
                || owners.containsKey(quarry.getUniqueId())) {
            return;
        }
        for (UUID minionId : army) {
            if (Bukkit.getEntity(minionId) instanceof Mob minion && minion.isValid()) {
                minion.setTarget(quarry);
            }
        }
    }

    // ---------------------------------------------------------------- upkeep

    private void followSweep() {
        for (Iterator<Map.Entry<UUID, Set<UUID>>> it = armies.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Set<UUID>> entry = it.next();
            Player owner = Bukkit.getPlayer(entry.getKey());

            // An absent or eliminated owner disbands the army on the spot.
            if (owner == null || !owner.isOnline() || !game.isAlive(owner)) {
                for (UUID minionId : entry.getValue()) {
                    Entity minion = Bukkit.getEntity(minionId);
                    if (minion != null) {
                        minion.customName(null);
                        minion.setCustomNameVisible(false);
                    }
                    owners.remove(minionId);
                }
                it.remove();
                continue;
            }

            for (Iterator<UUID> minions = entry.getValue().iterator(); minions.hasNext(); ) {
                UUID minionId = minions.next();
                Entity entity = Bukkit.getEntity(minionId);
                if (!(entity instanceof Mob minion) || !minion.isValid()) {
                    minions.remove();
                    owners.remove(minionId);
                    continue;
                }
                // Busy minions fight; idle ones walk to heel.
                if (minion.getTarget() == null
                        && minion.getWorld().equals(owner.getWorld())
                        && minion.getLocation().distance(owner.getLocation()) > HEEL_DISTANCE) {
                    minion.getPathfinder().moveTo(owner.getLocation(), FOLLOW_SPEED);
                }
            }
        }
    }

    /** Book-keeping only — the death itself is vanilla's, which is the kit's counter. */
    @EventHandler
    public void onMinionDeath(EntityDeathEvent event) {
        UUID minionId = event.getEntity().getUniqueId();
        UUID owner = owners.remove(minionId);
        if (owner != null) {
            Set<UUID> army = armies.get(owner);
            if (army != null) {
                army.remove(minionId);
            }
        }
    }
}
