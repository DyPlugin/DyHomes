package dev.dyhomes.command;

import dev.dyhomes.DyHomes;
import dev.dyhomes.lang.MessageService;
import dev.dyhomes.model.SavedLocation;
import dev.dyhomes.storage.SpawnStore;
import dev.dyhomes.teleport.TeleportKind;
import dev.dyhomes.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** /spawn and /setspawn. Without /setspawn, /spawn uses the main world's spawn when spawn.use-world-spawn is on. */
public class SpawnCommands implements CommandExecutor, TabCompleter {
    private final DyHomes plugin;
    private final MessageService messages;
    private final SpawnStore spawns;
    private final TeleportService teleports;

    public SpawnCommands(DyHomes plugin, MessageService messages, SpawnStore spawns, TeleportService teleports) {
        this.plugin = plugin;
        this.messages = messages;
        this.spawns = spawns;
        this.teleports = teleports;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (command.getName().equals("setspawn")) {
            setSpawn(player);
            return true;
        }
        if (!sender.hasPermission("dyhomes.spawn")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        Location spawn = spawnLocation();
        if (spawn == null) {
            messages.send(sender, "spawn.not-set");
            return true;
        }
        teleports.request(player, TeleportKind.SPAWN, null, spawn, messages.get(sender, "commands.spawn") + " " + messages.get(sender, "words.force"), Commands.force(args, 0));
        return true;
    }

    private void setSpawn(Player player) {
        if (!player.hasPermission("dyhomes.setspawn")) {
            messages.send(player, "general.no-permission");
            return;
        }
        Location location = player.getLocation();
        try {
            spawns.set(SavedLocation.of(location));
        } catch (IOException failure) {
            plugin.getLogger().warning("Could not save spawn: " + failure.getMessage());
            messages.send(player, "homes.save-failed");
            return;
        }
        if (plugin.getConfig().getBoolean("spawn.set-world-spawn", true)) {
            try {
                location.getWorld().setSpawnLocation(location);
            } catch (RuntimeException ignored) {
                // The DyHomes spawn is saved either way.
            }
        }
        messages.send(player, "spawn.set", Map.of("{world}", location.getWorld().getName(),
                "{x}", String.valueOf(location.getBlockX()), "{y}", String.valueOf(location.getBlockY()),
                "{z}", String.valueOf(location.getBlockZ())));
    }

    /** The spawn from /setspawn, or the main world's spawn when allowed; null when neither is available. */
    public Location spawnLocation() {
        SavedLocation saved = spawns.spawn();
        if (saved != null) {
            Location location = saved.toLocation();
            if (location != null) return location;
        }
        if (!plugin.getConfig().getBoolean("spawn.use-world-spawn", true)) return null;
        List<World> worlds = plugin.getServer().getWorlds();
        if (worlds.isEmpty()) return null;
        Location spawn = worlds.get(0).getSpawnLocation();
        return new Location(spawn.getWorld(), spawn.getBlockX() + 0.5, spawn.getBlockY(), spawn.getBlockZ() + 0.5);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && command.getName().equals("spawn")) return Commands.complete(args[0], List.of(messages.get(sender, "words.force")));
        return List.of();
    }
}
