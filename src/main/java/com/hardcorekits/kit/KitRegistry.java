package com.hardcorekits.kit;

import com.hardcorekits.game.GameConfig;
import com.hardcorekits.kit.kits.AnchorKit;
import com.hardcorekits.kit.kits.BarbarianKit;
import com.hardcorekits.kit.kits.BeastmasterKit;
import com.hardcorekits.kit.kits.BerserkerKit;
import com.hardcorekits.kit.kits.CannibalKit;
import com.hardcorekits.kit.kits.CookiemonsterKit;
import com.hardcorekits.kit.kits.CultivatorKit;
import com.hardcorekits.kit.kits.DemomanKit;
import com.hardcorekits.kit.kits.DiggerKit;
import com.hardcorekits.kit.kits.EndermageKit;
import com.hardcorekits.kit.kits.FighterKit;
import com.hardcorekits.kit.kits.FishermanKit;
import com.hardcorekits.kit.kits.FlashKit;
import com.hardcorekits.kit.kits.HadesKit;
import com.hardcorekits.kit.kits.MonkKit;
import com.hardcorekits.kit.kits.ScorchKit;
import com.hardcorekits.kit.kits.FiremanKit;
import com.hardcorekits.kit.kits.GrandpaKit;
import com.hardcorekits.kit.kits.JackhammerKit;
import com.hardcorekits.kit.kits.JellyfishKit;
import com.hardcorekits.kit.kits.LauncherKit;
import com.hardcorekits.kit.kits.KangarooKit;
import com.hardcorekits.kit.kits.TimelordKit;
import com.hardcorekits.kit.kits.HermitKit;
import com.hardcorekits.kit.kits.HulkKit;
import com.hardcorekits.kit.kits.KayaKit;
import com.hardcorekits.kit.kits.NinjaKit;
import com.hardcorekits.kit.kits.PoseidonKit;
import com.hardcorekits.kit.kits.PyroKit;
import com.hardcorekits.kit.kits.ForgerKit;
import com.hardcorekits.kit.kits.GamblerKit;
import com.hardcorekits.kit.kits.GladiatorKit;
import com.hardcorekits.kit.kits.GrapplerKit;
import com.hardcorekits.kit.kits.ReaperKit;
import com.hardcorekits.kit.kits.SnailKit;
import com.hardcorekits.kit.kits.SoulstealerKit;
import com.hardcorekits.kit.kits.SpidermanKit;
import com.hardcorekits.kit.kits.VampireKit;
import com.hardcorekits.kit.kits.ViperKit;
import com.hardcorekits.kit.kits.SpyKit;
import com.hardcorekits.kit.kits.SwitcherKit;
import com.hardcorekits.kit.kits.StomperKit;
import com.hardcorekits.kit.kits.TankKit;
import com.hardcorekits.kit.kits.TurtleKit;
import com.hardcorekits.kit.kits.WerewolfKit;
import com.hardcorekits.kit.kits.WispKit;
import com.hardcorekits.kit.kits.ThorKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Holds the available kits and who picked what.
 *
 * <p>Adding a kit is a one-liner in {@link #registerDefaults(GameConfig)} — no core game logic changes.
 */
public final class KitRegistry {

    // Sorted by id rather than insertion-ordered, so /kits and tab completion both come out
    // alphabetical and the order kits are registered in below stops mattering.
    /** How often a player is told their abilities are still locked. */
    private static final long LOCK_NOTICE_MILLIS = 3000L;

    private final Map<String, Kit> kits = new TreeMap<>();
    private final Map<UUID, Kit> selected = new HashMap<>();
    private final Map<UUID, Long> lastLockNotice = new HashMap<>();

    /** Open until the plugin wires the game in, so a registry on its own behaves normally. */
    private BooleanSupplier abilityGate = () -> true;

    /** Kits with tuning values of their own take the config; the rest ignore it. */
    public void registerDefaults(GameConfig config) {
        register(new FighterKit());
        register(new DemomanKit());
        register(new StomperKit());
        register(new DiggerKit(config));
        register(new CultivatorKit());
        register(new EndermageKit());
        register(new AnchorKit());
        register(new BeastmasterKit());
        register(new BerserkerKit());
        register(new CannibalKit());
        register(new BarbarianKit(config));
        register(new FishermanKit());
        register(new PoseidonKit());
        register(new PyroKit());
        register(new SpyKit());
        register(new CookiemonsterKit());
        register(new JellyfishKit());
        register(new NinjaKit());
        register(new LauncherKit(config));
        register(new FiremanKit());
        register(new GrandpaKit());
        register(new JackhammerKit());
        register(new KangarooKit());
        register(new TimelordKit());
        register(new ThorKit());
        register(new HermitKit());
        register(new HulkKit());
        register(new KayaKit(config));
        register(new TankKit());
        register(new TurtleKit());
        register(new SnailKit());
        register(new SwitcherKit());
        register(new WerewolfKit());
        register(new WispKit());
        register(new ScorchKit());
        register(new MonkKit());
        register(new HadesKit());
        register(new FlashKit());
        register(new ViperKit());
        register(new ReaperKit());
        register(new SpidermanKit());
        register(new VampireKit());
        register(new GrapplerKit());
        register(new SoulstealerKit());
        register(new ForgerKit());
        register(new GamblerKit());
        register(new GladiatorKit());
    }

    public void register(Kit kit) {
        kits.put(kit.id().toLowerCase(Locale.ROOT), kit);
    }

    public Kit byId(String id) {
        return kits.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Kit> all() {
        return kits.values();
    }

    /** The kit a player will spawn with, or null if they never picked one. */
    public Kit selectedFor(UUID uuid) {
        return selected.get(uuid);
    }

    public void select(UUID uuid, Kit kit) {
        selected.put(uuid, kit);
    }

    /**
     * Whether this player is running that kit, with no regard for whether its abilities are
     * switched on yet.
     *
     * <p>The check for passives and bookkeeping: a Fireman does not burn during the grace
     * period, a Stomper's own fall damage is still capped, a Demoman still arms what they lay.
     * For anything a player actively does, use {@link #canUseAbility(Player, String)}.
     */
    public boolean hasKit(Player player, String kitId) {
        Kit kit = selected.get(player.getUniqueId());
        return kit != null && kit.id().equalsIgnoreCase(kitId);
    }

    /**
     * Whether this player may fire that kit's ability right now.
     *
     * <p>Abilities are locked until invincibility wears off, so the opening minutes are the
     * same for everyone: nobody is frozen, thrown, drilled out from under, or struck by
     * lightning before the game has properly started.
     *
     * <p>The rule lives here rather than in twenty listeners, which means a kit written later
     * is locked during the grace period by default instead of by remembering to check.
     */
    public boolean canUseAbility(Player player, String kitId) {
        if (!hasKit(player, kitId)) {
            return false;
        }
        if (abilityGate.getAsBoolean()) {
            return true;
        }
        explainLock(player);
        return false;
    }

    /**
     * Says why nothing happened, at most once every few seconds.
     *
     * <p>Rate-limited because this is reached from movement and damage handlers as well as from
     * deliberate clicks, and a locked ability should read as "not yet" rather than as a kit
     * that does not work.
     */
    private void explainLock(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastLockNotice.get(player.getUniqueId());
        if (last != null && now - last < LOCK_NOTICE_MILLIS) {
            return;
        }
        lastLockNotice.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text("Kit abilities unlock when invincibility ends.",
                NamedTextColor.GRAY));
    }

    /** Wired by the plugin once the game exists, since the gate is a game-state question. */
    public void setAbilityGate(BooleanSupplier gate) {
        this.abilityGate = gate;
    }

    /** Applies the player's chosen kit, falling back to the first registered kit. */
    /**
     * Gives a player their kit at match start.
     *
     * <p>Players hold no kit until they run {@code /kit}; anyone who never did is handed the
     * default here rather than dropping in empty-handed.
     *
     * @return true if they never picked one, so the caller can say so
     */
    public boolean applyTo(Player player) {
        Kit kit = selected.get(player.getUniqueId());
        if (kit == null) {
            // Picking nothing is a choice: they play as None, with only the compass everyone
            // gets. Nothing is assigned on their behalf.
            return true;
        }
        kit.apply(player);
        return false;
    }

    /** Called on reset — everyone re-picks next match. */
    public void clearSelections() {
        selected.clear();
        lastLockNotice.clear();
    }
}
