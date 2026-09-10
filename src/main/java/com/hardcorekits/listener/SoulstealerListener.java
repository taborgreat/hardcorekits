package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.SoulstealerKit;
import com.hardcorekits.util.CooldownBar;
import com.hardcorekits.util.Msg;
import com.hardcorekits.util.Phases;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Soulstealer's second life.
 *
 * <p>This is the one kit allowed to interrupt the death pipeline. {@link DeathListener} asks
 * {@link #beginRevival} before eliminating anyone: a Soulstealer with their revival unspent is
 * let through death's door and back out of it — respawned at the spot they fell (not the map
 * centre like everyone else), invisible, on the hunt clock.
 *
 * <p>The hunt is two windows on the XP bar. Touch a player inside the first and the second
 * opens: visibility returns, a sword appears, and the prey must die — by the Soulstealer's
 * hand or anyone's — before the bar empties. The hunt's own swings land at forty percent.
 * Either clock running out collects the death that was deferred: an elimination like any
 * other, with its own line.
 *
 * <p>One revival per match, spent even if the hunt fails — spent, in fact, the moment it
 * begins.
 */
public final class SoulstealerListener implements Listener {

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Souls already used this match, spent at the moment of revival. */
    private final Set<UUID> spent = new HashSet<>();
    /** Where each pending revival should put the body back. */
    private final Map<UUID, Location> risingAt = new HashMap<>();
    /** Hunters in window one (no prey yet) or window two (prey marked). */
    private final Map<UUID, UUID> prey = new HashMap<>();
    private final Set<UUID> hunting = new HashSet<>();
    private final Map<UUID, BukkitTask> deadlines = new HashMap<>();

    public SoulstealerListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset like every other kit's. */
    public void clearState() {
        spent.clear();
        risingAt.clear();
        prey.clear();
        hunting.clear();
        deadlines.values().forEach(BukkitTask::cancel);
        deadlines.clear();
    }

    // ---------------------------------------------------------------- the door

    /**
     * Called by {@link DeathListener} for every death. Returns true when this death is
     * deferred — the caller then suppresses the elimination and lets the respawn happen.
     */
    public boolean beginRevival(Player victim) {
        if (!game.state().isLive() || !kits.hasKit(victim, SoulstealerKit.ID)
                || !spent.add(victim.getUniqueId())) {
            return false;
        }
        risingAt.put(victim.getUniqueId(), victim.getLocation().clone());
        return true;
    }

    /** Whether this hunter is inside the 40%-damage kill window. */
    public boolean isHunting(Player player) {
        return hunting.contains(player.getUniqueId()) || prey.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player soul = event.getPlayer();
        Location grave = risingAt.remove(soul.getUniqueId());
        if (grave == null) {
            return;
        }
        // HIGH: after DeathListener has pointed everyone else at the centre, the Soulstealer
        // is pointed back at their own grave instead.
        event.setRespawnLocation(grave);

        hunting.add(soul.getUniqueId());
        int huntSeconds = game.config().soulstealerHuntSeconds();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!soul.isOnline()) {
                return;
            }
            soul.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    huntSeconds * 20, 0, true, false));
            soul.sendMessage(Component.text("Your soul lingers. Touch someone within "
                    + huntSeconds + " seconds.", NamedTextColor.DARK_PURPLE));
            soul.playSound(grave, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7F, 1.4F);
            CooldownBar.show(plugin, game, soul, huntSeconds);
        });

        deadlines.put(soul.getUniqueId(), Phases.delayed(plugin, huntSeconds, () -> {
            if (hunting.remove(soul.getUniqueId())) {
                collect(soul, " found nobody to haunt, and died for good.");
            }
        }));
    }

    // ---------------------------------------------------------------- the hunt

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwing(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player soul)
                || !(event.getEntity() instanceof Player victim)) {
            return;
        }

        // Window one: the touch that marks the prey.
        if (hunting.remove(soul.getUniqueId())) {
            cancelDeadline(soul.getUniqueId());
            prey.put(soul.getUniqueId(), victim.getUniqueId());
            soul.removePotionEffect(PotionEffectType.INVISIBILITY);

            int killSeconds = game.config().soulstealerKillSeconds();
            soul.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
            soul.sendMessage(Component.text("Prey marked: " + victim.getName() + ". Kill them "
                    + "within " + killSeconds + " seconds!", NamedTextColor.DARK_PURPLE));
            victim.sendMessage(Component.text("A Soulstealer hunts you!", NamedTextColor.RED));
            CooldownBar.show(plugin, game, soul, killSeconds);

            deadlines.put(soul.getUniqueId(), Phases.delayed(plugin, killSeconds, () -> {
                UUID target = prey.remove(soul.getUniqueId());
                if (target == null) {
                    return;
                }
                if (game.alive().contains(target)) {
                    collect(soul, " failed the hunt, and died for good.");
                } else {
                    succeed(soul);
                }
            }));
        }

        // Both windows: the deferred death fights at reduced strength.
        if (isHunting(soul) || prey.containsKey(soul.getUniqueId())) {
            event.setDamage(event.getDamage() * game.config().soulstealerDamageMultiplier());
        }
    }

    /** The prey dying — to anyone, by anything — settles the hunt immediately. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreyDeath(PlayerDeathEvent event) {
        UUID dead = event.getEntity().getUniqueId();
        for (Map.Entry<UUID, UUID> hunt : new HashMap<>(prey).entrySet()) {
            if (!hunt.getValue().equals(dead)) {
                continue;
            }
            prey.remove(hunt.getKey());
            cancelDeadline(hunt.getKey());
            Player soul = Bukkit.getPlayer(hunt.getKey());
            if (soul != null && soul.isOnline()) {
                succeed(soul);
            }
        }
    }

    /** A hunter who logs out mid-hunt just dies — the quit path eliminates them normally. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        hunting.remove(uuid);
        prey.remove(uuid);
        cancelDeadline(uuid);
    }

    // ---------------------------------------------------------------- verdicts

    private void succeed(Player soul) {
        game.showKills(soul);
        soul.sendMessage(Component.text("The soul is yours. You live again.",
                NamedTextColor.DARK_PURPLE));
        Msg.kill(soul.getName() + " stole a soul, and lives again!");
        soul.playSound(soul.getLocation(), Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 1.0F, 0.8F);
    }

    private void collect(Player soul, String line) {
        game.showKills(soul);
        game.eliminate(soul,
                Msg.killLine(soul.getName() + line),
                Component.text("Your soul found no purchase.", NamedTextColor.RED));
    }

    private void cancelDeadline(UUID uuid) {
        BukkitTask task = deadlines.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }
}
