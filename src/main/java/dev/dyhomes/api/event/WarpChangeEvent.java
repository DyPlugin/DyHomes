package dev.dyhomes.api.event;

import dev.dyhomes.model.Warp;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A warp was created, moved or deleted. Fired after the change is saved and cannot be cancelled.
 * {@link #getWarp()} is the new state, or the deleted warp when {@link #isDeleted()}.
 */
public class WarpChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CommandSender sender;
    private final Warp warp;
    private final boolean created;
    private final boolean deleted;

    public WarpChangeEvent(CommandSender sender, Warp warp, boolean created, boolean deleted) {
        this.sender = sender;
        this.warp = warp;
        this.created = created;
        this.deleted = deleted;
    }

    public CommandSender getSender() { return sender; }
    public Warp getWarp() { return warp; }
    /** True for a new warp, false when an existing one was moved or deleted. */
    public boolean isCreated() { return created; }
    public boolean isDeleted() { return deleted; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
