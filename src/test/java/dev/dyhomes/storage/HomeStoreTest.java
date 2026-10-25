package dev.dyhomes.storage;

import dev.dyhomes.model.Home;
import dev.dyhomes.model.SavedLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class HomeStoreTest {
    @TempDir File folder;
    private final UUID owner = UUID.randomUUID();
    private final SavedLocation spot = new SavedLocation("world", UUID.randomUUID(), 10.5, 64, -3.5, 90f, 0f);

    @Test void homesSurviveAReloadAndNamesIgnoreCase() throws IOException {
        HomeStore store = new HomeStore(folder, Logger.getLogger("test"));
        store.put(new Home(owner, "Base", spot, 1L));
        store.put(new Home(owner, "mine", spot, 2L));
        HomeStore reloaded = new HomeStore(folder, Logger.getLogger("test"));
        assertEquals(2, reloaded.count(owner));
        Home base = reloaded.home(owner, "BASE").orElseThrow();
        assertEquals("Base", base.name(), "the typed spelling is kept");
        assertEquals(spot, base.location());
        assertEquals(java.util.List.of("Base", "mine"), reloaded.homes(owner).stream().map(Home::name).toList());
    }

    @Test void movingAHomeReplacesItAndDeletingTheLastRemovesTheFile() throws IOException {
        HomeStore store = new HomeStore(folder, Logger.getLogger("test"));
        store.put(new Home(owner, "home", spot, 1L));
        SavedLocation moved = new SavedLocation("world", spot.worldId(), 0, 70, 0, 0f, 0f);
        Home previous = store.put(new Home(owner, "HOME", moved, 1L));
        assertNotNull(previous);
        assertEquals(1, store.count(owner));
        assertEquals(moved, store.home(owner, "home").orElseThrow().location());
        File file = new File(folder, "homes/" + owner + ".yml");
        assertTrue(file.exists());
        assertNotNull(store.remove(owner, "home"));
        assertFalse(file.exists());
        assertNull(store.remove(owner, "home"));
    }

    @Test void anUnreadableFileIsNeverWrittenOver() throws Exception {
        File file = new File(folder, "homes/" + owner + ".yml");
        file.getParentFile().mkdirs();
        String broken = "homes:\n  base: [unclosed\n";
        Files.writeString(file.toPath(), broken);
        HomeStore store = new HomeStore(folder, Logger.getLogger("test"));
        assertTrue(store.locked(owner));
        assertEquals(0, store.count(owner));
        assertThrows(IOException.class, () -> store.put(new Home(owner, "new", spot, 1L)));
        assertThrows(IOException.class, () -> store.remove(owner, "base"));
        assertEquals(broken, Files.readString(file.toPath()), "the broken file is left for the owner to fix");
    }

    @Test void invalidEntriesAreSkippedAndOthersLoad() throws Exception {
        File file = new File(folder, "homes/" + owner + ".yml");
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), """
                homes:
                  good:
                    name: good
                    world: world
                    x: 1.0
                    y: 64.0
                    z: 1.0
                  broken:
                    name: broken
                    world: world
                """);
        HomeStore store = new HomeStore(folder, Logger.getLogger("test"));
        assertFalse(store.locked(owner));
        assertEquals(java.util.List.of("good"), store.homes(owner).stream().map(Home::name).toList());
    }

    @Test void warpsAndSpawnPersist() throws Exception {
        WarpStore warps = new WarpStore(folder, Logger.getLogger("test"));
        warps.put(new dev.dyhomes.model.Warp("Shop", spot, null, 5L));
        assertEquals("Shop", new WarpStore(folder, Logger.getLogger("test")).warp("shop").orElseThrow().name());
        assertNotNull(warps.remove("SHOP"));
        assertTrue(new WarpStore(folder, Logger.getLogger("test")).warps().isEmpty());
        SpawnStore spawn = new SpawnStore(folder, Logger.getLogger("test"));
        assertNull(spawn.spawn());
        spawn.set(spot);
        assertEquals(spot, new SpawnStore(folder, Logger.getLogger("test")).spawn());
    }
}
