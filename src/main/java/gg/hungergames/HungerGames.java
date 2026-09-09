package gg.hungergames;

import gg.hungergames.command.AdminCommand;
import gg.hungergames.command.KitCommand;
import gg.hungergames.game.GameConfig;
import gg.hungergames.game.GameManager;
import gg.hungergames.kit.KitRegistry;
import gg.hungergames.listener.AdvancementListener;
import gg.hungergames.listener.AnchorListener;
import gg.hungergames.listener.BarbarianListener;
import gg.hungergames.listener.BeastmasterListener;
import gg.hungergames.listener.BerserkerListener;
import gg.hungergames.listener.CannibalListener;
import gg.hungergames.listener.CombatListener;
import gg.hungergames.listener.CompassListener;
import gg.hungergames.listener.CopycatListener;
import gg.hungergames.listener.CultivatorListener;
import gg.hungergames.listener.ConnectionListener;
import gg.hungergames.listener.DeathListener;
import gg.hungergames.listener.DemomanListener;
import gg.hungergames.listener.FiremanListener;
import gg.hungergames.listener.HulkListener;
import gg.hungergames.listener.JackhammerListener;
import gg.hungergames.listener.CookiemonsterListener;
import gg.hungergames.listener.JellyfishListener;
import gg.hungergames.listener.LauncherListener;
import gg.hungergames.listener.KangarooListener;
import gg.hungergames.listener.LegacyCombatListener;
import gg.hungergames.listener.TimelordListener;
import gg.hungergames.listener.DiggerListener;
import gg.hungergames.listener.EndermageListener;
import gg.hungergames.listener.FishermanListener;
import gg.hungergames.listener.KayaListener;
import gg.hungergames.listener.PoseidonListener;
import gg.hungergames.listener.ProtectionListener;
import gg.hungergames.listener.PyroListener;
import gg.hungergames.listener.SoupListener;
import gg.hungergames.listener.SpyListener;
import gg.hungergames.listener.StomperListener;
import gg.hungergames.listener.TankListener;
import gg.hungergames.listener.TurtleListener;
import gg.hungergames.listener.ThorListener;
import gg.hungergames.world.BorderTask;
import gg.hungergames.world.WorldRotator;
import gg.hungergames.world.WorldShaper;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class HungerGames extends JavaPlugin {

    private GameManager game;
    private KitRegistry kits;
    private WorldShaper worldShaper;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // If the last boot retired a map, its folder is still on disk and is not the world we
        // just booted into — so now is the one safe moment to delete it.
        WorldRotator.purgePrevious(this);

        // Config first: kits with tuning values of their own are handed it as they register.
        GameConfig config = new GameConfig(getConfig());

        // Retire this map now, at boot, rather than on the way out. The next start gets fresh
        // terrain however this run ends — a finished match, /stop, Ctrl+C, or a crash — because
        // the decision is already written to disk instead of depending on a clean shutdown.
        if (config.freshWorldOnRestart()) {
            WorldRotator.rotate(this);
        }

        kits = new KitRegistry();
        kits.registerDefaults(config);

        game = new GameManager(this, kits, config);
        worldShaper = new WorldShaper(this, config.world().getName());

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
        getLogger().info("Hunger Games enabled — state: " + game.state());
    }

    @Override
    public void onDisable() {
        SoupListener.unregisterRecipe(this);
        KayaListener.unregisterRecipe(this);
        if (game == null) {
            return;
        }
        game.disable();
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
        bind("hgstart", admin, null);
        bind("hgendgame", admin, null);
        bind("hgreset", admin, null);
        bind("hgstate", admin, null);
        bind("hgfake", admin, null);

        KitCommand kitCommand = new KitCommand(game, kits);
        bind("kit", kitCommand, kitCommand);
        bind("kits", kitCommand, null);
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

        PoseidonListener poseidon = new PoseidonListener(game, kits);
        game.onReset(poseidon::clearState);

        // The Spy watches on a sweep rather than on movement, so it needs starting like the
        // border does; who it has already warned about is per-match state.
        SpyListener spy = new SpyListener(this, game, kits);
        game.onReset(spy::clearState);
        spy.start();

        CopycatListener copycat = new CopycatListener(this, game, kits);
        game.onReset(copycat::clearCopycats);

        ThorListener thor = new ThorListener(game, kits);
        game.onReset(thor::clearCooldowns);

        KayaListener kaya = new KayaListener(this, game, kits);
        game.onReset(kaya::clearTraps);

        JackhammerListener jackhammer = new JackhammerListener(this, game, kits);
        game.onReset(jackhammer::clearCooldowns);

        HulkListener hulk = new HulkListener(this, game, kits);
        game.onReset(hulk::clearGrips);

        KangarooListener kangaroo = new KangarooListener(game, kits);
        game.onReset(kangaroo::clearLandings);

        JellyfishListener jellyfish = new JellyfishListener(this, game, kits);
        game.onReset(jellyfish::drainAll);

        LauncherListener launcher = new LauncherListener(game, kits);
        // Pads are per-match state, like the Demoman's mines.
        game.onReset(launcher::clearPads);

        TimelordListener timelord = new TimelordListener(this, game, kits);
        game.onReset(timelord::releaseAll);

        LegacyCombatListener legacyCombat = new LegacyCombatListener(this, game);
        game.onReset(legacyCombat::clearHealTimers);

        for (Listener listener : new Listener[]{
                new AdvancementListener(),
                new ConnectionListener(game),
                new ProtectionListener(game),
                new DeathListener(this, game),
                new CompassListener(game),
                new CombatListener(game),
                new SoupListener(game),
                new StomperListener(game, kits),
                new CultivatorListener(this, game, kits),
                new EndermageListener(this, game, kits),
                anchor,
                barbarian,
                fisherman,
                poseidon,
                spy,
                copycat,
                new FiremanListener(game, kits),
                new TankListener(this, game, kits),
                new TurtleListener(game, kits),
                thor,
                kaya,
                new BeastmasterListener(game, kits),
                new BerserkerListener(game, kits),
                new CannibalListener(game, kits),
                new PyroListener(game, kits),
                jackhammer,
                hulk,
                kangaroo,
                new CookiemonsterListener(game, kits),
                jellyfish,
                launcher,
                timelord,
                legacyCombat,
                demoman,
                digger,
                worldShaper}) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
}
