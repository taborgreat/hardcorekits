package gg.hungergames.kit.kits;

import gg.hungergames.game.GameConfig;
import gg.hungergames.kit.Kit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.util.BiomeSearchResult;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Starts the game somewhere else entirely.
 *
 * <p>Everyone else is dropped within the scatter radius of centre and immediately has company.
 * A Hermit is put down in the furthest soup biome on the map instead — swamp for mushrooms
 * first, then jungle, then desert — and if the map has none of them, at least a few hundred
 * blocks out regardless.
 *
 * <p>The trade is isolation both ways. Nobody can reach you for the first couple of minutes,
 * so wood, stone and iron are yours uncontested; equally, you are nowhere near the fighting or
 * the centre when the feast lands.
 *
 * <p>This is the only kit that changes where the match starts, which is why {@link Kit} has a
 * {@link #dropPoint} seam at all.
 */
public final class HermitKit implements Kit {

    public static final String ID = "hermit";

    /** Preference order. Swamps make soup, jungles feed you, deserts at least have temples. */
    private static final List<List<Biome>> SOUP_BIOMES = List.of(
            List.of(Biome.SWAMP, Biome.MANGROVE_SWAMP),
            List.of(Biome.JUNGLE, Biome.SPARSE_JUNGLE, Biome.BAMBOO_JUNGLE),
            List.of(Biome.DESERT));

    /** Directions probed from the map edge when hunting for a biome. */
    private static final int PROBES = 8;
    /** Keeps the drop clear of the border, where standing still is fatal. */
    private static final double EDGE_MARGIN = 24.0D;

    /** Rings walked outward when the chosen column turns out to be water or lava. */
    private static final int GROUND_RINGS = 4;
    private static final int GROUND_RING_STEP = 8;
    private static final int GROUND_SPOKES = 6;
    /** Blocks the landing is nudged by, so two Hermits sent to one biome are not sent to one block. */
    private static final int GROUND_JITTER = 24;

    /** The map the remembered target belongs to, so a new world re-runs the search. */
    private String searchedWorld;
    /** Where the biome search landed, kept so every Hermit does not repeat it. */
    private Location searchedTarget;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Hermit";
    }

    @Override
    public String description() {
        return "Start alone in the furthest swamp, jungle or desert, far from everyone else.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment — the head start is the position, not the gear.
    }

    @Override
    public Location dropPoint(Player player, GameConfig config) {
        World world = config.world();
        return skyDrop(world, biomeTarget(world, config), config);
    }

    /**
     * The far corner this map gets, found once and then remembered.
     *
     * <p>Up to 24 biome searches over a 950-block radius is not something to run per Hermit
     * while the match is starting and everyone is being teleported at once — and the answer
     * cannot change while the world does not, so the first Hermit pays for it and the rest read
     * it back. They still land apart: {@link #dryColumn} jitters each one separately.
     */
    private Location biomeTarget(World world, GameConfig config) {
        if (world.getName().equals(searchedWorld) && searchedTarget != null) {
            return searchedTarget.clone();
        }

        Location centre = config.center();
        double limit = (config.borderSize() / 2.0D) - EDGE_MARGIN;

        Location found = null;
        for (List<Biome> tier : SOUP_BIOMES) {
            found = furthestOf(world, centre, limit, tier);
            if (found != null) {
                break;
            }
        }
        if (found == null) {
            found = farFallback(centre, limit, config);
        }

        searchedWorld = world.getName();
        searchedTarget = found;
        return found.clone();
    }

    /**
     * The furthest point of any of these biomes inside the play area.
     *
     * <p>{@code locateNearestBiome} only ever finds the nearest, so the search is run from
     * several origins spaced around the border and the most distant hit wins. Probing the edge
     * rather than the centre is what biases it outward.
     */
    private Location furthestOf(World world, Location centre, double limit, List<Biome> biomes) {
        Biome[] wanted = biomes.toArray(new Biome[0]);
        int searchRadius = (int) Math.ceil(limit * 2);

        Location best = null;
        double bestDistance = -1.0D;

        for (int i = 0; i < PROBES; i++) {
            double angle = (2.0D * Math.PI * i) / PROBES;
            Location probe = centre.clone().add(
                    Math.cos(angle) * limit, 0.0D, Math.sin(angle) * limit);

            BiomeSearchResult result;
            try {
                result = world.locateNearestBiome(probe, searchRadius, wanted);
            } catch (IllegalArgumentException e) {
                continue; // biome not present in this world's source at all
            }
            if (result == null) {
                continue;
            }

            Location hit = result.getLocation();
            double distance = horizontalDistance(centre, hit);
            if (distance > limit || distance <= bestDistance) {
                continue; // outside the play area, or not an improvement
            }
            bestDistance = distance;
            best = hit;
        }
        return best;
    }

    /** No soup biome anywhere: just put them a long way out in a random direction. */
    private Location farFallback(Location centre, double limit, GameConfig config) {
        double minimum = Math.min(config.hermitMinDistance(), limit);
        double distance = minimum + ThreadLocalRandom.current().nextDouble() * (limit - minimum);
        double angle = ThreadLocalRandom.current().nextDouble() * 2.0D * Math.PI;

        return centre.clone().add(
                Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
    }

    /** Same falling entrance everyone else gets, just somewhere far away — and on dry land. */
    private Location skyDrop(World world, Location target, GameConfig config) {
        int[] column = dryColumn(world, target, config.center(),
                (config.borderSize() / 2.0D) - EDGE_MARGIN);
        int y = world.getHighestBlockYAt(column[0], column[1]) + config.dropHeight();
        return new Location(world, column[0] + 0.5D, y, column[1] + 0.5D);
    }

    /**
     * A column near the target that lands on solid, dry ground inside the border.
     *
     * <p>The height lookup counts liquids, so the biome search happily hands back the middle of
     * a swamp pool, an ocean, or — in a desert — the surface of a lava lake, which is an
     * execution rather than a head start. So the landing is jittered a little and then rings are
     * walked outward until a column comes up solid. The jitter also matters on its own: the
     * biome search is deterministic, so without it every Hermit in the game would be dropped on
     * the same block, which is the opposite of the kit.
     *
     * <p>If nothing solid turns up nearby the original column is used anyway — a wet landing is
     * survivable, and the alternative is no drop point at all.
     */
    private int[] dryColumn(World world, Location target, Location centre, double limit) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double spin = random.nextDouble() * 2.0D * Math.PI;
        double nudge = random.nextDouble() * GROUND_JITTER;

        int originX = target.getBlockX() + (int) Math.round(Math.cos(spin) * nudge);
        int originZ = target.getBlockZ() + (int) Math.round(Math.sin(spin) * nudge);

        for (int ring = 0; ring <= GROUND_RINGS; ring++) {
            int spokes = ring == 0 ? 1 : GROUND_SPOKES;
            for (int spoke = 0; spoke < spokes; spoke++) {
                double angle = spin + (2.0D * Math.PI * spoke) / spokes;
                int x = originX + (int) Math.round(Math.cos(angle) * ring * GROUND_RING_STEP);
                int z = originZ + (int) Math.round(Math.sin(angle) * ring * GROUND_RING_STEP);

                if (horizontalDistance(centre, x, z) > limit) {
                    continue; // never trade a wet landing for one outside the map
                }
                if (world.getBlockAt(x, world.getHighestBlockYAt(x, z), z).getType().isSolid()) {
                    return new int[]{x, z};
                }
            }
        }
        return new int[]{target.getBlockX(), target.getBlockZ()};
    }

    private double horizontalDistance(Location a, Location b) {
        return horizontalDistance(a, b.getX(), b.getZ());
    }

    private double horizontalDistance(Location from, double x, double z) {
        double dx = from.getX() - x;
        double dz = from.getZ() - z;
        return Math.sqrt((dx * dx) + (dz * dz));
    }
}
