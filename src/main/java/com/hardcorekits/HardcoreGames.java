package com.hardcorekits;

import com.hardcorekits.command.AdminCommand;
import com.hardcorekits.command.KitCommand;
import com.hardcorekits.command.PlayerCommands;
import com.hardcorekits.command.StaffCommand;
import com.hardcorekits.game.GameConfig;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.staff.RolesStore;
import com.hardcorekits.staff.StaffManager;
import com.hardcorekits.stats.StatsStore;
import com.hardcorekits.web.StatusServer;
import com.hardcorekits.listener.CommandGuard;
import com.hardcorekits.listener.WatchdogListener;
import com.hardcorekits.listener.AdvancementListener;
import com.hardcorekits.listener.AnchorListener;
import com.hardcorekits.listener.BarbarianListener;
import com.hardcorekits.listener.BeastmasterListener;
import com.hardcorekits.listener.BerserkerListener;
import com.hardcorekits.listener.CannibalListener;
import com.hardcorekits.listener.CombatListener;
import com.hardcorekits.listener.CompassListener;
import com.hardcorekits.listener.CultivatorListener;
import com.hardcorekits.listener.ConnectionListener;
import com.hardcorekits.listener.DeathListener;
import com.hardcorekits.listener.DemomanListener;
import com.hardcorekits.listener.EnchantingListener;
import com.hardcorekits.listener.FiremanListener;
import com.hardcorekits.listener.HulkListener;
import com.hardcorekits.listener.JackhammerListener;
import com.hardcorekits.listener.CookiemonsterListener;
import com.hardcorekits.listener.JellyfishListener;
import com.hardcorekits.listener.LauncherListener;
import com.hardcorekits.listener.KangarooListener;
import com.hardcorekits.listener.KillCounterListener;
import com.hardcorekits.listener.NinjaListener;
import com.hardcorekits.listener.LegacyCombatListener;
import com.hardcorekits.listener.TimelordListener;
import com.hardcorekits.listener.DiggerListener;
import com.hardcorekits.listener.EndermageListener;
import com.hardcorekits.listener.FishermanListener;
import com.hardcorekits.listener.KayaListener;
import com.hardcorekits.listener.PoseidonListener;
import com.hardcorekits.listener.ProtectionListener;
import com.hardcorekits.listener.PyroListener;
import com.hardcorekits.listener.FlashListener;
import com.hardcorekits.listener.HadesListener;
import com.hardcorekits.listener.MonkListener;
import com.hardcorekits.listener.ScorchListener;
import com.hardcorekits.listener.ForgerListener;
import com.hardcorekits.listener.GamblerListener;
import com.hardcorekits.listener.GladiatorListener;
import com.hardcorekits.listener.GrapplerListener;
import com.hardcorekits.listener.ReaperListener;
import com.hardcorekits.listener.SnailListener;
import com.hardcorekits.listener.SoulstealerListener;
import com.hardcorekits.listener.SpidermanListener;
import com.hardcorekits.listener.VampireListener;
import com.hardcorekits.listener.ViperListener;
import com.hardcorekits.listener.SoupListener;
import com.hardcorekits.listener.SwitcherListener;
import com.hardcorekits.listener.WerewolfListener;
import com.hardcorekits.listener.WispListener;
import com.hardcorekits.listener.SpyListener;
import com.hardcorekits.listener.StomperListener;
import com.hardcorekits.listener.TankListener;
import com.hardcorekits.listener.TurtleListener;
import com.hardcorekits.listener.ThorListener;
import com.hardcorekits.util.CooldownBar;
import com.hardcorekits.world.BorderTask;
import com.hardcorekits.world.ChunkPregenerator;
import com.hardcorekits.world.WorldRotator;
import com.hardcorekits.world.WorldShaper;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class HardcoreGames extends JavaPlugin {

    private GameManager game;
    private KitRegistry kits;
    private WorldShaper worldShaper;
    private StatusServer statusServer;
    private StaffManager staff;
    private ChunkPregenerator pregen;

    @Override
    public void onEnable() {
        // The plugin was born as "HungerGames"; a server upgrading across the rebrand has
        // its stats.json and roles.json under the old folder name. Adopt that folder whole
        // before anything touches the new one — lifetime stats outlive even the name.
        java.io.File legacy = new java.io.File(getDataFolder().getParentFile(), "HungerGames");
        if (legacy.isDirectory() && !getDataFolder().exists() && legacy.renameTo(getDataFolder())) {
            getLogger().info("Adopted the old HungerGames data folder.");
        }

        // One config, one truth. The copy in the data folder is a mirror of the one in the
        // jar, rewritten on every boot — edit src/main/resources/config.yml and rebuild.
        // The old two-config split is how the feast spent days secretly 10 blocks wide: the
        // shipped file said one thing while the server quietly read another.
        saveResource("config.yml", true);
        reloadConfig();

        // If the last boot retired a map, its folder is still on disk and is not the world we
        // just booted into — so now is the one safe moment to delete it.
        WorldRotator.purgePrevious(this);

        // Config first: kits with tuning values of their own are handed it as they register.
        GameConfig config = new GameConfig(getConfig());

        kits = new KitRegistry();
        kits.registerDefaults(config);

        // Stats live in the data folder, so they survive the world rotation by design.
        StatsStore stats = new StatsStore(getDataFolder(), getLogger());

        game = new GameManager(this, kits, config, stats);

        // The staff layer sits over the game: mod and trainee roles survive world rotation
        // like the stats do. Owners are simply the server's ops — /op and /deop at the
        // console are the whole owner lifecycle.
        RolesStore roles = new RolesStore(getDataFolder(), getLogger());
        staff = new StaffManager(this, game, roles);
        game.setStaff(staff);
        game.onReset(staff::clearModMode);

        // Kit abilities stay locked until invincibility wears off. One gate, so every kit,
        // including any written later, is covered without touching its listener.
        kits.setAbilityGate(() -> game.state().isPvpEnabled());
        worldShaper = new WorldShaper(this, config.world().getName(),
                config.swampMushroomsPerChunk());

        // server.properties is rewritten on shutdown by the world rotator and the run/ copy is
        // untracked, so the listing details live in the plugin config and are stamped on at boot.
        getServer().setMaxPlayers(config.maxPlayers());
        getServer().motd(Component.text(config.motd()));

        registerCommands();
        registerListeners();
        SoupListener.registerRecipe(this);
        LegacyCombatListener.removeShieldRecipe(this);
        KayaListener.registerRecipe(this);

        game.enable();
        worldShaper.start();
        new BorderTask(this, game).start();
        // Generate the whole play area while the lobby fills — worldgen is the most
        // expensive thing scattered players can trigger, and this takes it off the table.
        // Centre-out, and it narrows to a trickle the moment a match goes live.
        pregen = new ChunkPregenerator(this, game);
        pregen.start();
        if (config.webEnabled()) {
            statusServer = new StatusServer(this, game);
            statusServer.start(config.webBind(), config.webPort());
        }
        getLogger().info("Hardcore Games enabled — state: " + game.state());
    }

    @Override
    public void onDisable() {
        // First thing out the door: the pregen sweep must stop issuing chunk requests, or
        // the halting chunk system waits its full 60s timeouts on work we keep creating.
        if (pregen != null) {
            pregen.stop();
        }
        if (statusServer != null) {
            statusServer.stop();
            statusServer = null;
        }
        SoupListener.unregisterRecipe(this);
        KayaListener.unregisterRecipe(this);
        if (game == null) {
            return;
        }
        game.disable();
        game.stats().flush();

        // Retiring the map has to happen here, on the way out, and not at boot: Paper reloads
        // server.properties when it starts and writes it back out as it stops, so a level-name
        // written mid-run is simply overwritten and the next boot comes up on the same map.
        // Measured, not assumed — rotating at enable produced two identical maps in a row.
        //
        // The cost is that a crash never gets here. That case belongs to the launcher, which
        // can see a world folder that was never retired and drop it while nothing is holding
        // it; tools/run-loop.sh does exactly that. isStopping keeps /reload from rotating a
        // server that is not going anywhere.
        if (game.config().freshWorldOnRestart() && Bukkit.isStopping()) {
            WorldRotator.rotate(this, game.config().worldgenNoOceans());
        }
    }

    public GameManager game() {
        return game;
    }

    public KitRegistry kits() {
        return kits;
    }

    public WorldShaper worldShaper() {
        return worldShaper;
    }

    private void registerCommands() {
        AdminCommand admin = new AdminCommand(this, game);
        bind("hg", admin, admin);

        KitCommand kitCommand = new KitCommand(game, kits);
        bind("kit", kitCommand, kitCommand);
        bind("kits", kitCommand, null);

        PlayerCommands player = new PlayerCommands(game, staff);
        bind("help", player, null);
        bind("stats", player, null);
        bind("msg", player, null);
        bind("feast", player, null);
        bind("game", player, null);

        StaffCommand staffCommand = new StaffCommand(staff);
        bind("mod", staffCommand, null);
        bind("mods", staffCommand, null);
        bind("ban", staffCommand, null);
        bind("pending", staffCommand, null);
        bind("propose", staffCommand, null);
    }

    private void bind(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml");
            return;
        }
        command.setExecutor(executor);
        if (completer != null) {
            command.setTabCompleter(completer);
        }
    }

    private void registerListeners() {
        DemomanListener demoman = new DemomanListener(this, game, kits);
        // Mines are per-match state, so drop them whenever the game resets.
        game.onReset(demoman::clearTraps);

        // Any kit timer still on an XP bar dies with the match it belonged to.
        game.onReset(CooldownBar::clearAll);

        DiggerListener digger = new DiggerListener(this, game, kits);
        // A burning fuse is per-match state too — never let one fire into the next game.
        game.onReset(digger::clearFuses);

        AnchorListener anchor = new AnchorListener(this, game, kits);
        game.onReset(anchor::clearState);

        BarbarianListener barbarian = new BarbarianListener(game, kits);
        // Earned XP and kill counts are per-match state, like the mines.
        game.onReset(barbarian::clearProgress);

        FishermanListener fisherman = new FishermanListener(this, game, kits);
        game.onReset(fisherman::clearCooldowns);

        PoseidonListener poseidon = new PoseidonListener(this, game, kits);
        game.onReset(poseidon::clearState);

        // The Spy watches on a sweep rather than on movement, so it needs starting like the
        // border does; who it has already warned about is per-match state.
        SpyListener spy = new SpyListener(this, game, kits);
        game.onReset(spy::clearState);
        spy.start();


        ThorListener thor = new ThorListener(this, game, kits);
        game.onReset(thor::clearCooldowns);

        KayaListener kaya = new KayaListener(this, game, kits);
        game.onReset(kaya::clearTraps);

        JackhammerListener jackhammer = new JackhammerListener(this, game, kits);
        game.onReset(jackhammer::clearCooldowns);

        HulkListener hulk = new HulkListener(this, game, kits);
        game.onReset(hulk::clearGrips);

        SwitcherListener switcher = new SwitcherListener(this, game, kits);
        game.onReset(switcher::clearCooldowns);

        MonkListener monk = new MonkListener(this, game, kits);
        game.onReset(monk::clearCooldowns);

        FlashListener flash = new FlashListener(this, game, kits);
        game.onReset(flash::clearCooldowns);

        // Armies follow on a sweep, like the Spy's radar; they disband with the match.
        HadesListener hades = new HadesListener(this, game, kits);
        game.onReset(hades::clearMinions);
        hades.start();

        WispListener wisp = new WispListener(this, game, kits);
        // Decoys are per-match creatures; none may wander into the next game.
        game.onReset(wisp::clearClones);

        // The Werewolf watches the clock on a sweep, like the Spy watches distances.
        WerewolfListener werewolf = new WerewolfListener(this, game, kits);
        werewolf.start();

        KangarooListener kangaroo = new KangarooListener(this, game, kits);
        game.onReset(kangaroo::clearLandings);

        JellyfishListener jellyfish = new JellyfishListener(this, game, kits);
        game.onReset(jellyfish::drainAll);

        NinjaListener ninja = new NinjaListener(game, kits);
        game.onReset(ninja::clearMarks);

        LauncherListener launcher = new LauncherListener(game, kits);
        // Pads are per-match state, like the Demoman's mines.
        game.onReset(launcher::clearPads);

        TimelordListener timelord = new TimelordListener(this, game, kits);
        game.onReset(timelord::releaseAll);

        LegacyCombatListener legacyCombat = new LegacyCombatListener(this, game);
        game.onReset(legacyCombat::clearHealTimers);

        // Created ahead of the loop because DeathListener consults it for the stomp kill line.
        StomperListener stomperListener = new StomperListener(game, kits);

        // Ahead of the loop for the same reason: DeathListener asks it before eliminating.
        SoulstealerListener souls = new SoulstealerListener(this, game, kits);
        game.onReset(souls::clearState);

        SpidermanListener spiderman = new SpidermanListener(this, game, kits);
        game.onReset(spiderman::clearWebs);

        VampireListener vampire = new VampireListener(game, kits);
        game.onReset(vampire::clearState);

        ScorchListener scorch = new ScorchListener(game, kits);
        game.onReset(scorch::clearTrails);

        GrapplerListener grappler = new GrapplerListener(this, game, kits);
        game.onReset(grappler::clearCooldowns);

        GamblerListener gambler = new GamblerListener(this, game, kits);
        game.onReset(gambler::clearTables);

        BerserkerListener berserker = new BerserkerListener(this, game, kits);
        game.onReset(berserker::clearState);

        GladiatorListener gladiator = new GladiatorListener(this, game, kits);
        game.onReset(gladiator::clearDuels);

        // Flag-only anticheat. Its hover sweep starts with the border task, below.
        WatchdogListener watchdog = new WatchdogListener(this, game, staff);
        game.onReset(watchdog::clearState);
        watchdog.start();

        for (Listener listener : new Listener[]{
                new AdvancementListener(),
                new EnchantingListener(this, game),
                new ConnectionListener(game, staff),
                new CommandGuard(staff),
                watchdog,
                new ProtectionListener(game),
                new DeathListener(this, game, stomperListener, souls),
                new CompassListener(game),
                new CombatListener(game),
                new SoupListener(game),
                stomperListener,
                new CultivatorListener(this, game, kits),
                new EndermageListener(this, game, kits),
                anchor,
                barbarian,
                fisherman,
                poseidon,
                spy,
                new FiremanListener(game, kits),
                new TankListener(this, game, kits),
                new TurtleListener(game, kits),
                thor,
                kaya,
                new BeastmasterListener(game, kits),
                berserker,
                new CannibalListener(game, kits),
                new SnailListener(game, kits),
                scorch,
                new ViperListener(game, kits),
                new ForgerListener(game, kits),
                gambler,
                gladiator,
                new ReaperListener(game, kits),
                grappler,
                spiderman,
                vampire,
                souls,
                monk,
                hades,
                flash,
                switcher,
                wisp,
                werewolf,
                new PyroListener(game, kits),
                jackhammer,
                hulk,
                kangaroo,
                new CookiemonsterListener(game, kits),
                new KillCounterListener(game),
                jellyfish,
                launcher,
                ninja,
                timelord,
                legacyCombat,
                demoman,
                digger,
                worldShaper}) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
}
