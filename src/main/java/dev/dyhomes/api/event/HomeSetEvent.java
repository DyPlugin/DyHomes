package dev.dyhomes.api.event;

import dev.dyhomes.model.Home;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired before a home is created or moved with /sethome. {@link #getPrevious()} is the home being moved, or null
 * for a new one. Cancelling it keeps everything as it was.
 */
public class HomeSetEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Home home;
    private final Home previous;
    private boolean cancelled;

    public HomeSetEvent(Player player, Home home, Home previous) {
        super(player);
        this.home = home;
        this.previous = previous;
    }

    public Home getHome() { return home; }
    public Home getPrevious() { return previous; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
