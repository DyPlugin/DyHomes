package dev.dyhomes.command;

import dev.dyhomes.DyHomes;
import dev.dyhomes.lang.MessageService;
import dev.dyhomes.model.Names;
import dev.dyhomes.model.SavedLocation;
import dev.dyhomes.model.Warp;
import dev.dyhomes.storage.WarpStore;
import dev.dyhomes.teleport.TeleportKind;
import dev.dyhomes.teleport.TeleportService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** /warp, /warps, /setwarp and /delwarp. With warps.per-warp-permission each warp needs dyhomes.warp.&lt;name&gt;. */
public class WarpCommands implements CommandExecutor, TabCompleter {
    private final DyHomes plugin;
    private final MessageService messages;
    private final WarpStore warps;
    private final TeleportService teleports;

    public WarpCommands(DyHomes plugin, MessageService messages, WarpStore warps, TeleportService teleports) {
        this.plugin = plugin;
        this.messages = messages;
        this.warps = warps;
        this.teleports = teleports;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName()) {
            case "warp" -> {
                if (args.length == 0) list(sender);
                else warp(sender, args);
            }
            case "warps" -> list(sender);
            case "setwarp" -> set(sender, args);
            case "delwarp" -> delete(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void warp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (!sender.hasPermission("dyhomes.warp")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        Warp warp = warps.warp(args[0]).filter(found -> canUse(sender, found)).orElse(null);
        if (warp == null) {
            messages.send(sender, "warps.not-found", Map.of("{name}", args[0]));
            return;
        }
        teleports.request(player, TeleportKind.WARP, warp.name(), warp.location().toLocation(),
                messages.get(sender, "commands.warp") + " " + warp.name() + " " + messages.get(sender, "words.force"), Commands.force(args, 1));
    }

    private void list(CommandSender sender) {
        if (!sender.hasPermission("dyhomes.warp")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        List<String> names = usable(sender);
        if (names.isEmpty()) {
            messages.send(sender, "warps.none");
            return;
        }
        Commands.sendNames(sender, messages, "warps.list", Map.of("{count}", String.valueOf(names.size())), names, messages.get(sender, "commands.warp") + " ");
    }

    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (!sender.hasPermission("dyhomes.setwarp")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length == 0) {
            messages.send(sender, "warps.set-usage");
            return;
        }
        if (!Names.valid(args[0])) {
            messages.send(sender, "homes.invalid-name", Map.of("{name}", args[0]));
            return;
        }
        Warp previous = warps.warp(args[0]).orElse(null);
        Warp warp = new Warp(previous == null ? args[0] : previous.name(), SavedLocation.of(player.getLocation()),
                player.getUniqueId(), System.currentTimeMillis());
        try {
            warps.put(warp);
        } catch (IOException failure) {
            plugin.getLogger().warning("Could not save warp " + warp.name() + ": " + failure.getMessage());
            messages.send(sender, "homes.save-failed");
            return;
        }
        plugin.getServer().getPluginManager().callEvent(new dev.dyhomes.api.event.WarpChangeEvent(sender, warp, previous == null, false));
        plugin.maps().refresh();
        messages.send(sender, previous == null ? "warps.set" : "warps.moved", Map.of("{name}", warp.name()));
    }

    private void delete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dyhomes.delwarp")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length == 0) {
            messages.send(sender, "warps.delete-usage");
            return;
        }
        Warp removed;
        try {
            removed = warps.remove(args[0]);
        } catch (IOException failure) {
            plugin.getLogger().warning("Could not delete warp " + args[0] + ": " + failure.getMessage());
            messages.send(sender, "homes.save-failed");
            return;
        }
        if (removed == null) messages.send(sender, "warps.not-found", Map.of("{name}", args[0]));
        else {
            plugin.getServer().getPluginManager().callEvent(new dev.dyhomes.api.event.WarpChangeEvent(sender, removed, false, true));
            plugin.maps().refresh();
            messages.send(sender, "warps.deleted", Map.of("{name}", removed.name()));
        }
    }

    private boolean canUse(CommandSender sender, Warp warp) {
        return !plugin.getConfig().getBoolean("warps.per-warp-permission", false)
                || sender.hasPermission("dyhomes.warp." + warp.key()) || sender.hasPermission("dyhomes.warp.*");
    }

    private List<String> usable(CommandSender sender) {
        return warps.warps().stream().filter(warp -> canUse(sender, warp)).map(Warp::name).toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName();
        if (args.length == 1 && name.equals("warp") && sender.hasPermission("dyhomes.warp")) return Commands.complete(args[0], usable(sender));
        if (args.length == 1 && name.equals("delwarp") && sender.hasPermission("dyhomes.delwarp"))
            return Commands.complete(args[0], warps.warps().stream().map(Warp::name).toList());
        if (args.length == 2 && name.equals("warp")) return Commands.complete(args[1], List.of(messages.get(sender, "words.force")));
        return List.of();
    }
}
