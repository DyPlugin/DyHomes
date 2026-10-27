package dev.dyhomes.teleport;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackServiceTest {
    private final YamlConfiguration config = new YamlConfiguration();
    private final BackService back = new BackService(() -> config);
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private final World events = mock(World.class);
    private final UUID id = UUID.randomUUID();

    BackServiceTest() {
        when(player.getUniqueId()).thenReturn(id);
        when(world.getName()).thenReturn("world");
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(events.getName()).thenReturn("events");
        when(events.getUID()).thenReturn(UUID.randomUUID());
    }

    @Test void remembersWhereATeleportStartedButNotSmallMoves() {
        back.teleported(player, new Location(world, 0, 64, 0), new Location(world, 2, 64, 0));
        assertNull(back.last(id), "a two-block correction is not a place to go back to");
        back.teleported(player, new Location(world, 0, 64, 0), new Location(world, 500, 64, 0));
        assertEquals(0, back.last(id).x());
        back.died(player, new Location(world, 77, 64, 77));
        assertEquals(77, back.last(id).x(), "death replaces the last position");
    }

    @Test void ignoredTeleportsSwitchesAndWorldsAreRespected() {
        back.ignoreNext(id);
        back.teleported(player, new Location(world, 0, 64, 0), new Location(world, 500, 64, 0));
        assertNull(back.last(id), "the spawn teleport after joining is not remembered");
        config.set("back.disabled-worlds", List.of("EVENTS"));
        back.teleported(player, new Location(events, 0, 64, 0), new Location(world, 500, 64, 0));
        assertNull(back.last(id));
        config.set("back.on-death", false);
        back.died(player, new Location(world, 9, 64, 9));
        assertNull(back.last(id));
        back.teleported(player, new Location(world, 1, 64, 1), new Location(world, 500, 64, 0));
        assertNotNull(back.last(id));
        back.forget(id);
        assertNull(back.last(id));
    }
}
