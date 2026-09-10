package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.GladiatorKit;
import com.hardcorekits.util.Msg;
import com.hardcorekits.util.Phases;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Shadow Game.
 *
 * <p>A duel is a small sealed bedrock box built directly above the spot where the challenge
 * was thrown, high enough that nothing else is invited. Both fighters are placed inside,
 * facing each other, with a breath of immunity so nobody dies mid-teleport.
 *
 * <p>The seal is temporary: after a minute the walls and ceiling are taken away, leaving an
 * open platform in the sky — from there, jumping is a legal answer, and so is a teammate with
 * a bow below. The floor stays until the duel actually ends.
 *
 * <p>The duel ends when either fighter leaves the match — death or logout, the pipeline does
 * not care which. The survivor gets a grace window to loot the body and drink, then is put
 * back into the world at a random spot near the challenge, never exactly on it: the loser's
 * team standing on the X waiting is the oldest trick in the book.
 */
public final class GladiatorListener implements Listener {

    /** Interior half-width. 4 gives a 9x9 floor. */
    private static final int ARENA_RADIUS = 4;
    /** Interior height, floor to ceiling. */
    private static final int ARENA_HEIGHT = 4;
    private static final Material SHELL = Material.BEDROCK;

    /** One running duel: the two fighters, where it started, and where it was built. */
    private static final class Duel {
        UUID challenger;
        UUID challenged;
        Location origin;
        int cx;
        int cy;
        int cz;
        boolean settled;
        BukkitTask wallDrop;
        BukkitTask returnDown;
    }

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;

    /** Both fighters point at the same duel. */
    private final Map<UUID, Duel> duels = new HashMap<>();
    /** When each Gladiator may challenge again. */
    private final Map<UUID, Long> nextChallenge = new HashMap<>();

    public GladiatorListener(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
    }

    /** Per-match state: every arena is torn down and every clock stopped. */
    public void clearDuels() {
        for (Duel duel : new ArrayList<>(duels.values())) {
            Phases.cancel(duel.wallDrop);
            Phases.cancel(duel.returnDown);
            demolish(duel);
        }
        duels.clear();
        nextChallenge.clear();
    }

    // ---------------------------------------------------------------- the challenge

