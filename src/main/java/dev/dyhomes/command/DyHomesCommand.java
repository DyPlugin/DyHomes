package dev.dyhomes.command;

import dev.dyhomes.DyHomes;
import dev.dyhomes.importer.EssentialsImporter;
import dev.dyhomes.lang.Language;
import dev.dyhomes.lang.MessageService;
import dev.dyhomes.model.Home;
import dev.dyhomes.model.Warp;
import dev.dyhomes.storage.HomeStore;
import dev.dyhomes.storage.PlayerDataStore;
import dev.dyhomes.storage.SpawnStore;
import dev.dyhomes.storage.WarpStore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** /dyhomes: help, language, reload and the EssentialsX import. Turkish subcommands are accepted too. */
public class DyHomesCommand implements CommandExecutor, TabCompleter {
    private final DyHomes plugin;
    private final MessageService messages;
    private final PlayerDataStore players;
    private final HomeStore homes;
    private final WarpStore warps;
    private final SpawnStore spawns;
    private final AtomicBoolean importing = new AtomicBoolean();

    public DyHomesCommand(DyHomes plugin, MessageService messages, PlayerDataStore players, HomeStore homes,
                          WarpStore warps, SpawnStore spawns) {
        this.plugin = plugin;
        this.messages = messages;
        this.players = players;
        this.homes = homes;
        this.warps = warps;
        this.spawns = spawns;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || Commands.matches(args[0], "help", "yardım", "yardim")) help(sender);
        else if (Commands.matches(args[0], "language", "lang", "dil")) language(sender, args);
        else if (Commands.matches(args[0], "reload", "yenile")) reload(sender);
        else if (Commands.matches(args[0], "import", "aktar")) importData(sender, args);
        else messages.send(sender, "general.unknown-command");
        return true;
    }

    private void help(CommandSender sender) {
        for (String line : messages.getList(sender, "help.player")) sender.sendMessage(line);
        if (sender.hasPermission("dyhomes.setwarp") || sender.hasPermission("dyhomes.setspawn") || sender.hasPermission("dyhomes.admin")) {
            for (String line : messages.getList(sender, "help.admin")) sender.sendMessage(line);
        }
    }

    private void language(CommandSender sender, String[] args) {
        boolean admin = sender.hasPermission("dyhomes.admin");
        if (args.length < 2) {
            messages.send(sender, "general.invalid-language");
            return;
        }
        String code = Commands.matches(args[1], "oto", "otomatik") ? "auto" : args[1].toLowerCase(java.util.Locale.ROOT);
        if (!code.equals("auto") && !Language.codes().contains(code)) {
            messages.send(sender, "general.invalid-language");
            return;
        }
        if (sender instanceof Player player) {
            players.setLanguage(player.getUniqueId(), code);
            messages.send(sender, "general.language-changed", Map.of("{language}", messages.get(sender, "value.language-" + code)));
            return;
        }
        if (!admin) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (!plugin.applyConfigChanges(Map.of("language.default", code))) {
            messages.send(sender, "general.reload-failed");
            return;
        }
        messages.send(sender, "general.server-language-changed", Map.of("{language}", messages.get(sender, "value.language-" + code)));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("dyhomes.admin")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        messages.send(sender, plugin.tryReloadPlugin() ? "general.reload" : "general.reload-failed");
    }

    private void importData(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dyhomes.admin")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2 || !Commands.matches(args[1], "essentials", "essentialsx")) {
            messages.send(sender, "import.usage");
            return;
        }
        EssentialsImporter importer = new EssentialsImporter(plugin.getDataFolder().getParentFile(), plugin.getLogger());
        if (!importer.present()) {
            messages.send(sender, "import.not-found");
            return;
        }
        if (!importing.compareAndSet(false, true)) {
            messages.send(sender, "import.running");
            return;
        }
        messages.send(sender, "import.started");
        plugin.scheduler().runAsync(() -> {
            try {
                EssentialsImporter.Result read = importer.read();
                int[] counts = apply(read);
                plugin.scheduler().runGlobal(() -> {
                    Map<String, String> values = new java.util.HashMap<>();
                    values.put("{homes}", String.valueOf(counts[0]));
                    values.put("{warps}", String.valueOf(counts[1]));
                    values.put("{spawn}", messages.get(sender, counts[2] == 1 ? "value.yes" : "value.no"));
                    values.put("{kept}", String.valueOf(counts[3]));
                    values.put("{skipped}", String.valueOf(read.skipped + read.unreadable + counts[4]));
                    messages.send(sender, "import.done", values);
                });
            } catch (RuntimeException failure) {
                plugin.getLogger().warning("Essentials import failed: " + failure);
                plugin.scheduler().runGlobal(() -> messages.send(sender, "import.failed"));
            } finally {
                importing.set(false);
            }
        });
    }

    /** Adds what DyHomes does not have yet: homes, warps, kept, failed. Never replaces an existing entry. */
    private int[] apply(EssentialsImporter.Result read) {
        int addedHomes = 0, addedWarps = 0, addedSpawn = 0, kept = 0, failed = 0;
        List<java.util.UUID> touched = new ArrayList<>();
        for (Home home : read.homes) {
            if (homes.home(home.owner(), home.name()).isPresent()) {
                kept++;
                continue;
            }
            try {
                homes.put(home);
                addedHomes++;
                touched.add(home.owner());
            } catch (IOException failure) {
                failed++;
            }
        }
        for (Warp warp : read.warps) {
            if (warps.warp(warp.name()).isPresent()) {
                kept++;
                continue;
            }
            try {
                warps.put(warp);
                addedWarps++;
            } catch (IOException failure) {
                failed++;
            }
        }
        if (read.spawn != null && spawns.spawn() == null) {
            try {
                spawns.set(read.spawn);
                addedSpawn = 1;
            } catch (IOException failure) {
                failed++;
            }
        }
        // Offline players' homes need not stay in memory after the import.
        for (java.util.UUID owner : touched) if (plugin.getServer().getPlayer(owner) == null) homes.unload(owner);
        return new int[]{addedHomes, addedWarps, addedSpawn, kept, failed};
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean turkish = messages.language(sender) == Language.TR;
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of(turkish ? "yardım" : "help", turkish ? "dil" : "language"));
            if (sender.hasPermission("dyhomes.admin")) {
                options.add(turkish ? "yenile" : "reload");
                options.add(turkish ? "aktar" : "import");
            }
            return Commands.complete(args[0], options);
        }
        if (args.length == 2 && Commands.matches(args[0], "language", "lang", "dil")) {
            List<String> codes = new ArrayList<>();
            codes.add(turkish ? "oto" : "auto");
            codes.addAll(Language.codes());
            return Commands.complete(args[1], codes);
        }
        if (args.length == 2 && Commands.matches(args[0], "import", "aktar") && sender.hasPermission("dyhomes.admin")) {
            return Commands.complete(args[1], List.of("essentials"));
        }
        return List.of();
    }
}
