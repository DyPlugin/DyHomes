package dev.dyhomes.command;

import dev.dyhomes.DyHomes;
import dev.dyhomes.api.event.HomeDeleteEvent;
import dev.dyhomes.api.event.HomeSetEvent;
import dev.dyhomes.claim.ClaimAccess;
import dev.dyhomes.economy.EconomyService;
import dev.dyhomes.lang.MessageService;
import dev.dyhomes.model.Home;
import dev.dyhomes.model.Names;
import dev.dyhomes.model.SavedLocation;
import dev.dyhomes.rank.RankRules;
import dev.dyhomes.storage.HomeStore;
import dev.dyhomes.teleport.TeleportKind;
import dev.dyhomes.teleport.TeleportService;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /home, /homes, /sethome and /delhome. Administrators reach other players with "player:home". */
public class HomeCommands implements CommandExecutor, TabCompleter {
    private final DyHomes plugin;
    private final MessageService messages;
    private final HomeStore homes;
    private final TeleportService teleports;
    private final ClaimAccess claims;
    private final EconomyService economy;
    private final RankRules ranks;

    public HomeCommands(DyHomes plugin, MessageService messages, HomeStore homes, TeleportService teleports,
                        ClaimAccess claims, EconomyService economy, RankRules ranks) {
        this.plugin = plugin;
        this.messages = messages;
        this.homes = homes;
        this.teleports = teleports;
        this.claims = claims;
        this.economy = economy;
        this.ranks = ranks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName()) {
            case "home" -> home(sender, args);
            case "homes" -> list(sender, args);
            case "sethome" -> set(sender, args);
            case "delhome" -> delete(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void home(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (!sender.hasPermission("dyhomes.home")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        Target target = target(sender, args.length == 0 ? null : args[0]);
        if (target == null) return;
        Home home;
        if (target.name == null) {
            List<Home> owned = homes.homes(target.owner);
            home = homes.home(target.owner, Names.DEFAULT_HOME).orElse(owned.size() == 1 ? owned.get(0) : null);
            if (home == null) {
                if (owned.isEmpty()) messages.send(sender, target.other ? "homes.none-other" : "homes.none",
                        Map.of("{player}", target.ownerName));
                else list(sender, target.other ? new String[]{target.ownerName} : new String[0]);
                return;
            }
        } else {
            home = homes.home(target.owner, target.name).orElse(null);
            if (home == null) {
                messages.send(sender, "homes.not-found", Map.of("{name}", target.name));
                return;
            }
        }
        String shown = target.other ? target.ownerName + ":" + home.name() : home.name();
        teleports.request(player, TeleportKind.HOME, shown, home.location().toLocation(), messages.get(sender, "commands.home") + " " + shown + " " + messages.get(sender, "words.force"),
                Commands.force(args, 1));
    }

    private void list(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dyhomes.home")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        UUID owner;
        String prefix = messages.get(sender, "commands.home") + " ";
        String ownerName;
        if (args.length > 0) {
            if (!sender.hasPermission("dyhomes.admin.others")) {
                messages.send(sender, "general.no-permission");
                return;
            }
            OfflinePlayer found = Commands.player(args[0]);
            if (found == null) {
                messages.send(sender, "general.player-not-found", Map.of("{player}", args[0]));
                return;
            }
            owner = found.getUniqueId();
            ownerName = found.getName() == null ? args[0] : found.getName();
            prefix = messages.get(sender, "commands.home") + " " + ownerName + ":";
        } else if (sender instanceof Player player) {
            owner = player.getUniqueId();
            ownerName = player.getName();
        } else {
            messages.send(sender, "general.player-only");
            return;
        }
        List<Home> owned = homes.homes(owner);
        if (owned.isEmpty()) {
            messages.send(sender, args.length > 0 ? "homes.none-other" : "homes.none", Map.of("{player}", ownerName));
            return;
        }
        Map<String, String> values = new HashMap<>();
        values.put("{count}", String.valueOf(owned.size()));
        values.put("{player}", ownerName);
        values.put("{limit}", args.length == 0 && sender instanceof Player player ? limitText(sender, ranks.homeLimit(player)) : "?");
        Commands.sendNames(sender, messages, args.length > 0 ? "homes.list-other" : "homes.list", values,
                owned.stream().map(Home::name).toList(), prefix);
    }

    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (!sender.hasPermission("dyhomes.sethome")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        String name = args.length == 0 ? Names.DEFAULT_HOME : args[0];
        if (!Names.valid(name)) {
            messages.send(sender, "homes.invalid-name", Map.of("{name}", name));
            return;
        }
        UUID owner = player.getUniqueId();
        if (plugin.getConfig().getStringList("homes.disabled-worlds").stream()
                .anyMatch(world -> world.equalsIgnoreCase(player.getWorld().getName()))
                && !player.hasPermission("dyhomes.bypass.worlds")) {
            messages.send(sender, "homes.world-disabled");
            return;
        }
        if (homes.locked(owner)) {
            messages.send(sender, "homes.storage-locked");
            return;
        }
        Home previous = homes.home(owner, name).orElse(null);
        int limit = ranks.homeLimit(player);
        if (previous == null && homes.count(owner) >= limit) {
            messages.send(sender, "homes.limit", Map.of("{limit}", limitText(sender, limit)));
            return;
        }
        ClaimAccess.Result access = claims.canSet(player, player.getLocation());
        if (access != ClaimAccess.Result.ALLOWED) {
            messages.send(sender, access == ClaimAccess.Result.DENIED ? "homes.claim-denied" : "teleport.claim-unavailable");
            return;
        }
        Home home = new Home(owner, previous == null ? name : previous.name(), SavedLocation.of(player.getLocation()),
                previous == null ? System.currentTimeMillis() : previous.createdAt());
        HomeSetEvent event = new HomeSetEvent(player, home, previous);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        double price = economy.price(player, EconomyService.Action.SETHOME);
        EconomyService.Withdrawal payment = economy.withdraw(player, price);
        if (payment == null) {
            messages.send(sender, "teleport.not-enough-money", Map.of("{cost}", economy.format(price)));
            return;
        }
        try {
            homes.put(home);
        } catch (IOException failure) {
            payment.refund();
            plugin.getLogger().warning("Could not save home " + home.name() + " for " + player.getName() + ": " + failure.getMessage());
            messages.send(sender, "homes.save-failed");
            return;
        }
        Map<String, String> values = new HashMap<>();
        values.put("{name}", home.name());
        values.put("{count}", String.valueOf(homes.count(owner)));
        values.put("{limit}", limitText(sender, limit));
        messages.send(sender, previous == null ? "homes.set" : "homes.moved", values);
        if (payment.amount() > 0) messages.send(sender, "teleport.charged", Map.of("{cost}", economy.format(payment.amount())));
    }

    private void delete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dyhomes.sethome")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length == 0) {
            messages.send(sender, "homes.delete-usage");
            return;
        }
        if (!(sender instanceof Player) && !args[0].contains(":")) {
            messages.send(sender, "general.player-only");
            return;
        }
        Target target = target(sender, args[0]);
        if (target == null) return;
        Home home = homes.home(target.owner, target.name).orElse(null);
        if (home == null) {
            messages.send(sender, "homes.not-found", Map.of("{name}", target.name));
            return;
        }
        if (homes.locked(target.owner)) {
            messages.send(sender, "homes.storage-locked");
            return;
        }
        HomeDeleteEvent event = new HomeDeleteEvent(sender, home);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        try {
            homes.remove(target.owner, home.name());
        } catch (IOException failure) {
            plugin.getLogger().warning("Could not delete home " + home.name() + ": " + failure.getMessage());
            messages.send(sender, "homes.save-failed");
            return;
        }
        messages.send(sender, "homes.deleted", Map.of("{name}", target.other ? target.ownerName + ":" + home.name() : home.name()));
    }

    /** Reads "home" or, for administrators, "player:home". Sends the error and returns null when it cannot. */
    private Target target(CommandSender sender, String argument) {
        if (argument != null && argument.contains(":")) {
            if (!sender.hasPermission("dyhomes.admin.others")) {
                messages.send(sender, "general.no-permission");
                return null;
            }
            String[] parts = argument.split(":", 2);
            OfflinePlayer found = Commands.player(parts[0]);
            if (found == null) {
                messages.send(sender, "general.player-not-found", Map.of("{player}", parts[0]));
                return null;
            }
            String name = parts[1].isEmpty() ? null : parts[1];
            return new Target(found.getUniqueId(), found.getName() == null ? parts[0] : found.getName(), name, true);
        }
        Player player = (Player) sender;
        return new Target(player.getUniqueId(), player.getName(), argument, false);
    }

    private String limitText(CommandSender sender, int limit) {
        return limit == RankRules.UNLIMITED ? messages.get(sender, "homes.unlimited") : String.valueOf(limit);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        String name = command.getName();
        if (args.length == 1 && (name.equals("home") || name.equals("delhome"))) {
            if (args[0].contains(":") && sender.hasPermission("dyhomes.admin.others")) {
                String[] parts = args[0].split(":", 2);
                OfflinePlayer found = Commands.player(parts[0]);
                if (found == null) return List.of();
                return Commands.complete(args[0], homes.homes(found.getUniqueId()).stream()
                        .map(home -> parts[0] + ":" + home.name()).toList());
            }
            return Commands.complete(args[0], homes.homes(player.getUniqueId()).stream().map(Home::name).toList());
        }
        if (args.length == 2 && name.equals("home")) return Commands.complete(args[1], List.of(messages.get(sender, "words.force")));
        if (args.length == 1 && name.equals("homes") && sender.hasPermission("dyhomes.admin.others")) {
            return Commands.complete(args[0], plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return List.of();
    }

    private record Target(UUID owner, String ownerName, String name, boolean other) {
    }
}
