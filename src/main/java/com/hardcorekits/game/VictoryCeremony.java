package com.hardcorekits.game;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.util.Msg;
import com.hardcorekits.util.Phases;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The send-off for the last tribute standing.
 *
 * <p>The winner is made invulnerable and lifted onto a floating 5x5 cake platform with
 * fireworks going up around it, their name is called out once a second, and then everyone is
 * kicked with the congratulations line — which is the cue for {@link GameManager#reset()} to
 * take the server down.
 *
 * <p>This runs in {@link GameState#ENDING}: the match is over, so nothing takes damage and no
 * timer is left running.
 */
public final class VictoryCeremony {

    /** 2 gives the 5x5 footprint. */
    private static final int PLATFORM_RADIUS = 2;
    /** Fireworks go up from a ring just outside the cake, not through the winner. */
    private static final double FIREWORK_RING = PLATFORM_RADIUS + 1.5D;
    /** A beat between landing on the cake and the first chant, so the two do not collide. */
    private static final long CHANT_DELAY_SECONDS = 2L;

    private static final Color[] COLORS = {
            Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA,
            Color.BLUE, Color.FUCHSIA, Color.WHITE};
    private static final FireworkEffect.Type[] SHAPES = {
            FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE,
            FireworkEffect.Type.BURST, FireworkEffect.Type.STAR};

    private final HardcoreGames plugin;
    private final GameConfig config;

    /** Every block the platform put down, so an in-place reset can take it back out. */
    private final List<Location> placed = new ArrayList<>();

    private BukkitTask fireworkTask;
    private BukkitTask chantTask;
    private BukkitTask chantStartTask;
    private BukkitTask closeTask;

    VictoryCeremony(HardcoreGames plugin, GameConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Runs the whole ceremony, then hands back to {@code onFinished} — which is the reset,
     * either a server shutdown or a return to WAITING.
     */
    void celebrate(Player winner, Runnable onFinished) {
        String line = winner.getName() + " wins!";
        Msg.win(line);

        winner.setInvulnerable(true);
        winner.setFallDistance(0.0F);
        restore(winner);
        giveWinMap(winner);
        Location podium = buildPlatform(winner);
        winner.teleport(podium);

        fireworkTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> launchFirework(podium), 0L, config.winFireworkIntervalTicks());

        chantStartTask = Phases.delayed(plugin, CHANT_DELAY_SECONDS,
                () -> chantTask = chant(line, winner, onFinished));
    }

    /**
     * Calls the winner's name on its own cadence, then holds before the lights go out.
     *
     * <p>Not a {@code Phases.countdown}: that ladder is one line a second, and the victory
     * line wants room around it. The pause after the last call is dead air on purpose — the
     * winner gets a few seconds alone on the cake with the fireworks before the kick.
     */
    private BukkitTask chant(String line, Player winner, Runnable onFinished) {
        int total = config.winChants();
        return new BukkitRunnable() {
            private int called;

            @Override
            public void run() {
                if (called >= total) {
                    cancel();
                    closeTask = Phases.delayed(plugin, config.winCloseDelaySeconds(),
                            () -> finish(winner, onFinished));
                    return;
                }
                Msg.win(line);
                called++;
            }
        }.runTaskTimer(plugin, 0L, config.winChantIntervalSeconds() * 20L);
    }

    /** Cancels the timers. The platform is left standing — see {@link #clear()}. */
    void cancel() {
        Phases.cancel(fireworkTask);
        Phases.cancel(chantTask);
        Phases.cancel(chantStartTask);
        Phases.cancel(closeTask);
        fireworkTask = null;
        chantTask = null;
        chantStartTask = null;
        closeTask = null;
    }

    /** Cancels and takes the platform back down, so it is not hanging over the next match. */
    void clear() {
        cancel();
        for (Location location : placed) {
            Block block = location.getBlock();
            if (block.getType() == Material.CAKE || block.getType() == Material.BARRIER) {
                block.setType(Material.AIR);
            }
        }
        placed.clear();
    }

    private void finish(Player winner, Runnable onFinished) {
        cancel();
        farewell(winner);
        onFinished.run();
    }

    /** The last thing anyone sees before the server goes down. */
    private void farewell(Player winner) {
        Component rejoin = Component.text("Rejoin to play again!", NamedTextColor.GRAY);
        // Copied, because kicking mutates the online-player view we would be iterating.
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            Component headline = player.equals(winner)
                    ? Component.text("Congratulations on your win!", NamedTextColor.GOLD)
                    : Component.text(winner.getName() + " wins!", NamedTextColor.RED);
            player.kick(headline.append(Component.newline()).append(rejoin));
        }
    }

    // ---------------------------------------------------------------- the winner

    /** The winner steps onto the cake whole: full hearts, full hunger, nothing burning. */
    private void restore(Player winner) {
        org.bukkit.attribute.AttributeInstance maxHealth =
                winner.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            winner.setHealth(maxHealth.getValue());
        }
        winner.setFoodLevel(20);
        winner.setSaturation(20.0F);
        winner.setFireTicks(0);
    }

    /** The classic keepsake: a map already in hand, reading out the congratulations. */
    private void giveWinMap(Player winner) {
        org.bukkit.map.MapView view = Bukkit.createMap(winner.getWorld());
        for (org.bukkit.map.MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
            view.removeRenderer(renderer); // no terrain under the card
        }
        view.addRenderer(new WinMapRenderer(winner.getName()));

        org.bukkit.inventory.ItemStack map =
                new org.bukkit.inventory.ItemStack(Material.FILLED_MAP);
        if (map.getItemMeta() instanceof org.bukkit.inventory.meta.MapMeta meta) {
            meta.setMapView(view);
            map.setItemMeta(meta);
        }
        winner.getInventory().setItemInMainHand(map);
    }

    /** Draws the congratulations card: red text on the parchment, a cake beneath. */
    private static final class WinMapRenderer extends org.bukkit.map.MapRenderer {
        private final String winner;
        private boolean drawn;

        WinMapRenderer(String winner) {
            this.winner = winner;
        }

        @Override
        public void render(org.bukkit.map.MapView view, org.bukkit.map.MapCanvas canvas,
                           Player player) {
            if (drawn) {
                return;
            }
            drawn = true;
            centred(canvas, 10, "CONGRATULATIONS!");
            centred(canvas, 30, "On winning a");
            centred(canvas, 40, "Hardcore Game,");
            centred(canvas, 54, winner);
            drawCake(canvas);
        }

        /** MinecraftFont, centred on the 128px canvas, in map-palette red. */
        @SuppressWarnings("deprecation") // MapPalette.RED: the drawText escape needs the byte
        private static void centred(org.bukkit.map.MapCanvas canvas, int y, String text) {
            org.bukkit.map.MinecraftFont font = org.bukkit.map.MinecraftFont.Font;
            if (!font.isValid(text)) {
                return; // a winner name the map font cannot draw is better blank than a crash
            }
            int x = Math.max(0, (128 - font.getWidth(text)) / 2);
            canvas.drawText(x, y, font, "§" + org.bukkit.map.MapPalette.RED + ";" + text);
        }

        /** A blocky cake: white icing over a brown base, cherries scattered on top. */
        private static void drawCake(org.bukkit.map.MapCanvas canvas) {
            java.awt.Color icing = new java.awt.Color(245, 245, 245);
            java.awt.Color sponge = new java.awt.Color(125, 80, 46);
            java.awt.Color cherry = new java.awt.Color(200, 30, 30);

            for (int x = 38; x <= 90; x++) {
                for (int y = 76; y <= 112; y++) {
                    canvas.setPixelColor(x, y, y <= 94 ? icing : sponge);
                }
            }
            int[][] cherries = {{46, 80}, {58, 84}, {70, 79}, {82, 83}, {52, 89}, {76, 90}, {64, 76}};
            for (int[] spot : cherries) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dy = 0; dy <= 1; dy++) {
                        canvas.setPixelColor(spot[0] + dx, spot[1] + dy, cherry);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- the platform

    /**
     * Builds the cake pad above wherever the winner is standing — their own chunk, so it is
     * guaranteed to be loaded — and returns the spot to stand them on.
     */
    private Location buildPlatform(Player winner) {
        World world = winner.getWorld();
        Location base = winner.getLocation();
        int y = Math.min(base.getBlockY() + config.winPlatformHeight(), world.getMaxHeight() - 3);
        int cx = base.getBlockX();
        int cz = base.getBlockZ();

        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                // Invisible floor under the cake: cake is edible, and an eaten slice would
                // otherwise leave a hole in the podium.
                place(world.getBlockAt(cx + dx, y, cz + dz), Material.BARRIER);
                place(world.getBlockAt(cx + dx, y + 1, cz + dz), Material.CAKE);
            }
        }
        return new Location(world, cx + 0.5D, y + 2, cz + 0.5D, base.getYaw(), base.getPitch());
    }

    private void place(Block block, Material material) {
        block.setType(material);
        placed.add(block.getLocation());
    }

    private void launchFirework(Location podium) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * 2.0D * Math.PI;
        Location spot = podium.clone().add(
                Math.cos(angle) * FIREWORK_RING, -1.0D, Math.sin(angle) * FIREWORK_RING);

        Firework firework = spot.getWorld().spawn(spot, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(SHAPES[random.nextInt(SHAPES.length)])
                .withColor(COLORS[random.nextInt(COLORS.length)])
                .withFade(COLORS[random.nextInt(COLORS.length)])
                .withTrail()
                .withFlicker()
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }
}
