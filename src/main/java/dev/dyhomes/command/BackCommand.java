package dev.dyhomes.command;

import dev.dyhomes.DyHomes;
import dev.dyhomes.lang.MessageService;
import dev.dyhomes.model.SavedLocation;
import dev.dyhomes.teleport.BackService;
import dev.dyhomes.teleport.TeleportKind;
import dev.dyhomes.teleport.TeleportService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** /back: return to where you last teleported from or died, through the usual warmup, cooldown and safety check. */
public class BackCommand implements CommandExecutor, TabCompleter {
    private final DyHomes plugin;
    private final MessageService messages;
    private final BackService back;
    private final TeleportService teleports;

    public BackCommand(DyHomes plugin, MessageService messages, BackService back, TeleportService teleports) {
        this.plugin = plugin;
        this.messages = messages;
        this.back = back;
        this.teleports = teleports;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!sender.hasPermission("dyhomes.back")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (!plugin.getConfig().getBoolean("back.enabled", true)) {
            messages.send(sender, "back.disabled");
            return true;
        }
        SavedLocation target = back.last(player.getUniqueId());
        if (target == null) {
            messages.send(sender, "back.none");
            return true;
        }
        teleports.request(player, TeleportKind.BACK, null, target.toLocation(),
                messages.get(sender, "commands.back") + " " + messages.get(sender, "words.force"), Commands.force(args, 0), null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Commands.complete(args[0], List.of(messages.get(sender, "words.force")));
        return List.of();
    }
}
