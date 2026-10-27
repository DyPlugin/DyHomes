package dev.dyhomes.teleport;

import dev.dyhomes.DyHomes;
import dev.dyhomes.api.event.DyHomesTeleportEvent;
import dev.dyhomes.claim.ClaimAccess;
import dev.dyhomes.economy.EconomyService;
import dev.dyhomes.lang.MessageService;
import dev.dyhomes.rank.RankRules;
import dev.dyhomes.scheduler.Scheduler;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The teleport flow shared by homes, warps and spawn: cooldown, price, DyClaim access, destination safety,
 * the cancellable API event, warmup, payment and the teleport itself. A new request replaces a pending one.
 */
public class TeleportService {
    private final DyHomes plugin;
    private final MessageService messages;
    private final EconomyService economy;
    private final ClaimAccess claims;
    private final Scheduler scheduler;
    private final RankRules ranks;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public TeleportService(DyHomes plugin, MessageService messages, EconomyService economy, ClaimAccess claims,
                           Scheduler scheduler, RankRules ranks) {
        this.plugin = plugin;
        this.messages = messages;
        this.economy = economy;
        this.claims = claims;
        this.scheduler = scheduler;
        this.ranks = ranks;
    }

    /**
     * Starts a teleport. {@code name} is the home or warp name shown in messages (null for spawn);
     * {@code forceCommand} is the command that skips the safety check, shown when the destination is unsafe.
     */
    public void request(Player player, TeleportKind kind, String name, Location destination, String forceCommand, boolean force) {
        request(player, kind, name, destination, forceCommand, force, kind == TeleportKind.HOME ? player.getUniqueId() : null);
    }

    /**
     * Like {@link #request(Player, TeleportKind, String, Location, String, boolean)}, with DyClaim access checked for
     * {@code claimOwner}: the player for their own homes, the owner for a public home, null for no check.
     */
    public void request(Player player, TeleportKind kind, String name, Location destination, String forceCommand, boolean force,
                        UUID claimOwner) {
        cancelSilently(player);
        Map<String, String> values = values(name);
        if (destination == null || destination.getWorld() == null) {
            messages.send(player, "teleport.world-missing", values);
            return;
        }
        long remaining = cooldownRemaining(player, kind);
        if (remaining > 0) {
            values.put("{time}", formatTime(player, remaining));
            messages.send(player, "teleport.cooldown", values);
            return;
        }
        double price = economy.price(player, kind.action());
        if (price > 0 && !economy.has(player, price)) {
            values.put("{cost}", economy.format(price));
            messages.send(player, "teleport.not-enough-money", values);
            return;
        }
        if (claimOwner != null) {
            ClaimAccess.Result access = claimOwner.equals(player.getUniqueId()) ? claims.canUse(player, destination)
                    : claims.canUseAs(claimOwner, player, destination);
            if (access == ClaimAccess.Result.DENIED) {
                messages.send(player, "teleport.claim-denied", values);
                return;
            }
            if (access == ClaimAccess.Result.UNAVAILABLE) {
                messages.send(player, "teleport.claim-unavailable", values);
                return;
            }
        }
        Pending request = new Pending(kind, name, destination.clone(), price);
        pending.put(player.getUniqueId(), request);
        if (force || !plugin.getConfig().getBoolean("safety.enabled", true)) {
            begin(player, request);
            return;
        }
        int maxFall = Math.max(0, plugin.getConfig().getInt("safety.max-fall", 3));
        scheduler.loadChunk(destination)
                .thenCompose(loaded -> scheduler.supplyAt(destination, () -> SafetyCheck.check(destination, maxFall)))
                .whenComplete((result, failure) -> scheduler.run(player, () -> {
                    if (pending.get(player.getUniqueId()) != request) return;
                    if (failure != null) {
                        plugin.getLogger().warning("Destination check failed: " + failure);
                        pending.remove(player.getUniqueId(), request);
                        messages.send(player, "teleport.failed", values);
                        return;
                    }
                    if (result != SafetyCheck.Result.SAFE) {
                        pending.remove(player.getUniqueId(), request);
                        messages.send(player, "teleport.unsafe-" + result.name().toLowerCase(Locale.ROOT), values);
                        if (forceCommand != null) {
                            values.put("{command}", forceCommand);
                            messages.send(player, "teleport.unsafe-hint", values);
                        }
                        return;
                    }
                    begin(player, request);
                }));
    }

    /** Teleports without warmup, cooldown, price or checks; used for first-join spawn. */
    public void teleportNow(Player player, Location destination) {
        scheduler.teleport(player, destination);
    }

