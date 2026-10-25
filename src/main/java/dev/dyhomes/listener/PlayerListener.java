package dev.dyhomes.listener;

import dev.dyhomes.DyHomes;
import dev.dyhomes.command.SpawnCommands;
import dev.dyhomes.storage.HomeStore;
import dev.dyhomes.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import dev.dyhomes.scheduler.Scheduler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Warmup cancellation, spawn on first join and respawn, and unloading homes when a player leaves. */
public class PlayerListener implements Listener {
    private final DyHomes plugin;
    private final TeleportService teleports;
    private final HomeStore homes;
    private final SpawnCommands spawn;

    public PlayerListener(DyHomes plugin, TeleportService teleports, HomeStore homes, SpawnCommands spawn) {
        this.plugin = plugin;
        this.teleports = teleports;
        this.homes = homes;
        this.spawn = spawn;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("warmup.cancel-on-move", true)) return;
        Player player = event.getPlayer();
        if (!teleports.hasPending(player)) return;
        Location start = teleports.pendingStart(player);
        Location to = event.getTo();
        if (start == null || to == null) return;
        if (start.getBlockX() != to.getBlockX() || start.getBlockY() != to.getBlockY() || start.getBlockZ() != to.getBlockZ()
                || !start.getWorld().equals(to.getWorld())) {
            teleports.cancel(player, "move");
        }
    }

    /** Another plugin or command moving the player during the warmup counts as moving. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.getConfig().getBoolean("warmup.cancel-on-move", true) && teleports.hasPending(event.getPlayer())) {
            teleports.cancel(event.getPlayer(), "move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!plugin.getConfig().getBoolean("warmup.cancel-on-damage", true)) return;
        if (event.getEntity() instanceof Player player && teleports.hasPending(player)) teleports.cancel(player, "damage");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teleports.cancelSilently(event.getPlayer());
        homes.unload(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore() || !plugin.getConfig().getBoolean("spawn.on-first-join", true)) return;
        if (plugin.spawnStore().spawn() == null) return; // Only a spawn set with /setspawn moves new players.
        plugin.scheduler().runLater(player, () -> {
            Location destination = spawn.spawnLocation();
            if (player.isOnline() && destination != null) teleports.teleportNow(player, destination);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        teleports.cancelSilently(event.getPlayer());
        if (!plugin.getConfig().getBoolean("spawn.on-respawn", true) || plugin.spawnStore().spawn() == null) return;
        if (event.isBedSpawn() || event.isAnchorSpawn()) return;
        Location destination = spawn.spawnLocation();
        if (destination != null) event.setRespawnLocation(destination);
    }

    /**
     * Folia does not fire PlayerRespawnEvent. There the player is moved to the spawn once they are alive again,
     * unless they have a bed or respawn anchor.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        teleports.cancelSilently(player);
        if (!Scheduler.FOLIA || !plugin.getConfig().getBoolean("spawn.on-respawn", true) || plugin.spawnStore().spawn() == null) return;
        if (player.getBedSpawnLocation() != null) return;
        awaitRespawn(player, 0);
    }

    private void awaitRespawn(Player player, int attempts) {
        plugin.scheduler().runLater(player, () -> {
            if (!player.isOnline()) return;
            if (player.isDead()) {
                if (attempts < 1200) awaitRespawn(player, attempts + 1); // Waits up to ten minutes on the death screen.
                return;
            }
            Location destination = spawn.spawnLocation();
            if (destination != null) teleports.teleportNow(player, destination);
        }, 10L);
    }
}
