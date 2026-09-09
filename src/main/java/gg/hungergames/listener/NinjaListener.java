package gg.hungergames.listener;

import gg.hungergames.game.GameConfig;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.NinjaKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Ninja's chase.
 *
 * <p>Two halves: hitting a player writes down who and when, and crouching spends that note to
 * teleport. The note expires on its own after a few seconds, so a Ninja who loses their target
 * has to land another hit rather than keeping a permanent leash.
 *
 * <p>Crouching is something everyone does constantly, so the mark is checked before anything
 * else. A Ninja with nothing marked crouches in silence, and only hears about cooldowns when
 * they actually had somewhere to go.
 */
public final class NinjaListener implements Listener {

    private final GameManager game;
    private final KitRegistry kits;

    /** Who each Ninja last hit, and when. */
    private final Map<UUID, Mark> marks = new HashMap<>();
    /** When each Ninja may teleport again. */
    private final Map<UUID, Long> readyAt = new HashMap<>();

    private record Mark(UUID target, long at) {
    }

    public NinjaListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset. */
    public void clearMarks() {
        marks.clear();
        readyAt.clear();
    }

    // ---------------------------------------------------------------- the mark

    /**
     * Any hit that lands marks its victim, arrows included.
     *
     * <p>MONITOR and ignoreCancelled: a hit that was blocked never happened, and this never
     * changes the outcome of the fight it is watching.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || attacker.equals(victim)
                || !kits.canUseAbility(attacker, NinjaKit.ID)) {
            return;
        }
        marks.put(attacker.getUniqueId(), new Mark(victim.getUniqueId(), System.currentTimeMillis()));
    }

    // ---------------------------------------------------------------- the jump

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return; // standing back up is not a cast
        }
        Player ninja = event.getPlayer();

        // Nothing marked means an ordinary crouch. Checked first so a Ninja with no target is
        // never told anything at all — including during the grace period, when they cannot
        // have marked anyone.
        Player target = markedTarget(ninja);
        if (target == null || !kits.canUseAbility(ninja, NinjaKit.ID)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long ready = readyAt.get(ninja.getUniqueId());
        if (ready != null && now < ready) {
            long remaining = (long) Math.ceil((ready - now) / 1000.0D);
            ninja.sendActionBar(Component.text("Another jump in " + remaining + "s.",
                    NamedTextColor.DARK_GRAY));
            return;
        }

        jumpTo(ninja, target);
        readyAt.put(ninja.getUniqueId(),
                now + game.config().ninjaCooldownSeconds() * 1000L);
    }

    /** The player this Ninja hit recently enough, or null if there is nobody to jump to. */
    private Player markedTarget(Player ninja) {
        Mark mark = marks.get(ninja.getUniqueId());
        if (mark == null) {
            return null;
        }
        GameConfig config = game.config();
        if (System.currentTimeMillis() - mark.at() > config.ninjaMarkSeconds() * 1000L) {
            marks.remove(ninja.getUniqueId());
            return null;
        }

        Player target = Bukkit.getPlayer(mark.target());
        if (target == null || !target.isOnline() || !game.isAlive(target)) {
            marks.remove(ninja.getUniqueId());
            return null;
        }
        return target;
    }

    private void jumpTo(Player ninja, Player target) {
        Location from = ninja.getLocation();
        Location to = target.getLocation();

        // Cleared so a jump taken while falling, or one that lands high, is not paid for on
        // arrival. Being knocked off a tower and coming straight back is the point of the kit.
        ninja.setFallDistance(0.0F);
        ninja.teleport(to);

        from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.2F);
        to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.2F);
        target.sendMessage(Component.text(ninja.getName() + " appears behind you.",
                NamedTextColor.DARK_GRAY));
    }

    /** The player responsible, following projectiles back to whoever fired them. */
    private Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
