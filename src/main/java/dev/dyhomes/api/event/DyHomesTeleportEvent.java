package dev.dyhomes.api.event;

import dev.dyhomes.teleport.TeleportKind;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired before a home, warp or spawn teleport starts its warmup. Cancelling it stops the teleport;
 * the cancelling plugin is expected to tell the player why. {@link #getName()} is the home or warp name,
 * or null for spawn.
 */
public class DyHomesTeleportEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final TeleportKind kind;
    private final String name;
    private final Location destination;
    private boolean cancelled;

    public DyHomesTeleportEvent(Player player, TeleportKind kind, String name, Location destination) {
        super(player);
        this.kind = kind;
        this.name = name;
        this.destination = destination;
    }

    public TeleportKind getKind() { return kind; }
    public String getName() { return name; }
    public Location getDestination() { return destination.clone(); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