    @EventHandler(ignoreCancelled = true)
    public void onChallenge(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player challenger = event.getPlayer();
        if (!game.state().isLive() || !kits.canUseAbility(challenger, GladiatorKit.ID)) {
            return;
        }
        if (challenger.getInventory().getItemInMainHand().getType() != GladiatorKit.BARS) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player challenged)
                || challenged.equals(challenger) || !game.isAlive(challenged)) {
            return;
        }
        // Once the End Game box seals, the Shadow Game is closed. It teleports its fighters
        // to a sky arena and returns the survivor to the surface — which, with lava pouring
        // into the box everyone else is locked in, is not a duel but an exit. No smart
        // arena placement fixes that: any Shadow Game during the End Game moves someone
        // out of the box, so the answer is that the End Game answers all challenges.
        if (game.endgame().hasBegun()) {
            challenger.sendActionBar(Component.text(
                    "The End Game answers all challenges.", NamedTextColor.RED));
            return;
        }
        if (duels.containsKey(challenger.getUniqueId())
                || duels.containsKey(challenged.getUniqueId())) {
            challenger.sendActionBar(Component.text("One Shadow Game at a time.",
                    NamedTextColor.RED));
            return;
        }

        long now = System.currentTimeMillis();
        Long until = nextChallenge.get(challenger.getUniqueId());
        if (until != null && now < until) {
            challenger.sendActionBar(Component.text("The Shadow Game needs "
                    + ((until - now) / 1000L + 1) + "s.", NamedTextColor.RED));
            return;
        }
        nextChallenge.put(challenger.getUniqueId(),
                now + game.config().gladiatorCooldownSeconds() * 1000L);

        event.setCancelled(true);
        begin(challenger, challenged);
    }

    private void begin(Player challenger, Player challenged) {
        Duel duel = new Duel();
        duel.challenger = challenger.getUniqueId();
        duel.challenged = challenged.getUniqueId();
        duel.origin = challenger.getLocation().clone();

        World world = duel.origin.getWorld();
        duel.cx = duel.origin.getBlockX();
        duel.cz = duel.origin.getBlockZ();
        duel.cy = Math.min(world.getMaxHeight() - ARENA_HEIGHT - 4,
                game.config().gladiatorHeight());

        buildArena(world, duel, true);
        duels.put(duel.challenger, duel);
        duels.put(duel.challenged, duel);

        place(challenger, duel, -2);
        place(challenged, duel, 2);
        int immunity = 3;
        game.grantImmunity(challenger, immunity);
        game.grantImmunity(challenged, immunity);

        Msg.notice(challenger.getName() + " challenged " + challenged.getName()
                + " to the Shadow Game!");
        for (Player fighter : new Player[]{challenger, challenged}) {
            fighter.playSound(fighter.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6F, 1.6F);
            fighter.sendMessage(Component.text("The Shadow Game. One of you comes back.",
                    NamedTextColor.DARK_PURPLE));
        }

        int sealSeconds = game.config().gladiatorSealSeconds();
        duel.wallDrop = Phases.delayed(plugin, sealSeconds, () -> {
            buildArena(world, duel, false);
            for (UUID uuid : new UUID[]{duel.challenger, duel.challenged}) {
                Player fighter = Bukkit.getPlayer(uuid);
                if (fighter != null && fighter.isOnline()) {
                    fighter.sendMessage(Component.text(
                            "The walls fall away. Gravity is now an option.",
                            NamedTextColor.DARK_PURPLE));
                }
            }
        });
    }

    private void place(Player fighter, Duel duel, int offsetX) {
        Location spot = new Location(duel.origin.getWorld(),
                duel.cx + offsetX + 0.5D, duel.cy + 1, duel.cz + 0.5D);
        spot.setDirection(new Vector(-Math.signum(offsetX), 0.0D, 0.0D));
        fighter.setFallDistance(0.0F);
        fighter.teleport(spot);
    }

    // ---------------------------------------------------------------- the arena

    /**
     * Builds the box, or reduces it to its floor.
     *
     * <p>Same walk both ways: with the seal on, the outer blocks are bedrock and the inside is
     * air; with it off, only the floor remains. Glowstone in the ceiling corners lights the
     * sealed minute, and leaves with the ceiling.
     */
    private void buildArena(World world, Duel duel, boolean sealed) {
        int r = ARENA_RADIUS + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean wall = Math.abs(dx) == r || Math.abs(dz) == r;
                for (int dy = 0; dy <= ARENA_HEIGHT + 1; dy++) {
                    boolean shell = dy == 0 || (sealed && (wall || dy == ARENA_HEIGHT + 1));
                    Material material = shell ? SHELL : Material.AIR;
                    if (sealed && dy == ARENA_HEIGHT + 1
                            && Math.abs(dx) == ARENA_RADIUS && Math.abs(dz) == ARENA_RADIUS) {
                        material = Material.GLOWSTONE;
                    }
                    world.getBlockAt(duel.cx + dx, duel.cy + dy, duel.cz + dz)
                            .setType(material, false);
                }
            }
        }
    }

    private void demolish(Duel duel) {
        World world = duel.origin.getWorld();
        int r = ARENA_RADIUS + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= ARENA_HEIGHT + 1; dy++) {
                    world.getBlockAt(duel.cx + dx, duel.cy + dy, duel.cz + dz)
                            .setType(Material.AIR, false);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the verdict

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        settle(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID gone = event.getPlayer().getUniqueId();

        // Leaving the server mid-duel is not a disconnect, it is a surrender: instant
        // elimination, no reconnect window — the same finality as walking out of the End
        // Game. Done before settle() so the duel resolves against someone already dead.
        Duel duel = duels.get(gone);
        if (duel != null && !duel.settled) {
            UUID opponent = gone.equals(duel.challenger) ? duel.challenged : duel.challenger;
            Player survivor = Bukkit.getPlayer(opponent);
            game.abandonDuel(gone, event.getPlayer().getName(),
                    survivor != null ? survivor.getName() : "the Shadow Game");
        }

        settle(gone);
    }

    /** Either fighter leaving the match ends the duel; the other is the survivor. */
    private void settle(UUID gone) {
        Duel duel = duels.get(gone);
        if (duel == null || duel.settled) {
            return;
        }
        duel.settled = true;
        Phases.cancel(duel.wallDrop);

        UUID survivorId = gone.equals(duel.challenger) ? duel.challenged : duel.challenger;
        Player survivor = Bukkit.getPlayer(survivorId);
        int lootSeconds = game.config().gladiatorLootSeconds();
        if (survivor != null && survivor.isOnline()) {
            survivor.sendMessage(Component.text(game.endgame().hasBegun()
                    ? "The Shadow Game is yours. The End Game holds you where you stand."
                    : "The Shadow Game is yours. " + lootSeconds
                            + " seconds to gather, then you return.",
                    NamedTextColor.DARK_PURPLE));
        }

        duel.returnDown = Phases.delayed(plugin, lootSeconds, () -> conclude(duel, survivorId));
    }

    private void conclude(Duel duel, UUID survivorId) {
        duels.remove(duel.challenger);
        duels.remove(duel.challenged);

        // If the End Game began while this duel ran, its fighters were pulled into the box
        // with everyone else — teleporting the survivor "back" now would lift them out of
        // it, past the lava, onto open ground. They stay exactly where the box put them.
        Player survivor = Bukkit.getPlayer(survivorId);
        if (survivor != null && survivor.isOnline() && game.isAlive(survivor)
                && !game.endgame().hasBegun()) {
            survivor.setFallDistance(0.0F);
            survivor.teleport(returnSpot(duel));
            game.grantImmunity(survivor, 3);
        }
        demolish(duel);
    }

    /** Near the challenge, never on it — camping the exact X is the oldest trick there is. */
    private Location returnSpot(Duel duel) {
        World world = duel.origin.getWorld();
        double radius = game.config().gladiatorReturnRadius();
        double angle = ThreadLocalRandom.current().nextDouble() * 2.0D * Math.PI;
        double distance = radius * Math.sqrt(ThreadLocalRandom.current().nextDouble());

        int x = duel.origin.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = duel.origin.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        return new Location(world, x + 0.5D, world.getHighestBlockYAt(x, z) + 1.0D, z + 0.5D);
    }
}
