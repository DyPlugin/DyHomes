package dev.dyhomes.hook;

import dev.dyhomes.DyHomes;
import dev.dyhomes.model.Home;
import dev.dyhomes.rank.RankRules;
import dev.dyhomes.teleport.TeleportKind;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Built-in %dyhomes_...% placeholders. Only loaded when PlaceholderAPI is installed, through register(),
 * so DyHomes never needs PlaceholderAPI classes otherwise.
 */
public final class PlaceholderHook extends PlaceholderExpansion {
    private final DyHomes plugin;

    public PlaceholderHook(DyHomes plugin) {
        this.plugin = plugin;
    }

    /** Returns the registered expansion, or null when registration failed. */
    public static Object register(DyHomes plugin) {
        PlaceholderHook hook = new PlaceholderHook(plugin);
        return hook.register() ? hook : null;
    }

    public static void unregister(Object hook) {
        if (hook instanceof PlaceholderHook expansion) expansion.unregister();
    }

    @Override public String getIdentifier() { return "dyhomes"; }
    @Override public String getAuthor() { return "Claww"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("warps")) return String.valueOf(plugin.warpStore().warps().size());
        Player player = offline == null ? null : offline.getPlayer();
        if (player == null) return "";
        var homes = plugin.homeStore();
        return switch (key) {
            case "count" -> String.valueOf(homes.count(player.getUniqueId()));
            case "limit" -> limit(plugin.ranks().homeLimit(player));
            case "remaining" -> {
                int limit = plugin.ranks().homeLimit(player);
                yield limit == RankRules.UNLIMITED ? limit(limit)
                        : String.valueOf(Math.max(0, limit - homes.count(player.getUniqueId())));
            }
            case "names" -> homes.homes(player.getUniqueId()).stream().map(Home::name).collect(Collectors.joining(", "));
            case "cooldown" -> String.valueOf(plugin.teleports().cooldownRemaining(player, TeleportKind.HOME));
            case "cooldown_formatted" -> {
                long seconds = plugin.teleports().cooldownRemaining(player, TeleportKind.HOME);
                yield seconds > 0 ? plugin.teleports().formatTime(player, seconds) : "";
            }
            default -> null;
        };
    }

    private String limit(int limit) {
        return limit == RankRules.UNLIMITED ? "∞" : String.valueOf(limit);
    }
}
