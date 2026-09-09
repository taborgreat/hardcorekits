package gg.hungergames.game;

import gg.hungergames.HungerGames;
import gg.hungergames.kit.Kit;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.util.Msg;
import gg.hungergames.util.Phases;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns the state machine. Every listener and command reads {@link #state()} before acting;
 * nothing else is allowed to mutate state except through the transition methods here.
 */
public final class GameManager {

    /** Old-school "click as fast as you can" combat: strip the 1.9 attack-speed gate. */
    private static final double UNCAPPED_ATTACK_SPEED = 1024.0D;

    /** Maximum vanilla world border size — effectively "no border, no wall". */
    private static final double VANILLA_BORDER_DISABLED = 29_999_984.0D;

    /** Noon, in world ticks. */
    private static final long MIDDAY_TICKS = 6000L;

    private final HungerGames plugin;
    private final KitRegistry kits;
    private final GameConfig config;
    private final FeastManager feast;
    private final EndgameManager endgame;
    private final CombatTracker combat;
    private final VictoryCeremony ceremony;

    /** Volatile: the login-validation event reads this off the main thread. */
    private volatile GameState state = GameState.WAITING;

    // Both sets are read from the async login-validation thread, so they must be concurrent.
    /** Still in the match — includes fake tributes. */
    private final Set<UUID> alive = ConcurrentHashMap.newKeySet();
    /** Out of the match — blocked from rejoining until the next reset. */
    private final Set<UUID> eliminated = ConcurrentHashMap.newKeySet();
    /** Disconnected mid-match, counting down to elimination. */
    private final Map<UUID, BukkitTask> graceTimers = new HashMap<>();
    /** Test-only stand-ins so the game can be driven solo. See /hgfake. */
    private final Map<UUID, String> fakes = new HashMap<>();
    /** Disconnects per player this game, counted only once PvP is live. */
    private final Map<UUID, Integer> disconnects = new HashMap<>();
    /** Short-lived per-player damage immunity, e.g. after an Endermage portal drag. */
    private final Map<UUID, Long> immuneUntil = new HashMap<>();
    /**
     * Cleanup run when a match resets. Ability kits register here to drop per-match state
     * (Demoman mines, and so on) without the state machine knowing they exist.
     */
    private final List<Runnable> resetHooks = new ArrayList<>();

    private int fakeCounter;

    private BukkitTask lobbyTask;
    private BukkitTask countdownTask;
    private BukkitTask invulnerableTask;

    public GameManager(HungerGames plugin, KitRegistry kits, GameConfig config) {
        this.plugin = plugin;
        this.kits = kits;
        this.config = config;
        this.feast = new FeastManager(plugin, this, config);
        this.endgame = new EndgameManager(plugin, this, config);
        this.combat = new CombatTracker(config.combatLogSeconds());
        this.ceremony = new VictoryCeremony(plugin, config);
    }

    // ---------------------------------------------------------------- lifecycle

    public void enable() {
        applyWorldRules();

        // Started once and never cancelled. It gates on the state itself, so no failure
        // anywhere else can leave the game unable to start — which is exactly what happened
        // when this was torn down and rebuilt on every transition.
        lobbyTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickLobby, 40L, 40L);

        enterWaiting();
    }

    public void disable() {
        ceremony.cancel();
        Phases.cancel(lobbyTask);
        Phases.cancel(countdownTask);
        Phases.cancel(invulnerableTask);
        feast.cancel();
        endgame.cancel();
        graceTimers.values().forEach(Phases::cancel);
        graceTimers.clear();
    }

    // ---------------------------------------------------------------- accessors

    public GameState state() {
        return state;
    }

    public GameConfig config() {
        return config;
    }

    public FeastManager feast() {
        return feast;
    }

    public EndgameManager endgame() {
        return endgame;
    }

    public CombatTracker combat() {
        return combat;
    }

    public KitRegistry kits() {
        return kits;
    }

    /** Registers per-match cleanup, run every time the game returns to WAITING. */
    public void onReset(Runnable hook) {
        resetHooks.add(hook);
    }

    /**
     * Makes a player untouchable for a few seconds.
     *
     * <p>Used where an ability moves someone against their will — being yanked through an
     * Endermage portal should not mean landing mid-swing and dying for it.
     */
    public void grantImmunity(Player player, int seconds) {
        immuneUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    /** Checked by the damage listener before anything else. */
    public boolean isImmune(Player player) {
        Long until = immuneUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    public Set<UUID> alive() {
        return Collections.unmodifiableSet(alive);
    }

    public boolean isAlive(Player player) {
        return alive.contains(player.getUniqueId());
    }

    /** Alive participants who are real, online players — fakes are excluded. */
    public List<Player> alivePlayers() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : alive) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    private String nameOf(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }
        return fakes.getOrDefault(uuid, "Unknown");
    }

    // ---------------------------------------------------------------- fake tributes (testing)

    public int fakeCount() {
        return fakes.size();
    }

    /** Adds stand-in tributes so thresholds and the win condition can be tested solo. */
    public int addFakes(int count) {
        for (int i = 0; i < count; i++) {
            UUID uuid = UUID.randomUUID();
            fakes.put(uuid, "Bot" + (++fakeCounter));
            if (state.isLive()) {
                alive.add(uuid);
            }
        }
        return fakes.size();
    }

    /** Eliminates fake tributes, announcing them exactly like real deaths. */
    public int killFakes(int count) {
        List<UUID> targets = new ArrayList<>();
        for (UUID uuid : fakes.keySet()) {
            if (targets.size() >= count) {
                break;
            }
            if (!state.isLive() || alive.contains(uuid)) {
                targets.add(uuid);
            }
        }
        for (UUID uuid : targets) {
            String name = fakes.get(uuid);
            if (state.isLive() && alive.remove(uuid)) {
                eliminated.add(uuid);
                announceElimination(Msg.killLine(name + " died."));
            }
            fakes.remove(uuid);
        }
        if (state.isLive()) {
            checkWinCondition();
        }
        return targets.size();
    }

    public void clearFakes() {
        for (UUID uuid : fakes.keySet()) {
            alive.remove(uuid);
        }
        fakes.clear();
    }

    // ---------------------------------------------------------------- join gating

    /**
     * Decides whether a connecting player is let in.
     *
     * @return null to allow, otherwise the reason to show on the disconnect screen
     */
    public Component loginDenialReason(UUID uuid) {
        if (state.isPreGame()) {
            return null;
        }
        if (eliminated.contains(uuid)) {
            return Component.text("You were eliminated. Wait for the next game.", NamedTextColor.RED);
        }
        if (alive.contains(uuid)) {
            return null; // reconnecting inside the grace window
        }
        return Component.text("A game is already in progress. Wait for the next one.", NamedTextColor.RED);
    }

    /** Cancels the disconnect countdown for a player who made it back in time. */
    public void handleRejoin(Player player) {
        BukkitTask timer = graceTimers.remove(player.getUniqueId());
        if (timer != null) {
            timer.cancel();
            Msg.notice(player.getName() + " reconnected.");
        }
    }

    /**
     * Handles a mid-match disconnect.
     *
     * <p>Logging out within the combat window counts as dying on the spot — otherwise the
     * player gets the normal window to reconnect.
     */
    public void handleQuit(Player player) {
        if (!state.isLive() || !alive.contains(player.getUniqueId())) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        // Inside the End Game the box is the whole match, and the lava is on a clock. Leaving
        // it has to be final: a reconnect window would let someone log out, wait for everyone
        // else to burn, and walk back in as the winner.
        if (endgame.hasBegun()) {
            abandonEndgame(uuid, name);
            return;
        }

        if (combat.inCombat(uuid)) {
            combatLogOut(uuid, name);
            return;
        }

        // Repeat-disconnect tracking only runs once PvP is live — dropping out during the
        // invincibility phase costs nothing but the reconnect window.
        if (state.isPvpEnabled()) {
            int count = disconnects.merge(uuid, 1, Integer::sum);
            if (count > config.maxDisconnects()) {
                forfeit(uuid, name);
                return;
            }
        }

        int grace = config.disconnectGraceSeconds();
        Msg.notice(name + " disconnected. " + grace + "s to reconnect.");
        graceTimers.put(uuid, Phases.delayed(plugin, grace, () -> {
            graceTimers.remove(uuid);
            forfeit(uuid, name);
        }));
    }

    /**
     * Removes a player who is gone for good: either they never came back inside the reconnect
     * window, or they have dropped out too many times this game.
     */
    private void forfeit(UUID uuid, String name) {
        Phases.cancel(graceTimers.remove(uuid));
        if (!alive.remove(uuid)) {
            return;
        }
        eliminated.add(uuid);
        combat.forget(uuid);

        announceElimination(Msg.forfeitLine(
                name + " was disconnected for too long, and has forfeit!"));
        checkWinCondition();
    }

    /**
     * Instant elimination for walking out of the End Game.
     *
     * <p>No grace timer and no disconnect allowance — being gone is the same as being dead in
     * there. Whoever last landed a hit still gets the credit, exactly as a combat log does.
     */
    private void abandonEndgame(UUID uuid, String name) {
        if (!alive.remove(uuid)) {
            return;
        }
        eliminated.add(uuid);
        Phases.cancel(graceTimers.remove(uuid));

        String killer = combat.lastAttackerName(uuid);
        combat.forget(uuid);

        announceElimination(killer == null
                ? Msg.forfeitLine(name + " abandoned the End Game, and has forfeit!")
                : Msg.killLine(name + " abandoned the End Game, courtesy of " + killer + "."));

        checkWinCondition();
    }

    /** Instant elimination for quitting mid-fight; the last attacker gets the kill. */
    private void combatLogOut(UUID uuid, String name) {
        alive.remove(uuid);
        eliminated.add(uuid);
        Phases.cancel(graceTimers.remove(uuid));

        String killer = combat.lastAttackerName(uuid);
        combat.forget(uuid);

        announceElimination(Msg.killLine(killer == null
                ? name + " logged out while in combat and died."
                : name + " logged out while in combat, courtesy of " + killer + "."));

        checkWinCondition();
    }

    /**
     * Eliminates every participant who is not online to be put in the box.
     *
     * <p>Called once by {@link EndgameManager} as the End Game opens. Someone sitting out the
     * end of the match in the reconnect window cannot be teleported in, and leaving them alive
     * would hand them the win once the lava has dealt with everyone who actually showed up.
     */
    void eliminateAbsent() {
        for (UUID uuid : new ArrayList<>(alive)) {
            if (fakes.containsKey(uuid)) {
                continue; // stand-ins are the End Game's own to clear up
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                continue;
            }
            String name = nameOf(uuid);
            alive.remove(uuid);
            eliminated.add(uuid);
            Phases.cancel(graceTimers.remove(uuid));
            combat.forget(uuid);
            announceElimination(Msg.forfeitLine(name + " missed the End Game, and has forfeit!"));
        }
        checkWinCondition();
    }

    // ---------------------------------------------------------------- transitions

    private void enterWaiting() {
        state = GameState.WAITING;
        alive.clear();
        eliminated.clear();
        kits.clearSelections();
        feast.clear();
        endgame.clear();
        ceremony.clear();
        combat.clear();
        disconnects.clear();
        immuneUntil.clear();
        clearFakes();
        // A broken kit hook must not stop the game returning to WAITING either.
        for (Runnable hook : resetHooks) {
            try {
                hook.run();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("A reset hook failed: " + e);
            }
        }

        // One awkward player must not abort the transition for everyone else.
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                prepareForLobby(player);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not reset " + player.getName()
                        + " for the lobby: " + e);
            }
        }

        holdMidday(true);
        Msg.notice("Waiting for players. Choose a kit with /kits.");
    }

    /**
     * "Need 7 players to start." — the lobby's answer to "why hasn't this started yet".
     *
     * <p>Deferred a tick because the headcount is read from the online list, and a quitting
     * player is still on it while their quit event runs.
     */
    public void announceLobbyNeed() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (state != GameState.WAITING) {
                return;
            }
            int missing = config.minPlayers() - participantCount();
            if (missing <= 0) {
                return; // the countdown is about to speak for itself
            }
            Msg.notice("Need " + Msg.players(missing) + " to start.");
        });
    }

    /** Runs every 2s during WAITING, watching for the player threshold. */
    private void tickLobby() {
        if (state == GameState.WAITING && participantCount() >= config.minPlayers()) {
            startCountdown();
        }
    }

    /** Everyone who would be dropped into a match right now: real players plus stand-ins. */
    public int participantCount() {
        return Bukkit.getOnlinePlayers().size() + fakes.size();
    }

    /** Whether the lobby watcher is actually running — the thing that starts a game. */
    public boolean lobbyWatcherRunning() {
        return lobbyTask != null && !lobbyTask.isCancelled();
    }

    public void startCountdown() {
        if (state != GameState.WAITING) {
            return;
        }

        // Schedule first, promote the state second. If scheduling throws, the game stays in
        // WAITING and keeps working rather than stranding in a COUNTDOWN that never ticks.
        // The lobby watcher is deliberately left running; it gates on the state.
        BukkitTask scheduled = Phases.countdown(plugin, config.countdownSeconds(),
                remaining -> {
                    if (Phases.isMilestone(remaining)) {
                        Msg.timer("Tournament will start in " + Msg.duration(remaining) + ".");
                    }
                },
                this::beginMatch);

        countdownTask = scheduled;
        state = GameState.COUNTDOWN;
    }

    /**
     * Pre-game is permanently noon; the clock only starts once the match does.
     *
     * <p>{@code advance_time} is the current name of the old {@code doDaylightCycle} rule.
     */
    private void holdMidday(boolean hold) {
        World world = config.world();
        world.setGameRule(GameRules.ADVANCE_TIME, !hold);
        if (hold) {
            world.setTime(MIDDAY_TICKS);
        }
    }

    /** COUNTDOWN -> INVULNERABLE: everyone dropped in around centre, then made invulnerable. */
    private void beginMatch() {
        // The countdown deliberately does not care if the lobby thins out — ten dropping to
        // seven should still play. An empty one is different: with joins locked for the
        // duration, a match that starts with nobody to fight has no way to end.
        // The floor bends to min-players so a solo test lobby still starts.
        if (participantCount() < Math.min(2, config.minPlayers())) {
            Msg.notice("Not enough players left to start. Waiting again.");
            enterWaiting();
            return;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        holdMidday(false);

        alive.clear();
        eliminated.clear();
        for (Player player : players) {
            prepareForMatch(player);
            player.teleport(dropPointFor(player));
            giveStartingItems(player);
            alive.add(player.getUniqueId());
        }
        alive.addAll(fakes.keySet());

        state = GameState.INVULNERABLE;

        Msg.timer("The Tournament has begun!");
        Msg.timer("There are " + Msg.players(alive.size()) + " participating.");
        Msg.timer("Everyone is invincible for " + Msg.duration(config.invulnerableSeconds()) + ".");
        Msg.timer("Good Luck!");

        // Beat before the invincibility countdown starts, so the opening block reads cleanly.
        Phases.delayed(plugin, 1L, this::startInvulnerabilityCountdown);
        feast.schedule();
        endgame.schedule();
    }

    private void startInvulnerabilityCountdown() {
        invulnerableTask = Phases.countdown(plugin, config.invulnerableSeconds(),
                remaining -> {
                    if (Phases.isMilestone(remaining)) {
                        Msg.timer("Invincibility wears off in " + Msg.duration(remaining) + ".");
                    }
                },
                this::enableCombat);
    }

    /**
     * A random point within the scatter radius of centre, high above the terrain.
     *
     * <p>Players fall in rather than being placed on the ground — that spreads them out
     * naturally and matches the classic drop-in start.
     */
    /**
     * The normal scatter point, unless the player's kit wants to start somewhere else.
     *
     * <p>The state machine does not know which kit does this — it just asks.
     */
    private Location dropPointFor(Player player) {
        Kit kit = kits.selectedFor(player.getUniqueId());
        if (kit != null) {
            Location override = kit.dropPoint(player, config);
            if (override != null) {
                return override;
            }
        }
        return randomDropPoint();
    }

    private Location randomDropPoint() {
        World world = config.world();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Uniform over the disc, not the radius, so players do not cluster at the centre.
        double angle = random.nextDouble() * 2.0D * Math.PI;
        double distance = config.scatterRadius() * Math.sqrt(random.nextDouble());

        int x = (int) Math.round(config.centerX() + distance * Math.cos(angle));
        int z = (int) Math.round(config.centerZ() + distance * Math.sin(angle));
        int y = world.getHighestBlockYAt(x, z) + config.dropHeight();

        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    /** Everyone gets a compass regardless of kit; the kit loadout goes on top. */
    private void giveStartingItems(Player player) {
        player.getInventory().addItem(new ItemStack(Material.COMPASS));

        // Anyone who never ran /kit plays as None; say so, rather than leaving them to wonder
        // whether their choice failed to register.
        if (kits.applyTo(player)) {
            player.sendMessage(Component.text("You chose no kit. You are playing as ",
                    NamedTextColor.GRAY)
                    .append(Component.text("None", NamedTextColor.AQUA))
                    .append(Component.text(".", NamedTextColor.GRAY)));
        }
    }

    private void enableCombat() {
        if (state != GameState.INVULNERABLE) {
            return;
        }
        state = GameState.ACTIVE;
        Msg.timer("You are no longer invincible.");
    }

    /** Called by {@link FeastManager} once the chests are down. */
    void enterFeastState() {
        if (state == GameState.ACTIVE) {
            state = GameState.FEAST;
        }
    }

    // ---------------------------------------------------------------- elimination

    /**
     * Removes a player from the match and kicks them. They cannot rejoin until the reset.
     *
     * @param announcement broadcast before the kick, so the kill line lands before the
     *                     vanilla "left the game" notice
     * @param kickReason   headline for the disconnect screen; the rejoin line is appended here
     * @return true if they were actually in the match
     */
    public boolean eliminate(Player player, Component announcement, Component kickReason) {
        if (!alive.remove(player.getUniqueId())) {
            return false;
        }
        eliminated.add(player.getUniqueId());
        Phases.cancel(graceTimers.remove(player.getUniqueId()));
        combat.forget(player.getUniqueId());

        announceElimination(announcement);

        player.kick(kickReason
                .append(Component.newline())
                .append(Component.text("Rejoin when the next game starts, or when the server resets.",
                        NamedTextColor.GRAY)));

        checkWinCondition();
        return true;
    }

    /** Kill line followed by the remaining count, both in blue. */
    private void announceElimination(Component announcement) {
        if (announcement != null) {
            Bukkit.broadcast(announcement);
        }
        Msg.kill(Msg.players(alive.size()) + " remaining.");
    }

    /**
     * The last tribute standing ends the match.
     *
     * <p>ENDING is the victory state: the ceremony owns the clock from here, and calls back
     * into {@link #reset()} once the winner has had their send-off. Anything that cannot be
     * celebrated — an empty map, or a winner who is a fake tribute or already offline —
     * announces and resets on a short timer instead.
     */
    private void checkWinCondition() {
        if (!state.isLive() || alive.size() > 1) {
            return;
        }

        state = GameState.ENDING;
        Phases.cancel(invulnerableTask);
        feast.cancel();
        endgame.cancel();

        if (alive.isEmpty()) {
            Msg.timer("Nobody survived.");
            Phases.delayed(plugin, 10L, this::reset);
            return;
        }

        UUID uuid = alive.iterator().next();
        Player winner = Bukkit.getPlayer(uuid);
        if (winner == null) {
            Msg.win(nameOf(uuid) + " wins!");
            Phases.delayed(plugin, 10L, this::reset);
            return;
        }
        ceremony.celebrate(winner, this::reset);
    }

    // ---------------------------------------------------------------- reset

    public void reset() {
        state = GameState.RESETTING;
        Phases.cancel(countdownTask);
        Phases.cancel(invulnerableTask);
        feast.cancel();
        endgame.cancel();
        graceTimers.values().forEach(Phases::cancel);
        graceTimers.clear();

        if (config.restartOnReset()) {
            Msg.notice("Restarting server…");
            // The map is retired on the way out, in HungerGames#onDisable, rather than here —
            // so a server stopped by hand gets a fresh world too, not only one that ran a match
            // all the way to a winner.
            Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 40L);
            return;
        }

        enterWaiting();
    }

    // ---------------------------------------------------------------- player prep

    /** Join / post-reset state: survival, empty inventory, parked at centre. */
    public void prepareForLobby(Player player) {
        resetPlayer(player);

        // Adventure rather than survival: pre-game is look-but-do-not-touch, and adventure says
        // so on the client. ProtectionListener cancels breaking and placing anyway, but a
        // cancelled break still plays the swing and flickers the block back — in adventure the
        // block never starts breaking at all. Admins keep survival so they can build.
        player.setGameMode(player.hasPermission("hungergames.admin")
                ? GameMode.SURVIVAL
                : GameMode.ADVENTURE);
        player.teleport(config.center());
    }

    private void prepareForMatch(Player player) {
        resetPlayer(player);
        // Survival for everyone once it is real, including anyone parked in adventure pre-game.
        player.setGameMode(GameMode.SURVIVAL);
    }

    private void resetPlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setFireTicks(0);
        player.setInvulnerable(false);
        player.setLevel(0);
        player.setExp(0.0F);
        player.setFallDistance(0.0F);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }

        applyCombatAttributes(player);
    }

    /** Removes the 1.9+ attack cooldown by making the recharge effectively instant. */
    public void applyCombatAttributes(Player player) {
        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.setBaseValue(UNCAPPED_ATTACK_SPEED);
        }
    }

    // ---------------------------------------------------------------- world setup

    private void applyWorldRules() {
        World world = config.world();

        // Leave the vanilla border at maximum so the client never draws the red barrier wall.
        // The real boundary is enforced by BorderTask, which just damages anyone outside it.
        WorldBorder border = world.getWorldBorder();
        border.setCenter(config.center());
        border.setSize(VANILLA_BORDER_DISABLED);

        world.setSpawnLocation(config.center());

        // Eliminated players are kicked, so nobody should ever see a respawn screen.
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);

        // The locator bar puts a dot above the hotbar for every player in the world, which
        // hands out for free exactly what the compass is supposed to cost you. Off, so
        // tracking someone is a decision again — as it was before the bar existed.
        world.setGameRule(GameRules.LOCATOR_BAR, false);

        // Belt and braces with AdvancementListener: it stops advancements being earned at all,
        // this stops anything already earned being announced.
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
    }
}
