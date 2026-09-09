package gg.hungergames.listener;

import gg.hungergames.game.GameConfig;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.KangarooKit;
import gg.hungergames.util.Interact;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Kangaroo's hop, and the landing it earns by fighting.
 *
 * <p>Two rules, deliberately independent: the rocket moves you, and hitting someone protects
 * you from the ground. Chaining them — hit, hop, drop on top of them — is the kit.
 *
 * <p>The rocket is never consumed. Its limit is the vanilla item cooldown, which the client
 * already draws on the item, so there is no bookkeeping and no message needed to say when the
 * next hop is ready.
 */
public final class KangarooListener implements Listener {

    private final GameManager game;
    private final KitRegistry kits;

    /** When each Kangaroo's fall-damage immunity runs out. */
    private final Map<UUID, Long> softLandingUntil = new HashMap<>();

    public KangarooListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset like every other kit's. */
    public void clearLandings() {
        softLandingUntil.clear();
    }

    // ---------------------------------------------------------------- the hop

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != KangarooKit.ROCKET) {
            return;
        }
        Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.hasKit(player, KangarooKit.ID)) {
            return; // anyone else gets an ordinary firework
        }
        if (action == Action.RIGHT_CLICK_BLOCK && Interact.opensBlock(event)) {
            return;
        }

        // Always cancel: the rocket is a tool, not ammunition, and must never be spent.
        event.setCancelled(true);
        if (player.hasCooldown(KangarooKit.ROCKET)) {
            return; // the item's own cooldown sweep is the only feedback needed
        }

        GameConfig config = game.config();
        Vector hop = player.getLocation().getDirection()
                .multiply(config.kangarooForwardPower())
                .setY(config.kangarooJumpPower());
        player.setVelocity(hop);
        player.setCooldown(KangarooKit.ROCKET, config.kangarooCooldownSeconds() * 20);
        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 1.0F);
    }

    // ---------------------------------------------------------------- the landing

    /** Landing a hit on another player buys a window with no fall damage. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || !kits.hasKit(attacker, KangarooKit.ID)) {
            return;
        }

        int seconds = game.config().kangarooFallImmunitySeconds();
        softLandingUntil.put(attacker.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        attacker.sendActionBar(Component.text("No fall damage for " + seconds + "s.",
                NamedTextColor.GREEN));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player player)
                || !kits.hasKit(player, KangarooKit.ID)) {
            return;
        }
        Long until = softLandingUntil.get(player.getUniqueId());
        if (until != null && System.currentTimeMillis() < until) {
            event.setCancelled(true);
        }
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
