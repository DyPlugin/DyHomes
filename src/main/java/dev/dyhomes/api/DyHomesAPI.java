package dev.dyhomes.api;

import dev.dyhomes.DyHomes;
import dev.dyhomes.model.Home;
import dev.dyhomes.model.Warp;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stable entry point for other plugins. Add {@code softdepend: [DyHomes]} and check {@link #isAvailable()} first.
 * All methods only read; changes are announced through the events in {@code dev.dyhomes.api.event}.
 */
public final class DyHomesAPI {
    private DyHomesAPI() {
    }

    public static boolean isAvailable() {
        DyHomes plugin = DyHomes.getInstance();
        return plugin != null && plugin.isEnabled() && plugin.homeStore() != null;
    }

    /** A player's homes, sorted by name. Works for offline players too. */
    public static List<Home> getHomes(UUID player) {
        return plugin().homeStore().homes(player);
    }

    public static Optional<Home> getHome(UUID player, String name) {
        return plugin().homeStore().home(player, name);
    }

    /** The player's home limit including dyhomes.limit.&lt;n&gt; permissions; Integer.MAX_VALUE when unlimited. */
    public static int getHomeLimit(Player player) {
        return plugin().ranks().homeLimit(player);
    }

    public static List<Warp> getWarps() {
        return plugin().warpStore().warps();
    }

    public static Optional<Warp> getWarp(String name) {
        return plugin().warpStore().warp(name);
    }

    /** Where /spawn leads, or empty when no spawn is available. */
    public static Optional<Location> getSpawn() {
        return Optional.ofNullable(plugin().spawnLocation());
    }

    private static DyHomes plugin() {
        DyHomes plugin = DyHomes.getInstance();
        if (plugin == null || !plugin.isEnabled() || plugin.homeStore() == null) throw new IllegalStateException("DyHomes is not enabled");
        return plugin;
    }
}
