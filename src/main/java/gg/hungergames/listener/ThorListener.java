package gg.hungergames.listener;

import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.ThorKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thor's hammer.
 *
 * <p>The strike is resolved against the top of the clicked column rather than the block that
 * was clicked, which is the whole anti-tower mechanic: click the base of someone's pillar and
 * the lightning lands on them, not at your feet.
 *
 * <p>Three outcomes, in order of precedence:
 * <ol>
 *   <li>The top block is already netherrack from an earlier high strike — it detonates.</li>
 *   <li>The strike lands above the height threshold — burning netherrack is left behind and
 *       the shove is stronger.</li>
 *   <li>Anything lower — lightning and a smaller shove.</li>
 * </ol>
 *
 * <p>Damage is left to the lightning itself, so a Fireman's immunity applies without this class
 * needing to know the kit exists.
 */
public final class ThorListener implements Listener {

    /** Vanilla TNT strength, which the netherrack blast is expressed as a fraction of. */
    private static final float TNT_POWER = 4.0F;
    /** Minimum upward component of the shove, so victims are lifted rather than slid. */
    private static final double MIN_LIFT = 0.4D;

    private final GameManager game;
    private final KitRegistry kits;

    /** Strikes spent since the last rest, per player. */
    private final Map<UUID, Integer> charges = new HashMap<>();
    /** When a spent Thor may strike again. */
    private final Map<UUID, Long> readyAt = new HashMap<>();

    public ThorListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset. */
    public void clearCooldowns() {
        charges.clear();
        readyAt.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onStrike(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || !game.state().isLive()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != ThorKit.HAMMER
                || !kits.hasKit(player, ThorKit.ID)) {
            return;
        }
        if (onCooldown(player)) {
            return;
        }

        // Otherwise the axe strips the log or carves a path instead of casting.
        event.setCancelled(true);

        World world = clicked.getWorld();
        int x = clicked.getX();
        int z = clicked.getZ();
        int topY = world.getHighestBlockYAt(x, z);
        Block top = world.getBlockAt(x, topY, z);
        Location strike = top.getLocation().add(0.5D, 1.0D, 0.5D);

        world.strikeLightning(strike);

        if (top.getType() == Material.NETHERRACK) {
            detonate(world, strike);
            return;
        }

        boolean high = topY > game.config().thorHighStrikeY();
        if (high) {
            leaveBurningNetherrack(world, top);
        }
        shove(strike, player, high);
    }

    /**
     * Charges, not a per-use timer: strike freely a few times, then wait.
     *
     * <p>That keeps the kit's burst — the whole point is answering a tower or a rush right now —
     * while stopping it being an endless lightning hose.
     *
     * @return true if the strike should be refused
     */
    private boolean onCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long ready = readyAt.get(uuid);
        if (ready != null && now < ready) {
            long remaining = (long) Math.ceil((ready - now) / 1000.0D);
            player.sendActionBar(Component.text(
                    "Lightning strike has " + remaining + "s cooldown left!", NamedTextColor.RED));
            return true;
        }

        int max = Math.max(1, game.config().thorUses());
        int used = charges.merge(uuid, 1, Integer::sum);
        if (used >= max) {
            // Spent the last charge: this strike still lands, the next one waits.
            charges.remove(uuid);
            readyAt.put(uuid, now + (game.config().thorCooldownSeconds() * 1000L));
        }
        return false;
    }

    /** Striking your own netherrack again turns it into a charge. */
    private void detonate(World world, Location strike) {
        world.getBlockAt(strike).setType(Material.AIR);
        world.createExplosion(strike,
                (float) (TNT_POWER * game.config().thorNetherrackTntFraction()), true, true);
    }

    /**
     * Replaces the struck block with netherrack and lights it.
     *
     * <p>Netherrack burns indefinitely, so this doubles as a permanent light source — which is
     * what makes striking underground worthwhile.
     */
    private void leaveBurningNetherrack(World world, Block top) {
        top.setType(Material.NETHERRACK);
        Block above = top.getRelative(0, 1, 0);
        if (above.getType().isAir()) {
            above.setType(Material.FIRE);
        }
    }

    /**
     * Pushes everyone near the strike away from it, harder on a high strike.
     *
     * <p>Thor is exempt from his own shove. He still takes the lightning's damage like anyone
     * else — what he does not do is get thrown off his own ledge by a strike he aimed.
     */
    private void shove(Location strike, Player caster, boolean high) {
        double radius = game.config().thorRadius();
        double power = high ? game.config().thorHighPower() : game.config().thorLowPower();

        for (Entity entity : strike.getWorld().getNearbyEntities(strike, radius, radius, radius)) {
            if (!(entity instanceof Player victim) || !game.isAlive(victim)
                    || victim.equals(caster)) {
                continue;
            }
            Vector away = victim.getLocation().toVector().subtract(strike.toVector());
            if (away.lengthSquared() < 0.0001D) {
                away = new Vector(0.0D, 1.0D, 0.0D); // struck dead-centre; straight up
            }
            away = away.normalize().multiply(power);
            away.setY(Math.max(MIN_LIFT, away.getY()));
            victim.setVelocity(victim.getVelocity().add(away));
        }

        caster.sendMessage(Component.text(high
                        ? "A high strike. The ground burns."
                        : "Lightning called.",
                NamedTextColor.AQUA));
    }
}
