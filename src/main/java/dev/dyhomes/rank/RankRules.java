package dev.dyhomes.rank;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * Per-rank home limit and cooldown from permissions such as dyhomes.limit.5 or dyhomes.cooldown.30.
 * For the limit the highest value wins and dyhomes.limit.unlimited removes it; for the cooldown the lowest wins.
 */
public final class RankRules {
    public static final int UNLIMITED = Integer.MAX_VALUE;
    private final Supplier<FileConfiguration> config;

    public RankRules(Supplier<FileConfiguration> config) {
        this.config = config;
    }

    public int homeLimit(Player player) {
        if (player.hasPermission("dyhomes.limit.unlimited")) return UNLIMITED;
        int base = Math.max(0, config.get().getInt("homes.default-limit", 3));
        OptionalLong rank = pick(player, "dyhomes.limit.", false);
        return rank.isPresent() ? (int) Math.min(UNLIMITED - 1L, rank.getAsLong()) : base;
    }

    /** The cooldown in seconds; a dyhomes.cooldown.&lt;seconds&gt; permission replaces the configured value. */
    public int cooldownSeconds(Player player, int configured) {
        if (!config.get().getBoolean("cooldown.permission-overrides", true)) return configured;
        OptionalLong rank = pick(player, "dyhomes.cooldown.", true);
        return rank.isPresent() ? (int) Math.min(Integer.MAX_VALUE, rank.getAsLong()) : configured;
    }

    static OptionalLong pick(Player player, String prefix, boolean lowest) {
        long best = lowest ? Long.MAX_VALUE : Long.MIN_VALUE;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String node = info.getPermission();
            if (!node.regionMatches(true, 0, prefix, 0, prefix.length())) continue;
            String number = node.substring(prefix.length());
            if (number.isEmpty() || number.length() > 9 || !number.chars().allMatch(Character::isDigit)) continue;
            long value = Long.parseLong(number);
            best = lowest ? Math.min(best, value) : Math.max(best, value);
        }
        return best == (lowest ? Long.MAX_VALUE : Long.MIN_VALUE) ? OptionalLong.empty() : OptionalLong.of(best);
    }
}
