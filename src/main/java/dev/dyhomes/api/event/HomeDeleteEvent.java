package dev.dyhomes.api.event;

import dev.dyhomes.model.Home;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired before a home is deleted, by its owner or by an administrator ({@link #getSender()}).
 * Cancelling it keeps the home.
 */
public class HomeDeleteEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CommandSender sender;
    private final Home home;
    private boolean cancelled;

    public HomeDeleteEvent(CommandSender sender, Home home) {
        this.sender = sender;
        this.home = home;
    }

    public CommandSender getSender() { return sender; }
    public Home getHome() { return home; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
