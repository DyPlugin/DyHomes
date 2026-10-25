package dev.dyhomes.command;

import dev.dyhomes.lang.MessageService;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Helpers shared by the command classes. */
final class Commands {
    private Commands() {
    }

    static boolean matches(String value, String... options) {
        if (value == null) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String option : options) if (normalized.equals(option)) return true;
        return false;
    }

    /** "force" or Turkish "zorla" as the last argument skips the destination safety check. */
    static boolean force(String[] args, int index) {
        return args.length > index && matches(args[index], "force", "zorla", "-f");
    }

    static List<String> complete(String typed, Collection<String> options) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(option);
        return result;
    }

    /** A player by exact online name, then by a name the server has seen before. Null when unknown. */
    @SuppressWarnings("deprecation")
    static OfflinePlayer player(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer known : Bukkit.getOfflinePlayers()) {
            if (known.getName() != null && known.getName().equalsIgnoreCase(name)) return known;
        }
        return null;
    }

    /**
     * Sends a header and a line of names. For players each name is clickable and runs {@code command + name};
     * the console gets plain text.
     */
    static void sendNames(CommandSender sender, MessageService messages, String header, Map<String, String> values,
                          List<String> names, String command) {
        messages.send(sender, header, values);
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get(sender, "list.names", Map.of("{names}", String.join(", ", names))));
            return;
        }
        String hover = messages.get(sender, "list.hover");
        String separator = messages.get(sender, "list.separator");
        List<BaseComponent> parts = new ArrayList<>();
        for (BaseComponent part : TextComponent.fromLegacyText(messages.get(sender, "list.names", Map.of("{names}", "")))) parts.add(part);
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) for (BaseComponent part : TextComponent.fromLegacyText(separator)) parts.add(part);
            String name = names.get(i);
            for (BaseComponent part : TextComponent.fromLegacyText(messages.get(sender, "list.name", Map.of("{name}", name)))) {
                part.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command + name));
                part.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover.replace("{command}", command + name))));
                parts.add(part);
            }
        }
        player.spigot().sendMessage(parts.toArray(new BaseComponent[0]));
    }
}
