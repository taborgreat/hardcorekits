package com.hardcorekits.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.BiomeSearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Typed view over config.yml. Reloaded wholesale rather than read per-access. */
public final class GameConfig {

    /**
     * One rung of the Barbarian's sword ladder: the total XP earned that buys it, the sword it
     * buys, and an optional sharpness level on top.
     */
    public record SwordTier(int xp, Material material, int sharpness) {
    }

    /** Used when {@code kits.barbarian.tiers} is missing or entirely unreadable. */
    private static final List<SwordTier> DEFAULT_SWORD_TIERS = List.of(
            new SwordTier(0, Material.WOODEN_SWORD, 0),
            new SwordTier(20, Material.STONE_SWORD, 0),
            new SwordTier(60, Material.IRON_SWORD, 0),
            new SwordTier(120, Material.DIAMOND_SWORD, 0),
            new SwordTier(200, Material.DIAMOND_SWORD, 1),
            new SwordTier(300, Material.DIAMOND_SWORD, 2),
            new SwordTier(450, Material.DIAMOND_SWORD, 3));

    /** Where the mushrooms are, and so where the soup is. */
    private static final Biome[] SWAMP_BIOMES = {Biome.SWAMP, Biome.MANGROVE_SWAMP};

    /** Biomes that only ever appear on dry land, used to pull the centre out of the sea. */
    private static final Biome[] INLAND_BIOMES = {
            Biome.PLAINS, Biome.SUNFLOWER_PLAINS, Biome.MEADOW, Biome.SAVANNA,
            Biome.FOREST, Biome.BIRCH_FOREST, Biome.DARK_FOREST, Biome.TAIGA,
            Biome.OLD_GROWTH_PINE_TAIGA, Biome.JUNGLE, Biome.DESERT, Biome.WINDSWEPT_HILLS};

    private final String worldName;
    private final int configuredCenterX;
    private final int configuredCenterZ;
    private final boolean centerAutoLand;
    private final boolean centerPreferSwamp;
    private final int centerSearchRadius;
    private final int minPlayers;
    private final int busyPlayers;
    private final int busyCountdownSeconds;
    private final int maxPlayers;
    private final String motd;
    private final int countdownSeconds;
    private final int invulnerableSeconds;
    private final double borderSize;
    private final double borderGraceDistance;
    private final double borderGraceDamagePerSecond;
    private final double borderRampPerBlock;
    private final long borderDamageIntervalTicks;
    private final double scatterRadius;
    private final int dropHeight;
    private final int disconnectGraceSeconds;
    private final int combatLogSeconds;
    private final int maxDisconnects;
    private final double compassMinDistance;
    private final int feastMinutes;
    private final int feastCircleMinutes;
    private final int feastRadius;
    private final int feastChests;
    private final int feastSpawnRadius;
    private final int maxBuildHeight;
    private final int buildAboveTerrain;
    private final int demomanMines;
    private final int horsemanHayBales;
    private final double horsemanSpeed;
    private final double horsemanJumpStrength;
    private final double horsemanHealth;
    private final double demomanExplosionPower;
    private final boolean demomanBreaksBlocks;
    private final double turtleCrouchDamage;
    private final double turtleBlockingDamage;
    private final double tankExplosionPower;
    private final boolean tankBreaksBlocks;
    private final int kayaStartingBlocks;
    private final double hermitMinDistance;
    private final int thorHighStrikeY;
    private final double thorRadius;
    private final double thorHighPower;
    private final double thorLowPower;
    private final double thorNetherrackTntFraction;
    private final int thorUses;
    private final int thorCooldownSeconds;
    private final double pyroFireballSpeed;
    private final double pyroIgniteRadius;
    private final int pyroBurnSeconds;
    private final double pyroExplosionPower;
    private final boolean pyroBreaksBlocks;
    private final double viperPoisonChance;
    private final int viperPoisonSeconds;
    private final int reaperWitherSeconds;
    private final int spidermanBurst;
    private final int spidermanCooldownSeconds;
    private final int spidermanWebSpeedLevel;
    private final double vampirePlayerKillHeal;
    private final double vampireMobKillHeal;
    private final double vampireVialHeal;
    private final double vampireVialDamage;
    private final double vampireInvertHealth;
    private final double grapplerPower;
    private final int grapplerBurst;
    private final int grapplerCooldownSeconds;
    private final int grapplerDurabilityPerPull;
    private final int soulstealerHuntSeconds;
    private final int soulstealerKillSeconds;
    private final double soulstealerDamageMultiplier;
    private final int gamblerCooldownSeconds;
    private final int gladiatorHeight;
    private final int gladiatorSealSeconds;
    private final int gladiatorLootSeconds;
    private final double gladiatorReturnRadius;
    private final int gladiatorCooldownSeconds;
    private final int monkCooldownSeconds;
    private final int hadesMaxMinions;
    private final double flashMaxDistance;
    private final int flashCooldownSeconds;
    private final double snailSlowChance;
    private final int snailSlowSeconds;
    private final int snailSlowLevel;
    private final int switcherCooldownSeconds;
    private final int wispClonesPerCream;
    private final int wispCloneLifetimeSeconds;
    private final double wispKillerDamage;
    private final double hulkThrowPower;
    private final double hulkThrowLift;
    private final double hulkThrowLiftMax;
    private final int hulkCooldownSeconds;
    private final double hulkChargeSeconds;
    private final double hulkChargedMultiplier;
    private final int cannibalFoodPerHit;
    private final float cannibalSaturationPerHit;
    private final int cannibalHungerSeconds;
    private final int cannibalHungerLevel;
    private final int berserkerMobStrengthLevel;
    private final int berserkerMobSeconds;
    private final int berserkerPlayerStrengthLevel;
    private final int berserkerPlayerSeconds;
    private final double anchorStepDistance;
    private final double anchorStepVolume;
    private final int endermageReach;
    private final int endermageImmunitySeconds;
    private final int endermageCooldownSeconds;
    private final int endgameMinutes;
    private final int endgameWarningMinutes;
    private final int endgameHeight;
    private final int endgameRadius;
    private final int endgameWallHeight;
    private final int endgamePourDelaySeconds;
    private final int endgamePourIntervalSeconds;
    private final int endgameArrivalImmunitySeconds;
    private final int diggerEggs;
    private final long diggerFuseTicks;
    private final int diggerRadius;
    private final int diggerDepth;
    private final double spyAlertRadius;
    private final double spyLookRange;
    private final long spySweepTicks;
    private final double poseidonWaterDamageMultiplier;
    private final int poseidonLandSlownessSeconds;
    private final int poseidonLandSlownessLevel;
    private final double fishermanMaxDistance;
    private final double fishermanHookDamage;
    private final double fishermanArcHeight;
    private final int fishermanCooldownSeconds;
    private final int barbarianKillXp;
    private final int barbarianKillXpStep;
    private final int barbarianMobKillXp;
    private final List<SwordTier> barbarianTiers;
    private final boolean disableSweep;
    private final boolean sprintCrits;
    private final boolean noPearlCooldown;
    private final boolean keepSprint;
    private final String combatLogKillMessage;
    private final String combatLogMessage;
    private final boolean disableShields;
    private final int regenIntervalSeconds;
    private final boolean legacyKnockback;
    private final double knockbackHorizontal;
    private final double knockbackVertical;
    private final double knockbackVerticalLimit;
    private final double knockbackExtraHorizontal;
    private final double knockbackExtraVertical;
    private final int poseidonWaterStrengthLevel;
    private final int ninjaMarkSeconds;
    private final int ninjaCooldownSeconds;
    private final int launcherSponges;
    private final double launcherPower;
    private final int launcherMaxStack;
    private final double launcherSidewaysPower;
    private final int launcherFallImmunitySeconds;
    private final int jellyfishSeconds;
    private final int jellyfishMaxActive;
    private final double launcherRestitution;
    private final double cookieGrassChance;
    private final int cookieFood;
    private final double cookieHeal;
    private final int cookieSpeedSeconds;
    private final int cookieSpeedLevel;
    private final double timelordRadius;
    private final int timelordDurationSeconds;
    private final int timelordCooldownSeconds;
    private final double kangarooJumpPower;
    private final double kangarooForwardPower;
    private final int kangarooCooldownSeconds;
    private final int kangarooFallImmunitySeconds;
    private final int jackhammerUses;
    private final int jackhammerCooldownSeconds;
    private final long jackhammerIntervalTicks;
    private final boolean jackhammerDropsBlocks;
    private final double stomperFallCap;
    private final double stomperRadius;
    private final double stomperSneakCap;
    private final double soupHeal;
    private final int soupFood;
    private final float soupSaturation;
    private final int winPlatformHeight;
    private final int winChants;
    private final int winChantIntervalSeconds;
    private final int winCloseDelaySeconds;
    private final long winFireworkIntervalTicks;
    private final boolean restartOnReset;
    private final boolean freshWorldOnRestart;
    private final boolean webEnabled;
    private final String webBind;
    private final int webPort;
    private final boolean worldgenNoOceans;
    private final int swampMushroomsPerChunk;
    private final int forestMushroomsPerChunk;
    private final int lateJoinCutoffSeconds;
    private final double borderHardWallMargin;
    private final double borderForcefieldWarningDistance;
    private final int viewDistance;
    private final int simulationDistance;
    private final boolean pregenEnabled;
    private final int pregenMarginChunks;
    private final int pregenParallel;
    private final int pregenParallelDuringMatch;
    private final int jellyfishCooldownSeconds;
    private final boolean watchdogEnabled;
    private final double watchdogMaxReach;
    private final int watchdogHoverSeconds;

