package dev.dyhomes.teleport;

import dev.dyhomes.model.SavedLocation;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Where /back leads: the place a player last teleported away from or died. Kept in memory only, so it
 * is cleared when the player leaves or the server restarts, like most /back commands.
 */
public class BackService {
    /** Teleports shorter than this, in blocks, are treated as small corrections and not remembered. */
    private static final double MIN_DISTANCE = 4.0D;

    private final Supplier<FileConfiguration> config;
    private final Map<UUID, SavedLocation> last = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> ignored = ConcurrentHashMap.newKeySet();

    public BackService(Supplier<FileConfiguration> config) {
        this.config = config;
    }

    /** Remembers {@code from} before a teleport to {@code to}, unless the move is tiny or the world is excluded. */
    public void teleported(Player player, Location from, Location to) {
        if (ignored.remove(player.getUniqueId())) return;
        if (!config.get().getBoolean("back.enabled", true) || !config.get().getBoolean("back.on-teleport", true)) return;
        if (from == null || from.getWorld() == null || to == null || to.getWorld() == null) return;
        if (from.getWorld().equals(to.getWorld()) && from.distanceSquared(to) < MIN_DISTANCE * MIN_DISTANCE) return;
        remember(player, from);
    }

    public void died(Player player, Location at) {
        if (!config.get().getBoolean("back.enabled", true) || !config.get().getBoolean("back.on-death", true)) return;
        if (at != null && at.getWorld() != null) remember(player, at);
    }

    private void remember(Player player, Location location) {
        boolean excluded = config.get().getStringList("back.disabled-worlds").stream()
                .anyMatch(world -> world.equalsIgnoreCase(location.getWorld().getName()));
        if (!excluded) last.put(player.getUniqueId(), SavedLocation.of(location));
    }

    /** The remembered place, or null. */
    public SavedLocation last(UUID player) {
        return last.get(player);
    }

    public void forget(UUID player) {
        last.remove(player);
        ignored.remove(player);
    }

    /** The player's next teleport is DyHomes moving them after joining or respawning; it is not a place to return to. */
    public void ignoreNext(UUID player) {
        ignored.add(player);
    }
}