    private void begin(Player player, Pending request) {
        if (!player.isOnline()) {
            pending.remove(player.getUniqueId(), request);
            return;
        }
        DyHomesTeleportEvent event = new DyHomesTeleportEvent(player, request.kind, request.name, request.destination);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            pending.remove(player.getUniqueId(), request);
            return;
        }
        int seconds = player.hasPermission("dyhomes.bypass.warmup") ? 0
                : Math.max(0, plugin.getConfig().getInt("warmup.seconds", 3));
        if (seconds == 0) {
            execute(player, request);
            return;
        }
        request.start = player.getLocation().clone();
        Map<String, String> values = values(request.name);
        values.put("{seconds}", String.valueOf(seconds));
        messages.send(player, "teleport.warmup", values);
        sound(player, "warmup");
        request.task = scheduler.runLater(player, () -> {
            if (pending.get(player.getUniqueId()) == request) execute(player, request);
        }, seconds * 20L);
    }

    private void execute(Player player, Pending request) {
        if (!pending.remove(player.getUniqueId(), request) || !player.isOnline()) return;
        Map<String, String> values = values(request.name);
        EconomyService.Withdrawal payment = economy.withdraw(player, request.price);
        if (payment == null) {
            values.put("{cost}", economy.format(request.price));
            messages.send(player, "teleport.not-enough-money", values);
            return;
        }
        Location from = player.getLocation().clone();
        scheduler.teleport(player, request.destination).whenComplete((success, failure) -> scheduler.run(player, () -> {
            if (failure != null || !Boolean.TRUE.equals(success)) {
                payment.refund();
                messages.send(player, "teleport.failed", values);
                sound(player, "failed");
                return;
            }
            // Folia fires no PlayerTeleportEvent for asynchronous teleports, so /back learns about our own here too.
            if (plugin.back() != null) plugin.back().teleported(player, from, request.destination);
            startCooldown(player, request.kind);
            messages.send(player, "teleport.success-" + request.kind.key(), values);
            if (payment.amount() > 0) {
                values.put("{cost}", economy.format(payment.amount()));
                messages.send(player, "teleport.charged", values);
            }
            sound(player, "success");
        }));
    }

    public boolean hasPending(Player player) {
        Pending request = pending.get(player.getUniqueId());
        return request != null && request.start != null;
    }

    public Location pendingStart(Player player) {
        Pending request = pending.get(player.getUniqueId());
        return request == null ? null : request.start;
    }

    /** Cancels a warmup and tells the player why ("move" or "damage"). */
    public void cancel(Player player, String reason) {
        Pending request = pending.remove(player.getUniqueId());
        if (request == null) return;
        if (request.task != null) request.task.cancel();
        messages.send(player, "teleport.cancel-" + reason, values(request.name));
        sound(player, "cancel");
    }

    public void cancelSilently(Player player) {
        Pending request = pending.remove(player.getUniqueId());
        if (request != null && request.task != null) request.task.cancel();
    }

    public void cancelAll() {
        pending.values().forEach(request -> { if (request.task != null) request.task.cancel(); });
        pending.clear();
    }

    public long cooldownRemaining(Player player, TeleportKind kind) {
        if (player.hasPermission("dyhomes.bypass.cooldown")) return 0L;
        long expiresAt = cooldowns.getOrDefault(player.getUniqueId() + ":" + kind.key(), 0L);
        return Math.max(0L, (expiresAt - System.currentTimeMillis() + 999L) / 1000L);
    }

    private void startCooldown(Player player, TeleportKind kind) {
        int configured = Math.max(0, plugin.getConfig().getInt("cooldown." + kind.key(), 0));
        int seconds = ranks.cooldownSeconds(player, configured);
        if (seconds > 0) cooldowns.put(player.getUniqueId() + ":" + kind.key(), System.currentTimeMillis() + seconds * 1000L);
    }

    public void clearCooldowns() {
        cooldowns.clear();
    }

    public String formatTime(CommandSender sender, long seconds) {
        long hours = seconds / 3600, minutes = seconds % 3600 / 60, rest = seconds % 60;
        Map<String, String> values = Map.of("{hours}", String.valueOf(hours), "{minutes}", String.valueOf(minutes),
                "{seconds}", String.valueOf(rest));
        if (hours > 0) return messages.get(sender, "time.hours", values);
        if (minutes > 0) return messages.get(sender, "time.minutes", values);
        return messages.get(sender, "time.seconds", values);
    }

    private void sound(Player player, String event) {
        if (!plugin.getConfig().getBoolean("feedback.sounds.enabled", true)) return;
        String name = plugin.getConfig().getString("feedback.sounds." + event, "");
        if (name == null || name.isBlank()) return;
        try {
            player.playSound(player.getLocation(), name.toLowerCase(Locale.ROOT),
                    (float) plugin.getConfig().getDouble("feedback.sounds.volume", 1.0),
                    (float) plugin.getConfig().getDouble("feedback.sounds.pitch", 1.0));
        } catch (RuntimeException ignored) {
            // An unknown sound name only skips the sound.
        }
    }

    private static Map<String, String> values(String name) {
        Map<String, String> values = new HashMap<>();
        values.put("{name}", name == null ? "" : name);
        return values;
    }

    private static final class Pending {
        final TeleportKind kind;
        final String name;
        final Location destination;
        final double price;
        volatile Location start;
        volatile Scheduler.Task task;

        Pending(TeleportKind kind, String name, Location destination, double price) {
            this.kind = kind;
            this.name = name;
            this.destination = destination;
            this.price = price;
        }
    }
}
