package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.Kit;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.kit.kits.CopycatKit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Copycat's theft.
 *
 * <p>The tricky part is that copying is a property of the <em>player</em>, not of the kit they
 * are currently holding: the moment a Copycat takes their first kit, they no longer register as
 * a Copycat, yet they must keep stealing on every kill after that.
 *
 * <p>So the set below remembers who started as one. It is filled lazily on the first kill —
 * which is the last moment the killer still reads as a Copycat — and from then on membership,
 * not the selected kit, is what decides whether a kill steals.
 */
public final class CopycatListener implements Listener {

    private final HungerGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Players who began the match as a Copycat, whatever they are carrying now. */
    private final Set<UUID> copycats = new HashSet<>();

    public CopycatListener(HungerGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state, dropped on reset. */
    public void clearCopycats() {
        copycats.clear();
    }

    /**
     * Runs before {@link DeathListener}'s default-priority handler so the victim's kit is read
     * while it is unambiguously still theirs.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!game.state().isLive()) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }

        UUID killerId = killer.getUniqueId();
        // Last moment the killer still reads as a Copycat, so latch it here.
        if (kits.hasKit(killer, CopycatKit.ID)) {
            copycats.add(killerId);
        }
        if (!copycats.contains(killerId)) {
            return;
        }

        Kit stolen = kits.selectedFor(victim.getUniqueId());
        if (stolen == null) {
            return;
        }

        kits.select(killerId, stolen);

        if (stolen.id().equals(CopycatKit.ID)) {
            killer.sendMessage(Component.text("You copied a Copycat, still nothing to show for it.",
                    NamedTextColor.LIGHT_PURPLE));
            return;
        }

        killer.sendMessage(Component.text("You have taken the " + stolen.displayName()
                + " kit from " + victim.getName() + ".", NamedTextColor.LIGHT_PURPLE));

        if (!game.config().copycatGrantsEquipment()) {
            return;
        }
        // Next tick: the death is still resolving, and several kits hand out items.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (killer.isOnline() && game.isAlive(killer)) {
                stolen.apply(killer);
            }
        });
    }
}
