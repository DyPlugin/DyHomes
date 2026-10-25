package dev.dyhomes.claim;

import dev.dyhomes.DyHomes;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Asks DyClaim whether a player may set or use a home in a claimed chunk. Uses DyClaim's public API
 * (1.1.0 and later) through reflection, so DyHomes runs without DyClaim installed.
 * When DyClaim is installed but cannot answer, the action is refused rather than allowed.
 */
public class ClaimAccess {
    public enum Result { ALLOWED, DENIED, UNAVAILABLE }

    private final DyHomes plugin;
    private volatile boolean warnedMissingApi;

    public ClaimAccess(DyHomes plugin) {
        this.plugin = plugin;
    }

    /** May the player set a home here? Uses the right in dyclaim.set-right (build by default). */
    public Result canSet(Player player, Location location) {
        return check(player, location, plugin.getConfig().getString("dyclaim.set-right", "build"));
    }

    /** May the player teleport to a home here? Uses the right in dyclaim.use-right (teleport by default). */
    public Result canUse(Player player, Location location) {
        return check(player, location, plugin.getConfig().getString("dyclaim.use-right", "teleport"));
    }

    Result check(Player player, Location location, String right) {
        if (!plugin.getConfig().getBoolean("dyclaim.enabled", true) || player.hasPermission("dyhomes.bypass.claims")) {
            return Result.ALLOWED;
        }
        Plugin dyClaim = plugin.getServer().getPluginManager().getPlugin("DyClaim");
        if (dyClaim == null) return Result.ALLOWED;
        if (!dyClaim.isEnabled()) return Result.UNAVAILABLE;
        try {
            Class<?> api = Class.forName("dev.dyclaim.api.DyClaimAPI", true, dyClaim.getClass().getClassLoader());
            if (!Boolean.TRUE.equals(api.getMethod("isAvailable").invoke(null))) return Result.UNAVAILABLE;
            Object found = api.getMethod("getClaimAt", Location.class).invoke(null, location);
            if (!(found instanceof Optional<?> optional)) return Result.UNAVAILABLE;
            if (optional.isEmpty()) return Result.ALLOWED;
            Class<?> claimType = Class.forName("dev.dyclaim.api.Claim", true, dyClaim.getClass().getClassLoader());
            Method allows = claimType.getMethod("allows", UUID.class, String.class);
            Object allowed = allows.invoke(optional.get(), player.getUniqueId(), right);
            if (!(allowed instanceof Boolean value)) return Result.UNAVAILABLE;
            return value ? Result.ALLOWED : Result.DENIED;
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            if (!warnedMissingApi) {
                warnedMissingApi = true;
                plugin.getLogger().warning("This DyClaim version has no public API; update DyClaim to 1.1.0 or later "
                        + "for claim checks. Homes are not checked against claims until then.");
            }
            return Result.ALLOWED;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            plugin.getLogger().warning("DyClaim could not answer a claim check: " + failure);
            return Result.UNAVAILABLE;
        }
    }
}
