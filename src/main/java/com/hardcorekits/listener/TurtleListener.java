package com.hardcorekits.listener;

import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.TurtleKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Set;

/**
 * The Turtle's shell.
 *
 * <p>Two halves of one bargain, and they are deliberately in separate handlers at different
 * priorities: the attack ban runs first, so a crouching Turtle's swing is cancelled before
 * anything bothers computing what the victim would have taken.
 *
 * <p>The shell caps damage rather than replacing it — a hit softer than the cap still lands
 * softly, so chip damage is not quietly upgraded into a full heart.
 */
public final class TurtleListener implements Listener {

    /**
     * The shell, made visible: a full XP bar while crouched, back to the kill count the
     * moment the crouch releases. Fill only, level untouched — same contract as every other
     * kit's use of the bar.
     */
    @EventHandler
    public void onCrouch(PlayerToggleSneakEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.hasKit(player, com.hardcorekits.kit.kits.TurtleKit.ID)) {
            return;
        }
        if (event.isSneaking()) {
            player.setExp(0.999F);
        } else {
            game.showKills(player);
        }
    }


    /**
     * Damage the shell does not stop.
     *
     * <p>Explosions are exempt because the kit says so — a Demoman trap kills a Turtle like
     * anyone else. The rest are exempt because a defensive crouch should never be a way to
     * survive something that is not an attack at all: the border in particular would otherwise
     * become a safe place to sit.
     */
    private static final Set<EntityDamageEvent.DamageCause> UNSTOPPABLE = Set.of(
            EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
            EntityDamageEvent.DamageCause.VOID,
            EntityDamageEvent.DamageCause.SUICIDE,
            EntityDamageEvent.DamageCause.CUSTOM);

    private final GameManager game;
    private final KitRegistry kits;

    public TurtleListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    /**
     * A crouching Turtle cannot swing.
     *
     * <p>LOW so the cancel lands before the shell handler below ever looks at the event.
     * Only direct melee is stopped — an arrow already in flight is not a swing, and its
     * shooter's stance by the time it lands is meaningless.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !game.state().isLive()) {
            return;
        }
        if (!attacker.isSneaking() || !kits.hasKit(attacker, TurtleKit.ID)) {
            return;
        }

        event.setCancelled(true);
        attacker.sendActionBar(Component.text("You cannot attack while crouched.",
                NamedTextColor.GRAY));
    }

    /** The shell itself. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !game.state().isLive()) {
            return;
        }
        if (!player.isSneaking() || !kits.hasKit(player, TurtleKit.ID)) {
            return;
        }
        if (UNSTOPPABLE.contains(event.getCause())) {
            return;
        }

        double cap = player.isBlocking()
                ? game.config().turtleBlockingDamage()
                : game.config().turtleCrouchDamage();

        // A cap, not a floor: a weaker hit still only does what it was going to.
        if (event.getDamage() > cap) {
            event.setDamage(cap);
        }
    }
}
