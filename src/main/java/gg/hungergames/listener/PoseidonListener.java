package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.PoseidonKit;
import gg.hungergames.util.CooldownBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The tide.
 *
 * <p>Three rules, all hanging off one question — is the Poseidon standing in water right now?
 * <ul>
 *   <li>Melee out of the water is multiplied. Only the swing counts, not arrows, and only
 *       where the <em>attacker</em> is standing: being dragged into a river does not save
 *       anyone, and it does not make the victim weaker either.</li>
 *   <li>Moving underwater refills the lungs, so a swimming Poseidon never drowns. Floating
 *       still does not — the air drains exactly as it does for everyone else, which is what
 *       stops the kit from being a place to hide.</li>
 *   <li>Leaving the water costs a few seconds of Slowness, and getting back in cancels it
 *       early. The penalty is for being on land, so it never applies at sea.</li>
 * </ul>
 *
 * <p>The land/water transition is watched rather than polled: {@code PlayerMoveEvent} already
 * fires whenever anyone moves, and a Poseidon who is not moving cannot be crossing the line.
 */
public final class PoseidonListener implements Listener {

    /**
     * Shortest gap between two shoreline messages to the same player.
     *
     * <p>Standing on the edge of a lake flickers in and out of the water every move, and the
     * effect of that is self-correcting — the Slowness goes on and comes straight back off —
     * but the chat is not, so the talking is what gets rate-limited.
     */
    private static final long NOTICE_COOLDOWN_MILLIS = 3000L;

    /** Vanilla Strength: a flat +3 damage a level. */
    private static final double VANILLA_STRENGTH_DAMAGE = 3.0D;

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Poseidons the last move left standing in water — the other half of the transition. */
    private final Set<UUID> inWater = new HashSet<>();
    /** Poseidons currently carrying the leaving-the-water Slowness, so only ours is removed. */
    private final Set<UUID> beached = new HashSet<>();
    /** When each Poseidon was last told about the shoreline. */
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public PoseidonListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Dropped on reset, like any other per-match state. */
    public void clearState() {
        inWater.clear();
        beached.clear();
        lastNotice.clear();
    }

    // ---------------------------------------------------------------- strength

    /**
     * HIGH so the base damage is settled before it is scaled, and after {@link ProtectionListener}
     * has had the chance to cancel the hit outright during the grace period.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return; // arrows and everything thrown are ordinary
        }
        if (!(event.getDamager() instanceof Player attacker)
                || !game.state().isLive()
                || !kits.canUseAbility(attacker, PoseidonKit.ID)
                || !attacker.isInWater()) {
            return;
        }
        // The Strength worn in water is a badge, not the mechanic, so its flat bonus comes back
        // off before the multiplier goes on. Without this the two would compound and a Poseidon
        // would hit far harder than the configured number says.
        double base = Math.max(0.0D, event.getDamage() - strengthBadgeBonus(attacker));
        event.setDamage(base * game.config().poseidonWaterDamageMultiplier());
    }

    /**
     * The damage the visible Strength effect is adding, which is vanilla's flat +3 a level.
     *
     * <p>Only ever the level this kit grants. A Poseidon who drank a stronger potion keeps the
     * difference, which is theirs to keep.
     */
    private double strengthBadgeBonus(Player attacker) {
        if (!attacker.hasPotionEffect(PotionEffectType.STRENGTH)) {
            return 0.0D;
        }
        return VANILLA_STRENGTH_DAMAGE * game.config().poseidonWaterStrengthLevel();
    }

    // ---------------------------------------------------------------- lungs and legs

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!game.state().isLive() || !kits.hasKit(player, PoseidonKit.ID)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        boolean wet = player.isInWater();
        boolean wasWet = inWater.contains(uuid);

        if (!wet) {
            if (wasWet) {
                inWater.remove(uuid);
                beach(player);
            }
            return;
        }

        // Swimming keeps the lungs full. Standing still in it does not — no move, no refill.
        if (event.hasChangedPosition()) {
            player.setRemainingAir(player.getMaximumAir());
        }

        if (!wasWet) {
            inWater.add(uuid);
            wade(player);
        }
    }

    /** Leaving the water: slowed for a few seconds, and told why. */
    private void beach(Player player) {
        dropBadge(player);

        int seconds = game.config().poseidonLandSlownessSeconds();
        int level = game.config().poseidonLandSlownessLevel();
        if (seconds <= 0 || level <= 0) {
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                seconds * 20, Math.max(0, level - 1), true, false));
        beached.add(player.getUniqueId());
        // The penalty, draining on the XP bar. The fill only — the level stays kills.
        CooldownBar.show(plugin, game, player, seconds);
        notice(player, "You leave the water, slowed for " + seconds + "s.");
    }

    /** Getting back in: the land penalty is lifted early, because it was never for the sea. */
    private void wade(Player player) {
        if (beached.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            CooldownBar.clear(game, player);
        }
        wearBadge(player);
        notice(player, "The water is yours.");
    }

    /**
     * Strength, worn while standing in water.
     *
     * <p>The kit's real bite is the damage multiplier, which is invisible: nothing on screen
     * says whether it is live, and "am I in the water enough" is exactly the question a
     * Poseidon needs answered mid-fight. So the buff wears a Strength icon while it applies and
     * loses it the moment it stops, and {@link #strengthBadgeBonus(Player)} takes the effect's
     * own damage back out so the badge changes nothing but the display.
     *
     * <p>Ambient with no particles: an icon in the corner, not a cloud that gives the position
     * of someone hiding in a river away.
     */
    private void wearBadge(Player player) {
        int level = game.config().poseidonWaterStrengthLevel();
        if (level <= 0) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                PotionEffect.INFINITE_DURATION, level - 1, true, false, true));
    }

    /** Dry land, dry badge. */
    private void dropBadge(Player player) {
        if (game.config().poseidonWaterStrengthLevel() > 0) {
            player.removePotionEffect(PotionEffectType.STRENGTH);
        }
    }

    /** Shoreline chatter, rate-limited so paddling at the edge cannot flood anyone's chat. */
    private void notice(Player player, String text) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last != null && now - last < NOTICE_COOLDOWN_MILLIS) {
            return;
        }
        lastNotice.put(player.getUniqueId(), now);
        player.sendMessage(Component.text(text, NamedTextColor.AQUA));
    }
}
