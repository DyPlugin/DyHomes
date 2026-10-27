package dev.dyhomes.api.event;

import dev.dyhomes.model.Home;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** A player opened a home to everyone or closed it again. Fired after the change is saved; cannot be cancelled. */
public class HomePublicChangeEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Home home;

    public HomePublicChangeEvent(Player player, Home home) {
        super(player);
        this.home = home;
    }

    /** The home after the change; {@link Home#publicHome()} tells whether it is now public. */
    public Home getHome() { return home; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