    /** Resolved lazily on first use, once the world is guaranteed to be loaded. */
    private Location cachedCenter;

    public GameConfig(FileConfiguration c) {
        this.worldName = c.getString("world", "");
        this.configuredCenterX = c.getInt("center.x", 0);
        this.configuredCenterZ = c.getInt("center.z", 0);
        // Both default OFF since the no-oceans worldgen: with the whole map generating as
        // land there is nothing to relocate away from, and the game wants one fixed frame —
        // 0,0 the middle, ±500 the edges, the feast inside its radius of the same origin.
        this.centerAutoLand = c.getBoolean("center.auto-land", false);
        this.centerPreferSwamp = c.getBoolean("center.prefer-swamp", false);
        this.centerSearchRadius = c.getInt("center.search-radius", 6000);
        this.minPlayers = c.getInt("min-players", 2);
        this.busyPlayers = c.getInt("busy-players", 20);
        this.busyCountdownSeconds = c.getInt("busy-countdown-seconds", 60);
        this.maxPlayers = c.getInt("max-players", 120);
        this.motd = c.getString("motd", "Minecraft Hardcore Games");
        this.countdownSeconds = c.getInt("countdown-seconds", 300);
        this.invulnerableSeconds = c.getInt("invulnerable-seconds", 120);
        this.borderSize = c.getDouble("border.size", 1000.0D);
        this.borderGraceDistance = c.getDouble("border.grace-distance", 10.0D);
        this.borderGraceDamagePerSecond = c.getDouble("border.grace-damage-per-second", 1.0D);
        this.borderRampPerBlock = c.getDouble("border.ramp-per-block", 2.0D);
        this.borderDamageIntervalTicks = c.getLong("border.damage-interval-ticks", 10L);
        this.scatterRadius = c.getDouble("scatter-radius", 50.0D);
        this.dropHeight = c.getInt("drop-height", 20);
        this.disconnectGraceSeconds = c.getInt("disconnect-grace-seconds", 60);
        this.combatLogSeconds = c.getInt("combat-log-seconds", 3);
        this.maxDisconnects = c.getInt("max-disconnects", 3);
        this.compassMinDistance = c.getDouble("compass-min-distance", 25.0D);
        this.feastMinutes = c.getInt("feast.minutes", 22);
        this.feastCircleMinutes = c.getInt("feast.circle-minutes", 17);
        this.feastRadius = c.getInt("feast.radius", 15);
        this.feastChests = c.getInt("feast.chests", 12);
        this.feastSpawnRadius = c.getInt("feast.spawn-radius", 200);
        this.maxBuildHeight = c.getInt("build.max-height", 140);
        this.buildAboveTerrain = c.getInt("build.above-terrain", 6);
        this.demomanMines = c.getInt("kits.demoman.mines", 2);
        this.horsemanHayBales = c.getInt("kits.horseman.hay-bales", 2);
        this.horsemanSpeed = c.getDouble("kits.horseman.speed", 0.3D);
        this.horsemanJumpStrength = c.getDouble("kits.horseman.jump-strength", 0.7D);
        this.horsemanHealth = c.getDouble("kits.horseman.health", 30.0D);
        this.demomanExplosionPower = c.getDouble("kits.demoman.explosion-power", 4.0D);
        this.demomanBreaksBlocks = c.getBoolean("kits.demoman.break-blocks", true);
        this.turtleCrouchDamage = c.getDouble("kits.turtle.crouch-damage", 2.0D);
        this.turtleBlockingDamage = c.getDouble("kits.turtle.blocking-damage", 1.0D);
        this.tankExplosionPower = c.getDouble("kits.tank.explosion-power", 3.0D);
        this.tankBreaksBlocks = c.getBoolean("kits.tank.break-blocks", false);
        this.kayaStartingBlocks = c.getInt("kits.kaya.starting-blocks", 8);
        this.hermitMinDistance = c.getDouble("kits.hermit.min-distance", 300.0D);
        this.thorHighStrikeY = c.getInt("kits.thor.high-strike-y", 80);
        this.thorRadius = c.getDouble("kits.thor.radius", 4.0D);
        this.thorHighPower = c.getDouble("kits.thor.high-power", 1.2D);
        this.thorLowPower = c.getDouble("kits.thor.low-power", 0.7D);
        this.thorNetherrackTntFraction = c.getDouble("kits.thor.netherrack-tnt-fraction", 0.5D);
        this.thorUses = c.getInt("kits.thor.uses", 3);
        this.thorCooldownSeconds = c.getInt("kits.thor.cooldown-seconds", 5);
        this.pyroFireballSpeed = c.getDouble("kits.pyro.fireball-speed", 1.2D);
        this.pyroIgniteRadius = c.getDouble("kits.pyro.ignite-radius", 3.0D);
        this.pyroBurnSeconds = c.getInt("kits.pyro.burn-seconds", 5);
        this.pyroExplosionPower = c.getDouble("kits.pyro.explosion-power", 1.5D);
        this.pyroBreaksBlocks = c.getBoolean("kits.pyro.breaks-blocks", true);
        this.viperPoisonChance = c.getDouble("kits.viper.poison-chance", 0.33D);
        this.viperPoisonSeconds = c.getInt("kits.viper.poison-seconds", 5);
        this.reaperWitherSeconds = c.getInt("kits.reaper.wither-seconds", 5);
        this.spidermanBurst = c.getInt("kits.spiderman.burst", 3);
        this.spidermanCooldownSeconds = c.getInt("kits.spiderman.cooldown-seconds", 30);
        this.spidermanWebSpeedLevel = c.getInt("kits.spiderman.web-speed-level", 4);
        this.vampirePlayerKillHeal = c.getDouble("kits.vampire.player-kill-heal", 6.0D);
        this.vampireMobKillHeal = c.getDouble("kits.vampire.mob-kill-heal", 2.0D);
        this.vampireVialHeal = c.getDouble("kits.vampire.vial-heal", 4.0D);
        this.vampireVialDamage = c.getDouble("kits.vampire.vial-damage", 2.0D);
        this.vampireInvertHealth = c.getDouble("kits.vampire.invert-amount", 4.0D);
        this.grapplerPower = c.getDouble("kits.grappler.power", 1.6D);
        this.grapplerBurst = c.getInt("kits.grappler.burst", 3);
        this.grapplerCooldownSeconds = c.getInt("kits.grappler.cooldown-seconds", 3);
        this.grapplerDurabilityPerPull = c.getInt("kits.grappler.durability-per-pull", 4);
        this.soulstealerHuntSeconds = c.getInt("kits.soulstealer.hunt-seconds", 10);
        this.soulstealerKillSeconds = c.getInt("kits.soulstealer.kill-seconds", 10);
        this.soulstealerDamageMultiplier = c.getDouble("kits.soulstealer.damage-multiplier", 0.4D);
        this.gamblerCooldownSeconds = c.getInt("kits.gambler.cooldown-seconds", 10);
        this.gladiatorHeight = c.getInt("kits.gladiator.height", 230);
        this.gladiatorSealSeconds = c.getInt("kits.gladiator.seal-seconds", 60);
        this.gladiatorLootSeconds = c.getInt("kits.gladiator.loot-seconds", 15);
        this.gladiatorReturnRadius = c.getDouble("kits.gladiator.return-radius", 50.0D);
        this.gladiatorCooldownSeconds = c.getInt("kits.gladiator.cooldown-seconds", 90);
        this.monkCooldownSeconds = c.getInt("kits.monk.cooldown-seconds", 5);
        this.hadesMaxMinions = c.getInt("kits.hades.max-minions", 5);
        this.flashMaxDistance = c.getDouble("kits.flash.max-distance", 60.0D);
        this.flashCooldownSeconds = c.getInt("kits.flash.cooldown-seconds", 150);
        this.snailSlowChance = c.getDouble("kits.snail.slow-chance", 0.33D);
        this.snailSlowSeconds = c.getInt("kits.snail.slowness-seconds", 5);
        this.snailSlowLevel = c.getInt("kits.snail.slowness-level", 2);
        this.switcherCooldownSeconds = c.getInt("kits.switcher.cooldown-seconds", 10);
        this.wispClonesPerCream = c.getInt("kits.wisp.clones-per-cream", 5);
        this.wispCloneLifetimeSeconds = c.getInt("kits.wisp.clone-lifetime-seconds", 60);
        this.wispKillerDamage = c.getDouble("kits.wisp.killer-damage", 2.0D);
        this.hulkThrowPower = c.getDouble("kits.hulk.throw-power", 1.6D);
        this.hulkThrowLift = c.getDouble("kits.hulk.throw-lift", 0.6D);
        this.hulkThrowLiftMax = c.getDouble("kits.hulk.throw-lift-max", 0.8D);
        this.hulkCooldownSeconds = c.getInt("kits.hulk.cooldown-seconds", 5);
        this.hulkChargeSeconds = c.getDouble("kits.hulk.charge-seconds", 2.0D);
        this.hulkChargedMultiplier = c.getDouble("kits.hulk.charged-multiplier", 2.5D);
        this.cannibalFoodPerHit = c.getInt("kits.cannibal.food-per-hit", 2);
        this.cannibalSaturationPerHit =
                (float) c.getDouble("kits.cannibal.saturation-per-hit", 1.0D);
        this.cannibalHungerSeconds = c.getInt("kits.cannibal.hunger-seconds", 30);
        this.cannibalHungerLevel = c.getInt("kits.cannibal.hunger-level", 1);
        this.berserkerMobStrengthLevel = c.getInt("kits.berserker.mob-kill.strength-level", 1);
        this.berserkerMobSeconds = c.getInt("kits.berserker.mob-kill.seconds", 10);
        this.berserkerPlayerStrengthLevel = c.getInt("kits.berserker.player-kill.strength-level", 2);
        this.berserkerPlayerSeconds = c.getInt("kits.berserker.player-kill.seconds", 15);
        this.anchorStepDistance = c.getDouble("kits.anchor.step-sound-distance", 2.5D);
        this.anchorStepVolume = c.getDouble("kits.anchor.step-sound-volume", 1.0D);
        this.endermageReach = c.getInt("kits.endermage.reach", 2);
        this.endermageImmunitySeconds = c.getInt("kits.endermage.immunity-seconds", 5);
        this.endermageCooldownSeconds = c.getInt("kits.endermage.cooldown-seconds", 30);
        this.endgameMinutes = c.getInt("endgame.minutes", 60);
        this.endgameWarningMinutes = c.getInt("endgame.warning-minutes", 55);
        this.endgameHeight = c.getInt("endgame.height", 180);
        this.endgameRadius = c.getInt("endgame.radius", 10);
        this.endgameWallHeight = c.getInt("endgame.wall-height", 30);
        this.endgamePourDelaySeconds = c.getInt("endgame.pour-delay-seconds", 40);
        this.endgamePourIntervalSeconds = c.getInt("endgame.pour-interval-seconds", 5);
        this.endgameArrivalImmunitySeconds = c.getInt("endgame.arrival-immunity-seconds", 3);
        this.diggerEggs = c.getInt("kits.digger.eggs", 6);
        this.diggerFuseTicks = c.getLong("kits.digger.fuse-ticks", 30L);
        this.diggerRadius = c.getInt("kits.digger.radius", 2);
        this.diggerDepth = c.getInt("kits.digger.depth", 8);
        this.spyAlertRadius = c.getDouble("kits.spy.alert-radius", 40.0D);
        this.spyLookRange = c.getDouble("kits.spy.look-range", 96.0D);
        this.spySweepTicks = c.getLong("kits.spy.sweep-ticks", 10L);
        this.poseidonWaterDamageMultiplier = c.getDouble("kits.poseidon.water-damage-multiplier", 1.6D);
        this.poseidonLandSlownessSeconds = c.getInt("kits.poseidon.land-slowness-seconds", 5);
        this.poseidonLandSlownessLevel = c.getInt("kits.poseidon.land-slowness-level", 1);
        this.fishermanMaxDistance = c.getDouble("kits.fisherman.max-distance", 32.0D);
        this.fishermanHookDamage = c.getDouble("kits.fisherman.hook-hit-damage", 1.0D);
        this.fishermanArcHeight = c.getDouble("kits.fisherman.arc-height", 1.2D);
        this.fishermanCooldownSeconds = c.getInt("kits.fisherman.cooldown-seconds", 2);
        this.barbarianKillXp = c.getInt("kits.barbarian.kill-xp", 25);
        this.barbarianKillXpStep = c.getInt("kits.barbarian.kill-xp-step", 25);
        this.barbarianMobKillXp = c.getInt("kits.barbarian.mob-kill-xp", 14);
        this.barbarianTiers = readSwordTiers(c);
        this.disableSweep = c.getBoolean("combat.disable-sweep", true);
        this.sprintCrits = c.getBoolean("combat.sprint-crits", true);
        this.noPearlCooldown = c.getBoolean("combat.no-pearl-cooldown", true);
        this.keepSprint = c.getBoolean("combat.keep-sprint", true);
        this.combatLogKillMessage = c.getString("messages.combat-log-kill",
                "{victim} logged out to escape {killer}, and lost anyway.");
        this.combatLogMessage = c.getString("messages.combat-log",
                "{victim} logged out mid fight and was eliminated.");
        this.disableShields = c.getBoolean("combat.disable-shields", true);
        this.regenIntervalSeconds = c.getInt("combat.regen-interval-seconds", 4);
        this.legacyKnockback = c.getBoolean("combat.knockback.enabled", true);
        this.knockbackHorizontal = c.getDouble("combat.knockback.horizontal", 0.4D);
        this.knockbackVertical = c.getDouble("combat.knockback.vertical", 0.4D);
        this.knockbackVerticalLimit = c.getDouble("combat.knockback.vertical-limit", 0.4D);
        this.knockbackExtraHorizontal = c.getDouble("combat.knockback.extra-horizontal", 0.5D);
        this.knockbackExtraVertical = c.getDouble("combat.knockback.extra-vertical", 0.1D);
        this.poseidonWaterStrengthLevel = c.getInt("kits.poseidon.water-strength-level", 1);
        this.ninjaMarkSeconds = c.getInt("kits.ninja.mark-seconds", 10);
        this.ninjaCooldownSeconds = c.getInt("kits.ninja.cooldown-seconds", 7);
        this.launcherSponges = c.getInt("kits.launcher.sponges", 20);
        this.launcherPower = c.getDouble("kits.launcher.power", 0.7D);
        this.launcherMaxStack = c.getInt("kits.launcher.max-stack", 4);
        this.launcherSidewaysPower = c.getDouble("kits.launcher.sideways-power", 0.6D);
        this.launcherFallImmunitySeconds = c.getInt("kits.launcher.fall-immunity-seconds", 15);
        this.jellyfishSeconds = c.getInt("kits.jellyfish.duration-seconds", 3);
        this.jellyfishMaxActive = c.getInt("kits.jellyfish.max-active", 6);
        this.launcherRestitution = c.getDouble("kits.launcher.restitution", 1.15D);
        this.cookieGrassChance = c.getDouble("kits.cookiemonster.grass-drop-chance", 0.2D);
        this.cookieFood = c.getInt("kits.cookiemonster.food", 2);
        this.cookieHeal = c.getDouble("kits.cookiemonster.heal", 2.0D);
        this.cookieSpeedSeconds = c.getInt("kits.cookiemonster.speed-seconds", 5);
        this.cookieSpeedLevel = c.getInt("kits.cookiemonster.speed-level", 2);
        this.timelordRadius = c.getDouble("kits.timelord.radius", 6.0D);
        this.timelordDurationSeconds = c.getInt("kits.timelord.duration-seconds", 10);
        this.timelordCooldownSeconds = c.getInt("kits.timelord.cooldown-seconds", 30);
        this.kangarooJumpPower = c.getDouble("kits.kangaroo.jump-power", 1.0D);
        this.kangarooForwardPower = c.getDouble("kits.kangaroo.forward-power", 0.5D);
        this.kangarooCooldownSeconds = c.getInt("kits.kangaroo.cooldown-seconds", 5);
        this.kangarooFallImmunitySeconds = c.getInt("kits.kangaroo.fall-immunity-seconds", 8);
        this.jackhammerUses = c.getInt("kits.jackhammer.uses", 5);
        this.jackhammerCooldownSeconds = c.getInt("kits.jackhammer.cooldown-seconds", 30);
        this.jackhammerIntervalTicks = c.getLong("kits.jackhammer.interval-ticks", 2L);
        this.jackhammerDropsBlocks = c.getBoolean("kits.jackhammer.drop-blocks", true);
        this.stomperFallCap = c.getDouble("kits.stomper.fall-damage-cap", 4.0D);
        this.stomperRadius = c.getDouble("kits.stomper.radius", 5.0D);
        this.stomperSneakCap = c.getDouble("kits.stomper.sneak-cap", 4.0D);
        this.soupHeal = c.getDouble("soup.heal", 7.0D);
        this.soupFood = c.getInt("soup.food", 6);
        this.soupSaturation = (float) c.getDouble("soup.saturation", 7.2D);
        this.winPlatformHeight = c.getInt("win.platform-height", 25);
        this.winChants = c.getInt("win.chants", 5);
        this.winChantIntervalSeconds = c.getInt("win.chant-interval-seconds", 2);
        this.winCloseDelaySeconds = c.getInt("win.close-delay-seconds", 5);
        this.winFireworkIntervalTicks = c.getLong("win.firework-interval-ticks", 10L);
        this.restartOnReset = c.getBoolean("restart-on-reset", true);
        this.freshWorldOnRestart = c.getBoolean("fresh-world-on-restart", true);
        this.webEnabled = c.getBoolean("web.enabled", true);
        this.webBind = c.getString("web.bind", "127.0.0.1");
        this.webPort = c.getInt("web.port", 8085);
        this.worldgenNoOceans = c.getBoolean("worldgen.no-oceans", true);
        this.swampMushroomsPerChunk = c.getInt("worldgen.swamp-mushrooms-per-chunk", 6);
        this.forestMushroomsPerChunk = c.getInt("worldgen.forest-mushrooms-per-chunk", 4);
        this.lateJoinCutoffSeconds = c.getInt("late-join-cutoff-seconds", 5);
        this.borderHardWallMargin = c.getDouble("border.hard-wall-margin", 20.0D);
        this.borderForcefieldWarningDistance =
                c.getDouble("border.forcefield-warning-distance", 25.0D);
        this.viewDistance = c.getInt("performance.view-distance", 8);
        this.simulationDistance = c.getInt("performance.simulation-distance", 6);
        this.pregenEnabled = c.getBoolean("pregen.enabled", true);
        this.pregenMarginChunks = c.getInt("pregen.margin-chunks", 2);
        this.pregenParallel = c.getInt("pregen.parallel", 8);
        this.pregenParallelDuringMatch = c.getInt("pregen.parallel-during-match", 2);
        this.jellyfishCooldownSeconds = c.getInt("kits.jellyfish.cooldown-seconds", 4);
        this.watchdogEnabled = c.getBoolean("watchdog.enabled", true);
        this.watchdogMaxReach = c.getDouble("watchdog.max-reach", 5.2);
        this.watchdogHoverSeconds = c.getInt("watchdog.hover-seconds", 4);
    }

