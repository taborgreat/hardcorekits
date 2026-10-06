package com.hardcorekits.studio;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.Kit;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.BeastmasterKit;
import com.hardcorekits.kit.kits.CannibalKit;
import com.hardcorekits.kit.kits.CookiemonsterKit;
import com.hardcorekits.kit.kits.DemomanKit;
import com.hardcorekits.kit.kits.DiggerKit;
import com.hardcorekits.kit.kits.EndermageKit;
import com.hardcorekits.kit.kits.FishermanKit;
import com.hardcorekits.kit.kits.FlashKit;
import com.hardcorekits.kit.kits.GladiatorKit;
import com.hardcorekits.kit.kits.HadesKit;
import com.hardcorekits.kit.kits.HulkKit;
import com.hardcorekits.kit.kits.JellyfishKit;
import com.hardcorekits.kit.kits.KangarooKit;
import com.hardcorekits.kit.kits.LauncherKit;
import com.hardcorekits.kit.kits.MonkKit;
import com.hardcorekits.kit.kits.NinjaKit;
import com.hardcorekits.kit.kits.PyroKit;
import com.hardcorekits.kit.kits.ReaperKit;
import com.hardcorekits.kit.kits.SnailKit;
import com.hardcorekits.kit.kits.SpidermanKit;
import com.hardcorekits.kit.kits.StomperKit;
import com.hardcorekits.kit.kits.SwitcherKit;
import com.hardcorekits.kit.kits.ThorKit;
import com.hardcorekits.kit.kits.TimelordKit;
import com.hardcorekits.kit.kits.ViperKit;
import com.hardcorekits.kit.kits.WispKit;
import com.hardcorekits.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Wolf;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * {@code /studio}: the film studio's remote control for kits. Bound in studio mode only.
 *
 * <pre>
 * /studio kit &lt;player&gt; &lt;kitId|none&gt;        give a player a kit (and its items), no questions asked
 * /studio fire &lt;player&gt; [kitId] [x y z]    make their kit's ability happen now, aimed at x y z if given
 * /studio clear &lt;player&gt;                   take their kit away
 * /studio reset                            drop every kit's per-match state (cooldowns, mines, arenas)
 * </pre>
 *
 * <p>{@code fire} does not re-implement anything. Each kit's ability lives in a listener that
 * answers a game event (a right click with the kit's item, a click on a player, a block going
 * down, a landing, a blow), so firing a kit means handing that listener the event it waits
 * for, with the kit's item in the player's hand for the length of the call. The table in
 * {@link #triggers()} says which event each kit answers; everything after that is the kit's
 * own code. Kits with nothing to trigger (passives) are a no-op by design.
 */
public final class StudioCommand implements CommandExecutor, TabCompleter {

    /** How far around a player a kit looks for someone, or something, to act on. */
    private static final double REACH = 6.0D;

    /** What {@code fire} hands a trigger: who, and where they are aiming if the studio said. */
    private record Shot(Player player, Location aim) {
    }

    /** Makes one kit's ability happen. Returns what it did, or null if there was nothing to do it to. */
    @FunctionalInterface
    private interface Trigger {
        String fire(Shot shot);
    }

    private final HardcoreGames plugin;
    private final GameManager game;
    private final KitRegistry kits;
    private final Map<String, Trigger> triggers;

    public StudioCommand(HardcoreGames plugin, GameManager game, KitRegistry kits) {
        this.plugin = plugin;
        this.game = game;
        this.kits = kits;
        this.triggers = triggers();
    }

    // ---------------------------------------------------------------- which event each kit answers

    private Map<String, Trigger> triggers() {
        Map<String, Trigger> map = new HashMap<>();
        // A right click with the kit's item.
        map.put(ThorKit.ID, shot -> click(shot, new ItemStack(ThorKit.HAMMER), true));
        map.put(FlashKit.ID, shot -> click(shot, new ItemStack(FlashKit.TORCH), false));
        map.put(KangarooKit.ID, shot -> click(shot, new ItemStack(KangarooKit.ROCKET), false));
        map.put(PyroKit.ID, shot -> click(shot, new ItemStack(PyroKit.CHARGE), false));
        map.put(TimelordKit.ID, shot -> click(shot, new ItemStack(TimelordKit.WATCH), false));
        map.put(WispKit.ID, shot -> click(shot, new ItemStack(WispKit.CREAM), false));
        map.put(CookiemonsterKit.ID, shot -> click(shot, new ItemStack(Material.COOKIE), false));
        map.put(JellyfishKit.ID, shot -> click(shot, null, true)); // an empty hand on a block
        // A right click on someone, or something, standing close.
        map.put(GladiatorKit.ID, shot -> touch(shot, new ItemStack(GladiatorKit.BARS), this::opponent));
        map.put(MonkKit.ID, shot -> touch(shot, new ItemStack(MonkKit.ROD), this::opponent));
        map.put(HulkKit.ID, shot -> touch(shot, null,
                e -> e instanceof LivingEntity && !(e instanceof ArmorStand)
                        && (!(e instanceof Player) || opponent(e))));
        map.put(HadesKit.ID, shot -> touch(shot, new ItemStack(HadesKit.WAND), e -> e instanceof Mob));
        map.put(BeastmasterKit.ID, shot -> touch(shot, new ItemStack(Material.BONE),
                e -> e instanceof Wolf wolf && !wolf.isTamed()));
        // The kit's block going down.
        map.put(EndermageKit.ID, shot -> place(shot, Set.of(EndermageKit.PORTAL_ITEM), true));
        map.put(DiggerKit.ID, shot -> place(shot, Set.of(DiggerKit.EGG), true));
        map.put(DemomanKit.ID, shot -> place(shot, Set.of(Material.STONE_PRESSURE_PLATE), false));
        map.put(LauncherKit.ID, shot -> place(shot, Set.of(Material.SPONGE, Material.WET_SPONGE), false));
        // A blow landing on the nearest opponent.
        map.put(ViperKit.ID, shot -> hit(shot, null));
        map.put(SnailKit.ID, shot -> hit(shot, null));
        map.put(CannibalKit.ID, shot -> hit(shot, null));
        map.put(NinjaKit.ID, shot -> hit(shot, null));
        map.put(ReaperKit.ID, shot -> hit(shot, new ItemStack(ReaperKit.SCYTHE)));
        // A hard landing.
        map.put(StomperKit.ID, this::land);
        // Something thrown or cast.
        map.put(SwitcherKit.ID, shot -> throwBall(shot, switcherBall()));
        map.put(SpidermanKit.ID, shot -> throwBall(shot, new ItemStack(Material.SNOWBALL)));
        map.put(FishermanKit.ID, this::hook);
        return map;
    }

    // ---------------------------------------------------------------- the command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "kit" -> {
                if (args.length < 3) {
                    return false;
                }
                Player player = player(sender, args[1]);
                if (player != null) {
                    assign(sender, player, args[2]);
                }
            }
            case "fire" -> {
                if (args.length < 2) {
                    return false;
                }
                Player player = player(sender, args[1]);
                if (player != null) {
                    fire(sender, player, args);
                }
            }
            case "clear" -> {
                if (args.length < 2) {
                    return false;
                }
                Player player = player(sender, args[1]);
                if (player != null) {
                    kits.deselect(player.getUniqueId());
                    game.studioDismiss(player);
                    Msg.admin(sender, player.getName() + " has no kit.");
                }
            }
            case "reset" -> {
                game.studioReset();
                Msg.admin(sender, "Kit state cleared.");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private Player player(CommandSender sender, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            Msg.admin(sender, "No player called " + name + " is online.");
        }
        return player;
    }

    private void assign(CommandSender sender, Player player, String kitId) {
        // Everyone the studio names is in the match as far as the kits are concerned, kit or
        // not: abilities only reach players the game counts as alive.
        game.studioAdmit(player);
        if (kitId.equalsIgnoreCase("none")) {
            kits.deselect(player.getUniqueId());
            Msg.admin(sender, player.getName() + " is in, with no kit.");
            return;
        }
        Kit kit = kits.byId(kitId);
        if (kit == null) {
            Msg.admin(sender, "Unknown kit '" + kitId + "'.");
            return;
        }
        kits.select(player.getUniqueId(), kit);
        // As at a match start: the kit goes into an emptied inventory.
        player.getInventory().clear();
        kit.apply(player);
        Msg.admin(sender, player.getName() + " is now " + kit.displayName() + ".");
    }

    private void fire(CommandSender sender, Player player, String[] args) {
        int next = 2;
        Kit kit = kits.selectedFor(player.getUniqueId());
        if (args.length > next && !isNumber(args[next])) {
            kit = kits.byId(args[next]);
            if (kit == null) {
                Msg.admin(sender, "Unknown kit '" + args[next] + "'.");
                return;
            }
            // Firing a kit the player does not hold yet makes it theirs (no items handed out).
            kits.select(player.getUniqueId(), kit);
            next++;
        }
        if (kit == null) {
            Msg.admin(sender, player.getName() + " has no kit to fire.");
            return;
        }
        Location aim = null;
        if (args.length >= next + 3) {
            try {
                aim = new Location(player.getWorld(), Double.parseDouble(args[next]),
                        Double.parseDouble(args[next + 1]), Double.parseDouble(args[next + 2]));
            } catch (NumberFormatException e) {
                Msg.admin(sender, "The aim point must be three numbers: x y z.");
                return;
            }
        }
        game.studioAdmit(player);

        Trigger trigger = triggers.get(kit.id().toLowerCase(Locale.ROOT));
        if (trigger == null) {
            Msg.admin(sender, kit.displayName() + " has nothing to fire (it works on its own).");
            return;
        }
        String did;
        StudioMode.firing(true);
        try {
            did = trigger.fire(new Shot(player, aim));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("studio: " + kit.id() + " failed for " + player.getName() + ": " + e);
            Msg.admin(sender, kit.displayName() + " failed: " + e);
            return;
        } finally {
            StudioMode.firing(false);
        }
        String line = did == null
                ? kit.id() + " had nothing in reach for " + player.getName()
                : kit.id() + " fired for " + player.getName() + " (" + did + ")";
        plugin.getLogger().info("studio: " + line);
        Msg.admin(sender, line);
    }

    private static boolean isNumber(String text) {
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- the events

    /**
     * Runs {@code body} with {@code item} (null for an empty hand) in the player's main hand,
     * then puts back what they were holding. Set and restored inside one tick, so nobody
     * watching sees the hand change: the puppet keeps showing what the recording says.
     */
    private <T> T holding(Player player, ItemStack item, java.util.function.Supplier<T> body) {
        ItemStack before = player.getInventory().getItemInMainHand();
        ItemStack kept = before == null ? null : before.clone();
        player.getInventory().setItemInMainHand(item);
        if (item != null) {
            player.setCooldown(item.getType(), 0);
        }
        try {
            return body.get();
        } finally {
            player.getInventory().setItemInMainHand(kept);
        }
    }

    /** Turns the player to face the aim point, if the studio gave one. */
    private void face(Shot shot) {
        if (shot.aim() == null) {
            return;
        }
        Location eye = shot.player().getEyeLocation();
        Vector to = shot.aim().toVector().subtract(eye.toVector());
        if (to.lengthSquared() < 0.01D) {
            return;
        }
        Location looking = eye.clone().setDirection(to);
        shot.player().setRotation(looking.getYaw(), looking.getPitch());
    }

    /** The block a click lands on: the aim point, else what they look at, else the ground ahead. */
    private Block clicked(Shot shot) {
        Player player = shot.player();
        World world = player.getWorld();
        if (shot.aim() != null) {
            Block block = world.getBlockAt(shot.aim());
            for (int down = 0; down < 4 && block.getType().isAir(); down++) {
                block = block.getRelative(BlockFace.DOWN);
            }
            return block;
        }
        RayTraceResult ray = player.rayTraceBlocks(40.0D);
        if (ray != null && ray.getHitBlock() != null) {
            return ray.getHitBlock();
        }
        Vector ahead = player.getLocation().getDirection().setY(0.0D);
        if (ahead.lengthSquared() < 0.0001D) {
            ahead = new Vector(0.0D, 0.0D, 1.0D);
        }
        Location spot = player.getLocation().add(ahead.normalize().multiply(5.0D));
        return world.getHighestBlockAt(spot.getBlockX(), spot.getBlockZ());
    }

    /** A right click with the kit's item: at a block if the kit needs one, else in the air. */
    private String click(Shot shot, ItemStack item, boolean onBlock) {
        Player player = shot.player();
        face(shot);
        Block block = onBlock ? clicked(shot) : null;
        return holding(player, item, () -> {
            PlayerInteractEvent event = new PlayerInteractEvent(player,
                    block == null ? Action.RIGHT_CLICK_AIR : Action.RIGHT_CLICK_BLOCK,
                    item, block, block == null ? BlockFace.SELF : BlockFace.UP, EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(event);
            return block == null ? "right click" : "right click on " + at(block.getLocation());
        });
    }

    private boolean opponent(Entity entity) {
        return entity instanceof Player other && game.isAlive(other);
    }

    /** The nearest thing of the right kind to the aim point (or the player), within reach. */
    private Entity nearest(Shot shot, Predicate<Entity> wanted, double reach) {
        Player player = shot.player();
        Location around = shot.aim() != null ? shot.aim() : player.getLocation();
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : player.getWorld().getNearbyEntities(around, reach, reach, reach)) {
            if (entity.equals(player) || !entity.isValid() || !wanted.test(entity)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(around);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    /** A right click on the nearest fitting creature or player. */
    private String touch(Shot shot, ItemStack item, Predicate<Entity> wanted) {
        Player player = shot.player();
        Entity target = nearest(shot, wanted, REACH);
        if (target == null) {
            return null;
        }
        return holding(player, item, () -> {
            Bukkit.getPluginManager().callEvent(
                    new PlayerInteractEntityEvent(player, target, EquipmentSlot.HAND));
            return "right click on " + target.getName();
        });
    }

    /**
     * The kit's block being placed. In a film the block itself is already there (the recording
     * replays every block a player put down), so this finds it and tells the kit about it.
     * Kits whose block is their whole ability get one put down in front of them if none is.
     */
    private String place(Shot shot, Set<Material> blocks, boolean create) {
        Player player = shot.player();
        Location around = shot.aim() != null ? shot.aim() : player.getLocation();
        Block found = null;
        double bestDistance = Double.MAX_VALUE;
        int r = (int) REACH;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    Block block = around.getBlock().getRelative(dx, dy, dz);
                    if (!blocks.contains(block.getType())) {
                        continue;
                    }
                    double distance = block.getLocation().add(0.5D, 0.0D, 0.5D).distanceSquared(around);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        found = block;
                    }
                }
            }
        }
        Material material = blocks.iterator().next();
        if (found == null) {
            if (!create) {
                return null;
            }
            found = freeSpot(around);
            if (found == null) {
                return null;
            }
            found.setType(material, false);
        } else {
            material = found.getType();
        }
        Block block = found;
        ItemStack item = new ItemStack(material);
        return holding(player, item, () -> {
            Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(block, block.getState(),
                    block.getRelative(BlockFace.DOWN), item, player, true, EquipmentSlot.HAND));
            return block.getType().name().toLowerCase(Locale.ROOT) + " at " + at(block.getLocation());
        });
    }

    /** An empty block with ground under it, at or next to a spot. */
    private Block freeSpot(Location around) {
        Block base = around.getBlock();
        for (int[] step : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int dy = 1; dy >= -2; dy--) {
                Block block = base.getRelative(step[0], dy, step[1]);
                if (block.getType().isAir() && block.getRelative(BlockFace.DOWN).getType().isSolid()) {
                    return block;
                }
            }
        }
        return null;
    }

    /**
     * A blow on the nearest opponent, as the kit's listener sees one. Only the event is
     * raised, no damage is dealt: the recording already lands the hits it logged, this just
     * lets the kit add what it adds (poison, slowness, a mark).
     */
    private String hit(Shot shot, ItemStack weapon) {
        Player player = shot.player();
        Entity victim = nearest(shot, this::opponent, REACH);
        if (victim == null) {
            return null;
        }
        java.util.function.Supplier<String> blow = () -> {
            DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK)
                    .withCausingEntity(player).withDirectEntity(player).build();
            Bukkit.getPluginManager().callEvent(new EntityDamageByEntityEvent(player, victim,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 1.0D));
            return "a blow on " + victim.getName();
        };
        return weapon == null ? blow.get() : holding(player, weapon, blow);
    }

    /** A landing from a height: what the Stomper's shockwave answers. */
    private String land(Shot shot) {
        Player player = shot.player();
        double fall = 12.0D; // six hearts of fall: a drop worth stomping from
        Bukkit.getPluginManager().callEvent(new EntityDamageEvent(player,
                EntityDamageEvent.DamageCause.FALL, DamageSource.builder(DamageType.FALL).build(), fall));
        return "a landing";
    }

    private ItemStack switcherBall() {
        ItemStack ball = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = ball.getItemMeta();
        meta.getPersistentDataContainer().set(SwitcherKit.BALL_KEY, PersistentDataType.BYTE, (byte) 1);
        ball.setItemMeta(meta);
        return ball;
    }

    /** A real snowball leaves the hand; the kit's listeners take it from there. */
    private String throwBall(Shot shot, ItemStack ball) {
        Player player = shot.player();
        face(shot);
        return holding(player, ball, () -> {
            player.launchProjectile(Snowball.class);
            return "a throw";
        });
    }

    /** A cast that has caught the nearest opponent, about to be reeled in. */
    private String hook(Shot shot) {
        Player player = shot.player();
        Entity victim = nearest(shot, this::opponent, 24.0D);
        if (victim == null) {
            return null;
        }
        return holding(player, new ItemStack(Material.FISHING_ROD), () -> {
            FishHook hook = player.launchProjectile(FishHook.class);
            try {
                Bukkit.getPluginManager().callEvent(new PlayerFishEvent(player, victim, hook,
                        EquipmentSlot.HAND, PlayerFishEvent.State.CAUGHT_ENTITY));
            } finally {
                hook.remove();
            }
            return "hooked " + victim.getName();
        });
    }

    private static String at(Location location) {
        return location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    // ---------------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("kit", "fire", "clear", "reset"));
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reset")) {
            Bukkit.getOnlinePlayers().forEach(player -> out.add(player.getName()));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("kit") || args[0].equalsIgnoreCase("fire"))) {
            kits.all().forEach(kit -> out.add(kit.id()));
            if (args[0].equalsIgnoreCase("kit")) {
                out.add("none");
            }
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(typed));
        return out;
    }
}
