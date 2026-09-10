package com.hardcorekits.listener;

import com.hardcorekits.HardcoreGames;
import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.Kit;
import com.hardcorekits.util.Damage;
import com.hardcorekits.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Death is elimination: the kill line is broadcast, then the player is kicked and blocked from
 * rejoining until the next game. There is no spectator mode.
 *
 * <p>The announcement is deliberately sent before the kick so the blue kill line lands ahead
 * of the vanilla yellow "left the game" notice.
 */
public final class DeathListener implements Listener {

    private final HardcoreGames plugin;
    private final GameManager game;
    /** Consulted so a death mid-stomp reads "was stomped by", not as an ordinary melee kill. */
    private final StomperListener stomper;
    /** Consulted before eliminating: a Soulstealer's first death is deferred, not resolved. */
    private final SoulstealerListener souls;

    public DeathListener(HardcoreGames plugin, GameManager game, StomperListener stomper,
                         SoulstealerListener souls) {
        this.plugin = plugin;
        this.game = game;
        this.stomper = stomper;
        this.souls = souls;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!game.state().isLive()) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Suppress the vanilla death message; we broadcast our own formatted line.
        event.deathMessage(null);

        // The sky marks every death: one clap of thunder, heard by everyone wherever they
        // stand — the count just went down, and the whole map should feel it.
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(),
                    org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 1.0F);
        }

        if (killer != null && !killer.equals(victim)) {
            game.creditKill(killer);
        }

        // The Soulstealer's door: their first death starts a hunt instead of an elimination.
        // The kill above still counted — the death was real; only its consequences wait.
        if (souls.beginRevival(victim)) {
            Bukkit.broadcast(Msg.killLine(label(victim) + " died... but their soul lingers!"));
            return;
        }

        Component announcement = buildAnnouncement(victim, killer);
        // The disconnect screen repeats the line everyone else just read, so the last thing a
        // player sees is what actually happened to them — a kill, or a long drop.
        Component kickReason = Component.text("You lost! ", NamedTextColor.RED).append(announcement);

        // Kicking inside the death event is asking for trouble — let the death resolve first.
        Bukkit.getScheduler().runTask(plugin,
                () -> game.eliminate(victim, announcement, kickReason));
    }

    private Component buildAnnouncement(Player victim, Player killer) {
        String victimLabel = label(victim);
        if (killer == null) {
            return Msg.killLine(victimLabel + " " + causeOf(victim) + ".");
        }

        String killerLabel = label(killer);

        // The stomp has its own line — the kit's signature kill should read like one, and
        // "killed with a fist" undersold being landed on from thirty blocks.
        if (stomper.isStompDeath(victim)) {
            return Msg.killLine(victimLabel + " was stomped by " + killerLabel);
        }

        Weapon weapon = weaponOf(killer);

        String text = ThreadLocalRandom.current().nextBoolean()
                ? victimLabel + " entered the next life, courtesy of " + killerLabel
                        + "'s " + weapon.name()
                : victimLabel + " was killed by " + killerLabel + weapon.phrase();

        return Msg.killLine(text);
    }

    /**
     * Third-person phrase for a death nobody gets the credit for — "fell from a high place",
     * "drowned", "was slain by a zombie".
     *
     * <p>Vanilla's own death message is suppressed, so the wording lives here instead. Anything
     * unmapped falls back to a plain "died", which is never wrong, only dull.
     */
    private String causeOf(Player victim) {
        EntityDamageEvent last = victim.getLastDamageCause();
        if (last == null) {
            return "died";
        }

        String byMob = mobKill(last);
        if (byMob != null) {
            return byMob;
        }
        // Asked before the switch: a sourceless blast reports CUSTOM, not an explosion cause.
        if (Damage.isExplosion(last)) {
            return "was blown up";
        }

        return switch (last.getCause()) {
            case FALL -> "fell from a high place";
            case VOID -> "fell out of the world";
            case FIRE, FIRE_TICK, CAMPFIRE -> "burned to death";
            case LAVA -> "tried to swim in lava";
            case HOT_FLOOR -> "discovered the floor was lava";
            case DROWNING -> "drowned";
            case SUFFOCATION -> "suffocated";
            case STARVATION -> "starved to death";
            case FREEZE -> "froze to death";
            case LIGHTNING -> "was struck by lightning";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "was blown up";
            case FALLING_BLOCK -> "was squashed by a falling block";
            case FLY_INTO_WALL -> "experienced kinetic energy";
            case CONTACT -> "was pricked to death";
            case THORNS -> "was killed trying to hurt someone";
            case POISON, MAGIC, WITHER -> "withered away";
            case CRAMMING -> "was squished too much";
            case DRYOUT -> "died out of water";
            case SUICIDE -> "gave up";
            case WORLD_BORDER -> "strayed too far from the map";
            // BorderTask deals plain damage, which arrives with no cause of its own.
            case CUSTOM -> outsideBorder(victim) ? "strayed too far from the map" : "died";
            default -> "died";
        };
    }

    /** "was slain by a zombie" / "was shot by a skeleton", or null if no mob was involved. */
    private String mobKill(EntityDamageEvent last) {
        if (!(last instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        boolean shot = false;
        if (damager instanceof Projectile projectile) {
            shot = true;
            ProjectileSource shooter = projectile.getShooter();
            if (!(shooter instanceof Entity source)) {
                return "was shot";
            }
            damager = source;
        }
        if (damager instanceof Player) {
            return null; // a player kill that never reached getKiller(); let the switch phrase it
        }

        String name = damager.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String article = "aeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ";
        return (shot ? "was shot by " : "was slain by ") + article + name;
    }

    /** Whether they died outside the play area, which is the only damage BorderTask deals. */
    private boolean outsideBorder(Player victim) {
        Location location = victim.getLocation();
        double limit = game.config().borderSize() / 2.0D;
        return Math.abs(location.getX() - game.config().centerX()) > limit
                || Math.abs(location.getZ() - game.config().centerZ()) > limit;
    }

    /** "Username(Kit)" — the kit is part of the kill line on these servers. */
    private String label(Player player) {
        UUID uuid = player.getUniqueId();
        Kit kit = game.kits().selectedFor(uuid);
        return player.getName() + "(" + (kit == null ? "None" : kit.displayName()) + ")";
    }

    /**
     * What the killer was holding.
     *
     * <p>A named item — the Barbarian's Tyrfing, anything renamed on an anvil — is a proper
     * noun and loses the article: "killed by X with Tyrfing", not "with a Tyrfing".
     *
     * @param proper whether the name stands on its own without an article
     */
    private record Weapon(String name, boolean proper, boolean fists) {

        /** The "with ..." half of the second kill-line phrasing. */
        String phrase() {
            if (fists) {
                return " with their fists";
            }
            return (proper ? " with " : " with a ") + name;
        }
    }

    private Weapon weaponOf(Player killer) {
        ItemStack held = killer.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            return new Weapon("fists", false, true);
        }
        ItemMeta meta = held.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return new Weapon(PlainTextComponentSerializer.plainText().serialize(meta.displayName()),
                    true, false);
        }
        return new Weapon(held.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '), false, false);
    }

    /** Safety net: if anything ever does respawn, put them at the centre rather than adrift. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(game.config().center());
    }
}