    /**
     * Reads the Barbarian's sword ladder.
     *
     * <p>A rung is {@code {xp, material, sharpness}}; sharpness is optional. Unreadable rungs
     * are skipped with a warning rather than taking the game down, and a list that yields
     * nothing at all falls back to the shipped ladder — a Barbarian with no sword to grow is
     * worse than one growing the wrong sword.
     *
     * <p>Sorted by cost, so the ladder is well-ordered however it was written.
     */
    private static List<SwordTier> readSwordTiers(FileConfiguration c) {
        List<SwordTier> tiers = new ArrayList<>();
        for (Map<?, ?> entry : c.getMapList("kits.barbarian.tiers")) {
            Object rawMaterial = entry.get("material");
            Material material = rawMaterial == null ? null : Material.matchMaterial(rawMaterial.toString());
            if (material == null) {
                Bukkit.getLogger().warning("[HardcoreGames] Unknown Barbarian sword material '"
                        + rawMaterial + "' — skipping that tier.");
                continue;
            }
            tiers.add(new SwordTier(intOf(entry.get("xp")), material, intOf(entry.get("sharpness"))));
        }
        if (tiers.isEmpty()) {
            return DEFAULT_SWORD_TIERS;
        }
        tiers.sort(Comparator.comparingInt(SwordTier::xp));
        return List.copyOf(tiers);
    }

