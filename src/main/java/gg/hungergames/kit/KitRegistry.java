package gg.hungergames.kit;

import gg.hungergames.game.GameConfig;
import gg.hungergames.kit.kits.AnchorKit;
import gg.hungergames.kit.kits.BarbarianKit;
import gg.hungergames.kit.kits.BeastmasterKit;
import gg.hungergames.kit.kits.BerserkerKit;
import gg.hungergames.kit.kits.CannibalKit;
import gg.hungergames.kit.kits.CookiemonsterKit;
import gg.hungergames.kit.kits.CopycatKit;
import gg.hungergames.kit.kits.CultivatorKit;
import gg.hungergames.kit.kits.DemomanKit;
import gg.hungergames.kit.kits.DiggerKit;
import gg.hungergames.kit.kits.EndermageKit;
import gg.hungergames.kit.kits.FighterKit;
import gg.hungergames.kit.kits.FishermanKit;
import gg.hungergames.kit.kits.FiremanKit;
import gg.hungergames.kit.kits.GrandpaKit;
import gg.hungergames.kit.kits.JackhammerKit;
import gg.hungergames.kit.kits.JellyfishKit;
import gg.hungergames.kit.kits.LauncherKit;
import gg.hungergames.kit.kits.KangarooKit;
import gg.hungergames.kit.kits.TimelordKit;
import gg.hungergames.kit.kits.HermitKit;
import gg.hungergames.kit.kits.HulkKit;
import gg.hungergames.kit.kits.KayaKit;
import gg.hungergames.kit.kits.PoseidonKit;
import gg.hungergames.kit.kits.PyroKit;
import gg.hungergames.kit.kits.SpyKit;
import gg.hungergames.kit.kits.StomperKit;
import gg.hungergames.kit.kits.TankKit;
import gg.hungergames.kit.kits.TurtleKit;
import gg.hungergames.kit.kits.ThorKit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Holds the available kits and who picked what.
 *
 * <p>Adding a kit is a one-liner in {@link #registerDefaults(GameConfig)} — no core game logic changes.
 */
public final class KitRegistry {

    // Sorted by id rather than insertion-ordered, so /kits and tab completion both come out
    // alphabetical and the order kits are registered in below stops mattering.
    private final Map<String, Kit> kits = new TreeMap<>();
    private final Map<UUID, Kit> selected = new HashMap<>();

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
        register(new CopycatKit());
        register(new CookiemonsterKit());
        register(new JellyfishKit());
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

    /** Ability listeners call this to decide whether to fire. */
    public boolean hasKit(Player player, String kitId) {
        Kit kit = selected.get(player.getUniqueId());
        return kit != null && kit.id().equalsIgnoreCase(kitId);
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
    }
}