    /** Missing or non-numeric config values read as zero. */
    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * The world the game runs in.
     *
     * <p>Blank config means "whatever the server booted into", which is what world rotation
     * needs — {@link com.hardcorekits.world.WorldRotator} renames the folder on every restart,
     * so a hardcoded name would go stale after the first game.
     */
    public World world() {
        if (!worldName.isBlank()) {
            World named = Bukkit.getWorld(worldName);
            if (named != null) {
                return named;
            }
            Bukkit.getLogger().warning("[HardcoreGames] Configured world '" + worldName
                    + "' is not loaded — falling back to the server's main world.");
        }
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            throw new IllegalStateException("The server has no worlds loaded");
        }
        return worlds.get(0);
    }

    /**
     * Centre of the map, snapped to the surface.
     *
     * <p>Cached — resolving the surface height is a chunk lookup and this is read often.
     */
    public Location center() {
        if (cachedCenter == null) {
            World world = world();
            cachedCenter = centerAutoLand ? resolveLandCentre(world) : surfaceAt(world, configuredCenterX, configuredCenterZ);
        }
        return cachedCenter.clone();
    }

    /**
     * Moves the play area onto a landmass when the configured centre lands in water.
     *
     * <p>This cannot make the world contain less ocean — terrain height and sea level come from
     * the noise generator, not from biomes, so no plugin-side biome choice removes water. What
     * it can do is stop the 1000-block box being centred on a random patch of sea: the whole
     * game only ever happens within that box, so putting the box on land is the part that
     * matters in practice.
     *
     * <p>Cheap by design. {@code locateNearestBiome} reads the biome source without generating
     * chunks, so the only real cost is one height lookup per candidate.
     */
    private Location resolveLandCentre(World world) {
        // Swamps first, when asked for: they are where the mushrooms are, and mushroom stew is
        // the healing economy. Centring the play area on one puts soup within reach of the
        // fighting instead of leaving it to whoever happened to land near a swamp.
        if (centerPreferSwamp) {
            Location swamp = nearestOf(world, SWAMP_BIOMES);
            if (swamp != null) {
                Bukkit.getLogger().info("[HardcoreGames] Centre set on a swamp at "
                        + swamp.getBlockX() + ", " + swamp.getBlockZ() + ".");
                return swamp;
            }
        }

        if (isDryLand(world, configuredCenterX, configuredCenterZ)) {
            return surfaceAt(world, configuredCenterX, configuredCenterZ);
        }

        Location land = nearestOf(world, INLAND_BIOMES);
        if (land != null) {
            Bukkit.getLogger().info("[HardcoreGames] Centre moved to dry land at "
                    + land.getBlockX() + ", " + land.getBlockZ()
                    + " (configured " + configuredCenterX + ", " + configuredCenterZ + " was water).");
            return land;
        }

        Bukkit.getLogger().warning("[HardcoreGames] No dry land found near the configured centre; "
                + "using it anyway. Expect a wet map.");
        return surfaceAt(world, configuredCenterX, configuredCenterZ);
    }

    /** Nearest of these biomes that is actually dry, or null if there is no such thing nearby. */
    private Location nearestOf(World world, Biome[] wanted) {
        Location from = new Location(world, configuredCenterX, world.getSeaLevel(), configuredCenterZ);
        try {
            BiomeSearchResult found = world.locateNearestBiome(from, centerSearchRadius, wanted);
            if (found == null) {
                return null;
            }
            int x = found.getLocation().getBlockX();
            int z = found.getLocation().getBlockZ();
            return isDryLand(world, x, z) ? surfaceAt(world, x, z) : null;
        } catch (IllegalArgumentException e) {
            return null; // none of those biomes exist in this world's source
        }
    }

    /** The top block of a column is liquid if that column is ocean, river or a lava lake. */
    private boolean isDryLand(World world, int x, int z) {
        return !world.getHighestBlockAt(x, z).isLiquid();
    }

    private Location surfaceAt(World world, int x, int z) {
        return new Location(world, x + 0.5D, world.getHighestBlockYAt(x, z) + 1, z + 0.5D);
    }

    /**
     * X of the centre the match actually uses.
     *
     * <p>This is the <em>resolved</em> centre, not the number in config.yml. Those differ
     * whenever auto-land moves the play area onto land, and they can differ by thousands of
     * blocks — so anything measuring against the middle of the map (the border, the feast, the
     * drop, the End Game box) has to read it from here. Reading the configured value instead
     * put the boundary in one place and the players in another, and everyone alive was
     * instantly outside a border they could not see.
     */
    public int centerX() {
        return center().getBlockX();
    }

    /** Z of the resolved centre. See {@link #centerX()}. */
    public int centerZ() {
        return center().getBlockZ();
    }

    public int minPlayers() {
        return minPlayers;
    }

    /** At this many participants the countdown drops to the short fuse. */
    public int busyPlayers() {
        return busyPlayers;
    }

    public int busyCountdownSeconds() {
        return busyCountdownSeconds;
    }

    /**
     * Server slots.
     *
     * <p>Applied at enable rather than left to server.properties, because the shutdown rewrites
     * that file for world rotation — one authoritative place beats two that can drift.
     */
    public int maxPlayers() {
        return maxPlayers;
    }

    /** The line under the server name in the multiplayer list. Applied at boot, like the slots. */
    public String motd() {
        return motd;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public int invulnerableSeconds() {
        return invulnerableSeconds;
    }

    /** Full width of the play area; the bounds are +/- half this around {@link #center()}. */
    public double borderSize() {
        return borderSize;
    }

    /** How far past the edge stays a survivable warning rather than a death sentence. */
    public double borderGraceDistance() {
        return borderGraceDistance;
    }

    /** Damage per second inside the grace band. 1.0 = half a heart a second. */
    public double borderGraceDamagePerSecond() {
        return borderGraceDamagePerSecond;
    }

    /** Damage per second added for every block past the grace band. */
    public double borderRampPerBlock() {
        return borderRampPerBlock;
    }

    public long borderDamageIntervalTicks() {
        return borderDamageIntervalTicks;
    }

    public double scatterRadius() {
        return scatterRadius;
    }

    public int dropHeight() {
        return dropHeight;
    }

    public int disconnectGraceSeconds() {
        return disconnectGraceSeconds;
    }

    /** Disconnecting within this many seconds of taking damage counts as being killed. */
    public int combatLogSeconds() {
        return combatLogSeconds;
    }

    /** Disconnecting more times than this in one game forfeits the match. */
    public int maxDisconnects() {
        return maxDisconnects;
    }

    public double compassMinDistance() {
        return compassMinDistance;
    }

    public int feastMinutes() {
        return feastMinutes;
    }

    public int feastCircleMinutes() {
        return feastCircleMinutes;
    }

    public int feastRadius() {
        return feastRadius;
    }

    public int feastChests() {
        return feastChests;
    }

    /** How far from centre the feast may be sited. Clamped to the border. */
    public int feastSpawnRadius() {
        return feastSpawnRadius;
    }

    /** Highest Y a player may place a block at. Absolute, not relative to the terrain. */
    public int maxBuildHeight() {
        return maxBuildHeight;
    }

    /** Blocks allowed over the natural ground where that ground is already above the cap. */
    public int buildAboveTerrain() {
        return buildAboveTerrain;
    }

    /** Blast strength of a Demoman mine. Vanilla TNT is 4.0. */
    /** Gravel and pressure plates in the starting kit, one mine per pair. */
    public int horsemanHayBales() {
        return horsemanHayBales;
    }

    public double horsemanSpeed() {
        return horsemanSpeed;
    }

    public double horsemanJumpStrength() {
        return horsemanJumpStrength;
    }

    public double horsemanHealth() {
        return horsemanHealth;
    }

    public int demomanMines() {
        return demomanMines;
    }

    public double demomanExplosionPower() {
        return demomanExplosionPower;
    }

    public boolean demomanBreaksBlocks() {
        return demomanBreaksBlocks;
    }

    /**
     * Whether stealing a kit also hands over its starting equipment.
     *
     * <p>On by default: several kits are nothing but their items, so a Demoman or Beastmaster
     * copied without them would be an empty prize.
     */
    /** Most a crouching Turtle takes from one hit. 2.0 = one heart. */
    public double turtleCrouchDamage() {
        return turtleCrouchDamage;
    }

    /** Most a crouching Turtle takes with a shield up. 1.0 = half a heart. */
    public double turtleBlockingDamage() {
        return turtleBlockingDamage;
    }

    /** Blast a Tank's kill sets off. Vanilla TNT is 4.0, so the default is a shade smaller. */
    public double tankExplosionPower() {
        return tankExplosionPower;
    }

    /** Whether a Tank's kill craters the ground. Off by default — every corpse would dig a pit. */
    public boolean tankBreaksBlocks() {
        return tankBreaksBlocks;
    }

    /** Grass blocks a Kaya starts with. More are crafted from dirt and seeds. */
    public int kayaStartingBlocks() {
        return kayaStartingBlocks;
    }

    /** How far out a Hermit lands when the map has no swamp, jungle or desert to offer. */
    public double hermitMinDistance() {
        return hermitMinDistance;
    }

    /** Above this Y a Thor strike also leaves burning netherrack and shoves harder. */
    public int thorHighStrikeY() {
        return thorHighStrikeY;
    }

    /** Radius of the shove around a Thor strike. */
    public double thorRadius() {
        return thorRadius;
    }

    public double thorHighPower() {
        return thorHighPower;
    }

    public double thorLowPower() {
        return thorLowPower;
    }

    /** Netherrack blast strength, as a fraction of TNT. */
    public double thorNetherrackTntFraction() {
        return thorNetherrackTntFraction;
    }

    /** Strikes a Thor gets before needing to rest. */
    public int thorUses() {
        return thorUses;
    }

    /** Seconds of rest once those strikes are spent. */
    public int thorCooldownSeconds() {
        return thorCooldownSeconds;
    }

    /** Strength level a Berserker gets for a mob kill. 1 = Strength I. */
    /** How hard a thrown fire charge is launched. */
    public double pyroFireballSpeed() {
        return pyroFireballSpeed;
    }

    /** Blocks around the impact that catch fire. */
    public double pyroIgniteRadius() {
        return pyroIgniteRadius;
    }

    /** Seconds everything caught in that radius burns for. */
    public int pyroBurnSeconds() {
        return pyroBurnSeconds;
    }

    /** The landing's blast. 1.5 hurts and shoves without one-shotting; TNT is 4.0. */
    public double pyroExplosionPower() {
        return pyroExplosionPower;
    }

    public boolean pyroBreaksBlocks() {
        return pyroBreaksBlocks;
    }

    public double viperPoisonChance() {
        return viperPoisonChance;
    }

    public int viperPoisonSeconds() {
        return viperPoisonSeconds;
    }

    public int reaperWitherSeconds() {
        return reaperWitherSeconds;
    }

    /** Webs thrown back-to-back before the arm needs its rest. */
    public int spidermanBurst() {
        return spidermanBurst;
    }

    public int spidermanCooldownSeconds() {
        return spidermanCooldownSeconds;
    }

    /** Speed level worn in webbing. High, because the web's slow applies after speed does. */
    public int spidermanWebSpeedLevel() {
        return spidermanWebSpeedLevel;
    }

    public double vampirePlayerKillHeal() {
        return vampirePlayerKillHeal;
    }

    public double vampireMobKillHeal() {
        return vampireMobKillHeal;
    }

    public double vampireVialHeal() {
        return vampireVialHeal;
    }

    public double vampireVialDamage() {
        return vampireVialDamage;
    }

    /** Health moved (either direction) by a full-strength splash on a Vampire. */
    public double vampireInvertHealth() {
        return vampireInvertHealth;
    }

    /** How hard the Grappler's reel pulls them along the line. */
    public double grapplerPower() {
        return grapplerPower;
    }

    /** Pulls back to back before the arm needs its rest. */
    public int grapplerBurst() {
        return grapplerBurst;
    }

    public int grapplerCooldownSeconds() {
        return grapplerCooldownSeconds;
    }

    /** Rod damage per pull. A fishing rod holds 64, so 4 is sixteen pulls a rod. */
    public int grapplerDurabilityPerPull() {
        return grapplerDurabilityPerPull;
    }

    public int soulstealerHuntSeconds() {
        return soulstealerHuntSeconds;
    }

    public int soulstealerKillSeconds() {
        return soulstealerKillSeconds;
    }

    /** The hunt swings at this fraction of normal damage. 0.4 is the classic 40%. */
    public double soulstealerDamageMultiplier() {
        return soulstealerDamageMultiplier;
    }

    /** Seconds between presses of a Gambler's button. */
    public int gamblerCooldownSeconds() {
        return gamblerCooldownSeconds;
    }

    /** Y of the Shadow Game floor. Clamped under the world ceiling at build time. */
    public int gladiatorHeight() {
        return gladiatorHeight;
    }

    /** Seconds the arena stays sealed before the walls and ceiling fall away. */
    public int gladiatorSealSeconds() {
        return gladiatorSealSeconds;
    }

    /** Seconds the winner gets to loot and heal before being returned to the world. */
    public int gladiatorLootSeconds() {
        return gladiatorLootSeconds;
    }

    /** The winner comes back within this radius of the challenge, never exactly on it. */
    public double gladiatorReturnRadius() {
        return gladiatorReturnRadius;
    }

    public int gladiatorCooldownSeconds() {
        return gladiatorCooldownSeconds;
    }

    /** Seconds between Monk disarms. */
    public int monkCooldownSeconds() {
        return monkCooldownSeconds;
    }

    /** Head-count cap on a Hades army. The wand is free; this is the limit. */
    public int hadesMaxMinions() {
        return hadesMaxMinions;
    }

    /** How far a Flash can teleport in one use. */
    public double flashMaxDistance() {
        return flashMaxDistance;
    }

    /** 150 = the classic 2:30. */
    public int flashCooldownSeconds() {
        return flashCooldownSeconds;
    }

    /** Odds that a Snail's landed hit applies its slow. */
    public double snailSlowChance() {
        return snailSlowChance;
    }

    public int snailSlowSeconds() {
        return snailSlowSeconds;
    }

    /** 2 = Slowness II, as the classic kit had it. */
    public int snailSlowLevel() {
        return snailSlowLevel;
    }

    /** Seconds between Switcher Ball throws. The ten balls are the other limit. */
    public int switcherCooldownSeconds() {
        return switcherCooldownSeconds;
    }

    public int wispClonesPerCream() {
        return wispClonesPerCream;
    }

    /** Seconds a decoy wanders before removing itself. */
    public int wispCloneLifetimeSeconds() {
        return wispCloneLifetimeSeconds;
    }

    /** Dealt to whoever kills a decoy. 2.0 is the classic one heart. */
    public double wispKillerDamage() {
        return wispKillerDamage;
    }

    /** Push along the Hulk's line of sight when they let go. */
    public double hulkThrowPower() {
        return hulkThrowPower;
    }

    /** Upward part of the throw, so the victim arcs rather than skids. */
    public double hulkThrowLift() {
        return hulkThrowLift;
    }

    /** Ceiling on the lift after the charge multiplier, so no throw is a fatal launch. */
    public double hulkThrowLiftMax() {
        return hulkThrowLiftMax;
    }

    /** Seconds after a throw before the same Hulk can grab again. */
    public int hulkCooldownSeconds() {
        return hulkCooldownSeconds;
    }

    /** Seconds of held crouch that fill the throw's charge bar. */
    public double hulkChargeSeconds() {
        return hulkChargeSeconds;
    }

    /** Throw strength at a full bar, as a multiple of the uncharged throw. */
    public double hulkChargedMultiplier() {
        return hulkChargedMultiplier;
    }

    /** Drumsticks the Cannibal gains per hit landed on a player. */
    public int cannibalFoodPerHit() {
        return cannibalFoodPerHit;
    }

    public float cannibalSaturationPerHit() {
        return cannibalSaturationPerHit;
    }

    public int cannibalHungerSeconds() {
        return cannibalHungerSeconds;
    }

    /** 1 = Hunger I, which is what rotten flesh gives. */
    public int cannibalHungerLevel() {
        return cannibalHungerLevel;
    }

    public int berserkerMobStrengthLevel() {
        return berserkerMobStrengthLevel;
    }

    public int berserkerMobSeconds() {
        return berserkerMobSeconds;
    }

    /** Strength level for a player kill — the bigger prize. */
    public int berserkerPlayerStrengthLevel() {
        return berserkerPlayerStrengthLevel;
    }

    public int berserkerPlayerSeconds() {
        return berserkerPlayerSeconds;
    }

    /** Blocks an Anchor walks between iron footstep sounds. */
    public double anchorStepDistance() {
        return anchorStepDistance;
    }

    /** How far an Anchor's footsteps carry. */
    public double anchorStepVolume() {
        return anchorStepVolume;
    }

    /** Horizontal reach of an Endermage portal. Height is ignored entirely. */
    /** Blocks out from the portal on each side; 2 is a 5x5 column. */
    public int endermageReach() {
        return endermageReach;
    }

    /** Grace given to the Endermage and everyone dragged, so nobody lands mid-swing. */
    public int endermageImmunitySeconds() {
        return endermageImmunitySeconds;
    }

    /** Seconds before the portal returns to the Endermage's inventory. */
    public int endermageCooldownSeconds() {
        return endermageCooldownSeconds;
    }

    /** Minutes into the match when everyone still alive is sealed in the box. */
    public int endgameMinutes() {
        return endgameMinutes;
    }

    /** Minutes into the match when the End Game countdown starts. */
    public int endgameWarningMinutes() {
        return endgameWarningMinutes;
    }

    /** Y of the box floor, before it is clamped to fit under the world height. */
    public int endgameHeight() {
        return endgameHeight;
    }

    /** Interior half-width of the box. 10 gives a 21x21 floor. */
    public int endgameRadius() {
        return endgameRadius;
    }

    /** Interior height of the box, floor to ceiling. */
    public int endgameWallHeight() {
        return endgameWallHeight;
    }

    /** Seconds between arriving in the box and the first lava landing. */
    public int endgamePourDelaySeconds() {
        return endgamePourDelaySeconds;
    }

    /** Seconds between each layer of lava, working down from the ceiling. */
    public int endgamePourIntervalSeconds() {
        return endgamePourIntervalSeconds;
    }

    /** Seconds of immunity on arrival, so nobody is killed mid-teleport. */
    public int endgameArrivalImmunitySeconds() {
        return endgameArrivalImmunitySeconds;
    }

    /** Dragon eggs a Digger starts with. Each one is one hole. */
    public int diggerEggs() {
        return diggerEggs;
    }

    /** Ticks between placing the egg and the floor going. 30 = a second and a half. */
    public long diggerFuseTicks() {
        return diggerFuseTicks;
    }

    /** Half-width of the shaft. 2 gives the classic 5x5. */
    public int diggerRadius() {
        return diggerRadius;
    }

    /** How far down the shaft goes. */
    public int diggerDepth() {
        return diggerDepth;
    }

    /** How close another player has to get before a Spy's compass warns them. */
    public double spyAlertRadius() {
        return spyAlertRadius;
    }

    /** How far a Spy can read someone's kit by looking at them. */
    public double spyLookRange() {
        return spyLookRange;
    }

    /** Ticks between Spy radar sweeps. 10 = twice a second. */
    public long spySweepTicks() {
        return spySweepTicks;
    }

    /** Melee damage multiplier while a Poseidon is standing in water. 1.6 = "160% strength". */
    public double poseidonWaterDamageMultiplier() {
        return poseidonWaterDamageMultiplier;
    }

    /** Seconds of Slowness a Poseidon carries after leaving the water. */
    public int poseidonLandSlownessSeconds() {
        return poseidonLandSlownessSeconds;
    }

    /** 1 = Slowness I. */
    public int poseidonLandSlownessLevel() {
        return poseidonLandSlownessLevel;
    }

    /** Furthest a Fisherman's reel will pull from. Beyond this the line snaps. */
    /** The hook's own sting on connect — feedback and attribution, not the weapon. */
    public double fishermanHookDamage() {
        return fishermanHookDamage;
    }

    public double fishermanMaxDistance() {
        return fishermanMaxDistance;
    }

    /** Blocks a reel arcs above the victim before dropping them on the Fisherman. */
    public double fishermanArcHeight() {
        return fishermanArcHeight;
    }

    /** Seconds before the same victim can be reeled in again, by anyone. */
    public int fishermanCooldownSeconds() {
        return fishermanCooldownSeconds;
    }

    /** XP a Barbarian's first kill is worth, on top of whatever the corpse drops. */
    public int barbarianKillXp() {
        return barbarianKillXp;
    }

    /** What a mob kill feeds Tyrfing. A seventh of the first player kill by default. */
    public int barbarianMobKillXp() {
        return barbarianMobKillXp;
    }

    /** How much more each kill after the first is worth than the one before it. */
    public int barbarianKillXpStep() {
        return barbarianKillXpStep;
    }

    /** Tyrfing's ladder, cheapest rung first. Never empty — index 0 is the starting sword. */
    public List<SwordTier> barbarianTiers() {
        return barbarianTiers;
    }

    // ---------------------------------------------------------------- 1.8 combat

    /** Whether 1.9 sweep attacks are cancelled. */
    public boolean disableSweep() {
        return disableSweep;
    }

    /** Whether a sprinting player can still land a critical hit, as in 1.8. */
    public boolean sprintCrits() {
        return sprintCrits;
    }

    /** Whether a melee hit leaves the attacker sprinting. */
    public boolean keepSprint() {
        return keepSprint;
    }

    /** Elimination line for logging out under attack, with {victim} and {killer} filled in. */
    public String combatLogKillMessage(String victim, String killer) {
        return combatLogKillMessage.replace("{victim}", victim).replace("{killer}", killer);
    }

    /** Elimination line for logging out mid fight with nobody to credit. */
    public String combatLogMessage(String victim) {
        return combatLogMessage.replace("{victim}", victim);
    }

    public boolean noPearlCooldown() {
        return noPearlCooldown;
    }

    public boolean disableShields() {
        return disableShields;
    }

    /** Seconds between natural heals. 0 leaves modern saturation regeneration alone. */
    public int regenIntervalSeconds() {
        return regenIntervalSeconds;
    }

    /** Whether melee knockback is recomputed with the 1.8 formula. */
    public boolean legacyKnockback() {
        return legacyKnockback;
    }

    public double knockbackHorizontal() {
        return knockbackHorizontal;
    }

    public double knockbackVertical() {
        return knockbackVertical;
    }

    public double knockbackVerticalLimit() {
        return knockbackVerticalLimit;
    }

    /** Added per knockback level, where sprinting counts as a level. */
    public double knockbackExtraHorizontal() {
        return knockbackExtraHorizontal;
    }

    public double knockbackExtraVertical() {
        return knockbackExtraVertical;
    }

    /** Strength worn as a badge while a Poseidon stands in water. 0 turns the badge off. */
    public int poseidonWaterStrengthLevel() {
        return poseidonWaterStrengthLevel;
    }

    /** Seconds a Ninja's hit stays worth teleporting to. */
    public int ninjaMarkSeconds() {
        return ninjaMarkSeconds;
    }

    /** Seconds between a Ninja's jumps. */
    public int ninjaCooldownSeconds() {
        return ninjaCooldownSeconds;
    }

    /** Sponges in the Launcher's starting kit. */
    public int launcherSponges() {
        return launcherSponges;
    }

    /** Upward kick per sponge in the stack. A vanilla jump is about 0.42. */
    public double launcherPower() {
        return launcherPower;
    }

    /** How many stacked sponges still count towards the throw. */
    public int launcherMaxStack() {
        return launcherMaxStack;
    }

    /** Sideways push when the pad is lopsided. */
    public double launcherSidewaysPower() {
        return launcherSidewaysPower;
    }

    /** Seconds a launched player is owed a free landing. */
    public int launcherFallImmunitySeconds() {
        return launcherFallImmunitySeconds;
    }

    /** Seconds a Jellyfish's conjured water lasts before it is taken back. */
    public int jellyfishSeconds() {
        return jellyfishSeconds;
    }

    /** Most conjured waters one Jellyfish may have standing at once. */
    public int jellyfishMaxActive() {
        return jellyfishMaxActive;
    }

    /** Fraction of falling speed a pad returns as bounce. Above 1.0, bounces grow. */
    public double launcherRestitution() {
        return launcherRestitution;
    }

    /** Chance a Cookiemonster's broken grass drops a cookie. */
    public double cookieGrassChance() {
        return cookieGrassChance;
    }

    /** Hunger a cookie restores first. 2 is one drumstick. */
    public int cookieFood() {
        return cookieFood;
    }

    /** Health a cookie restores once hunger is full. 2.0 is one heart. */
    public double cookieHeal() {
        return cookieHeal;
    }

    public int cookieSpeedSeconds() {
        return cookieSpeedSeconds;
    }

    /** Speed level when both bars are full. 2 is Speed II. */
    public int cookieSpeedLevel() {
        return cookieSpeedLevel;
    }

    /** How far the Timelord's freeze reaches. */
    public double timelordRadius() {
        return timelordRadius;
    }

    /** Seconds a frozen player stays rooted, unless someone hits them first. */
    public int timelordDurationSeconds() {
        return timelordDurationSeconds;
    }

    /** Seconds the watch is spent for on top of the freeze itself. */
    public int timelordCooldownSeconds() {
        return timelordCooldownSeconds;
    }

    /** Upward kick of the Kangaroo's rocket. A vanilla jump is about 0.42. */
    public double kangarooJumpPower() {
        return kangarooJumpPower;
    }

    /** Push along the Kangaroo's line of sight, so the hop travels rather than only rising. */
    public double kangarooForwardPower() {
        return kangarooForwardPower;
    }

    public int kangarooCooldownSeconds() {
        return kangarooCooldownSeconds;
    }

    /** Fall-damage immunity a Kangaroo earns by landing a hit on another player. */
    public int kangarooFallImmunitySeconds() {
        return kangarooFallImmunitySeconds;
    }

    /** Columns a Jackhammer can drill before the hammer needs a rest. */
    public int jackhammerUses() {
        return jackhammerUses;
    }

    public int jackhammerCooldownSeconds() {
        return jackhammerCooldownSeconds;
    }

    /** Ticks between blocks in a drilled column — this is the animation speed. */
    public long jackhammerIntervalTicks() {
        return jackhammerIntervalTicks;
    }

    /** Whether a drilled column drops its blocks, like ordinary mining. */
    public boolean jackhammerDropsBlocks() {
        return jackhammerDropsBlocks;
    }

    /** Most fall damage a Stomper takes themselves. 4.0 = two hearts. */
    public double stomperFallCap() {
        return stomperFallCap;
    }

    /** How far a stomp reaches. Damage tapers linearly to nothing at this distance. */
    public double stomperRadius() {
        return stomperRadius;
    }

    /** Most a crouching victim takes from a stomp. 4.0 = two hearts. */
    public double stomperSneakCap() {
        return stomperSneakCap;
    }

    /** Health one soup restores while below full. 7.0 = three and a half hearts. */
    public double soupHeal() {
        return soupHeal;
    }

    /** Hunger one soup restores instead, once health is already full. */
    public int soupFood() {
        return soupFood;
    }

    public float soupSaturation() {
        return soupSaturation;
    }

    /** How far above the winner the cake podium is built. */
    public int winPlatformHeight() {
        return winPlatformHeight;
    }

    /** How many times the win line is repeated, once a second, before the server closes. */
    public int winChants() {
        return winChants;
    }

    /** Seconds between each call of the winner's name. */
    public int winChantIntervalSeconds() {
        return winChantIntervalSeconds;
    }

    /** Seconds the winner is left on the podium after the last call, before the server closes. */
    public int winCloseDelaySeconds() {
        return winCloseDelaySeconds;
    }

    public long winFireworkIntervalTicks() {
        return winFireworkIntervalTicks;
    }

    public boolean restartOnReset() {
        return restartOnReset;
    }

    /** Whether a restart leaves the old map behind and generates a new one. */
    public boolean webEnabled() {
        return webEnabled;
    }

    /** Loopback by default — the website's Node process is the audience, not the internet. */
    public String webBind() {
        return webBind;
    }

    public int webPort() {
        return webPort;
    }

    /**
     * Whether rotated worlds are born with the no-oceans datapack.
     *
     * <p>Only takes effect through {@link com.hardcorekits.world.WorldRotator} — a world reads
     * datapacks as it is generated, so the running world's terrain is already decided.
     */
    public boolean worldgenNoOceans() {
        return worldgenNoOceans;
    }

    /** Extra mushrooms planted per swamp chunk by the WorldShaper. 0 turns it off. */
    public int swampMushroomsPerChunk() {
        return swampMushroomsPerChunk;
    }

    public int forestMushroomsPerChunk() {
        return forestMushroomsPerChunk;
    }

    public boolean freshWorldOnRestart() {
        return freshWorldOnRestart;
    }

    /**
     * Late joiners are admitted until this many seconds BEFORE invincibility ends — the buffer
     * exists so nobody materialises into a world where PvP went live mid-teleport.
     */
    public int lateJoinCutoffSeconds() {
        return lateJoinCutoffSeconds;
    }

    public boolean watchdogEnabled() {
        return watchdogEnabled;
    }

    /** Eye-to-hitbox distance on a melee hit past which the watchdog flags it to staff. */
    public double watchdogMaxReach() {
        return watchdogMaxReach;
    }

    /** Consecutive seconds of unsupported, non-falling hover before the watchdog flags it. */
    public int watchdogHoverSeconds() {
        return watchdogHoverSeconds;
    }

    /**
     * How far past the damage zone the real, impassable vanilla border stands. Its job is
     * purely mechanical: a runner dying slowly outward can no longer force the server to
     * generate fresh terrain forever.
     */
    public double borderHardWallMargin() {
        return borderHardWallMargin;
    }

    /** Within this many blocks of the edge, the player's chat warns about the forcefield. */
    public double borderForcefieldWarningDistance() {
        return borderForcefieldWarningDistance;
    }

    /** Chunks of world sent to each client. 0 leaves the server default alone. */
    public int viewDistance() {
        return viewDistance;
    }

    /** Chunks around each player the server actually ticks. 0 leaves the default alone. */
    public int simulationDistance() {
        return simulationDistance;
    }

    /** Whether the whole playable area is generated up front at boot. */
    public boolean pregenEnabled() {
        return pregenEnabled;
    }

    /** Extra chunks generated beyond the hard wall, so its far side isn't a void seam. */
    public int pregenMarginChunks() {
        return pregenMarginChunks;
    }

    /** Chunk generations in flight at once. Higher is faster and heavier. */
    public int pregenParallel() {
        return pregenParallel;
    }

    /** Pipeline width once a match is live — a trickle, so the sweep never fights the game. */
    public int pregenParallelDuringMatch() {
        return pregenParallelDuringMatch;
    }

    /** Seconds between one conjured water and the next, per Jellyfish. */
    public int jellyfishCooldownSeconds() {
        return jellyfishCooldownSeconds;
    }
}
