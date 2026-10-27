package dev.dyhomes.command;

import dev.dyhomes.DyHomes;
import dev.dyhomes.api.event.HomeSetEvent;
import dev.dyhomes.claim.ClaimAccess;
import dev.dyhomes.economy.EconomyService;
import dev.dyhomes.lang.MessageService;
import dev.dyhomes.model.Home;
import dev.dyhomes.model.SavedLocation;
import dev.dyhomes.model.Warp;
import dev.dyhomes.rank.RankRules;
import dev.dyhomes.storage.HomeStore;
import dev.dyhomes.storage.PublicHomeIndex;
import dev.dyhomes.storage.WarpStore;
import dev.dyhomes.teleport.TeleportKind;
import dev.dyhomes.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HomeCommandsTest {
    @TempDir File folder;
    private final YamlConfiguration config = new YamlConfiguration();
    private final DyHomes plugin = mock(DyHomes.class);
    private final MessageService messages = mock(MessageService.class);
    private final TeleportService teleports = mock(TeleportService.class);
    private final ClaimAccess claims = mock(ClaimAccess.class);
    private final EconomyService economy = mock(EconomyService.class);
    private final PluginManager plugins = mock(PluginManager.class);
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private HomeStore homes;
    private PublicHomeIndex publicIndex;
    private HomeCommands commands;
    private org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit;

    @org.junit.jupiter.api.AfterEach void tearDown() {
        bukkit.close();
    }

    @BeforeEach void setUp() {
        bukkit = mockStatic(org.bukkit.Bukkit.class);
        bukkit.when(() -> org.bukkit.Bukkit.getWorld(any(UUID.class))).thenReturn(world);
        bukkit.when(() -> org.bukkit.Bukkit.getWorld(anyString())).thenReturn(world);
        Server server = mock(Server.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.maps()).thenReturn(mock(dev.dyhomes.map.MapService.class));
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(server.getPluginManager()).thenReturn(plugins);
        when(world.getName()).thenReturn("world");
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alex");
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenAnswer(call -> new Location(world, 1.5, 64, 2.5));
        when(player.getEffectivePermissions()).thenReturn(java.util.Set.of());
        when(player.hasPermission(anyString())).thenAnswer(call -> !((String) call.getArgument(0)).startsWith("dyhomes.admin")
                && !((String) call.getArgument(0)).startsWith("dyhomes.limit") && !((String) call.getArgument(0)).startsWith("dyhomes.bypass"));
        when(messages.get(any(), anyString())).thenAnswer(call -> switch ((String) call.getArgument(1)) {
            case "commands.home" -> "/home";
            case "commands.phome" -> "/phome";
            case "words.force" -> "force";
            default -> "";
        });
        when(messages.get(any(), anyString(), anyMap())).thenReturn("");
        when(claims.canSet(any(), any())).thenReturn(ClaimAccess.Result.ALLOWED);
        when(economy.withdraw(any(), anyDouble())).thenReturn(mock(EconomyService.Withdrawal.class));
        config.set("homes.default-limit", 2);
        homes = new HomeStore(folder, Logger.getLogger("test"));
        publicIndex = new PublicHomeIndex(folder, Logger.getLogger("test"));
        commands = new HomeCommands(plugin, messages, homes, teleports, claims, economy, new RankRules(() -> config), publicIndex);
    }

    private void run(String name, String... args) {
        Command command = mock(Command.class);
        when(command.getName()).thenReturn(name);
        commands.onCommand(player, command, name, args);
    }

    @Test void sethomeRespectsTheLimitButMayMoveAnExistingHome() {
        run("sethome");
        run("sethome", "Farm");
        assertEquals(2, homes.count(player.getUniqueId()));
        run("sethome", "third");
        verify(messages).send(eq(player), eq("homes.limit"), anyMap());
        assertEquals(2, homes.count(player.getUniqueId()));
        run("sethome", "farm");
        verify(messages).send(eq(player), eq("homes.moved"), anyMap());
        assertEquals("Farm", homes.home(player.getUniqueId(), "farm").orElseThrow().name());
    }

    @Test void invalidNamesClaimsAndCancelledEventsStopSethome() {
        run("sethome", "bad.name");
        verify(messages).send(eq(player), eq("homes.invalid-name"), anyMap());
        when(claims.canSet(any(), any())).thenReturn(ClaimAccess.Result.DENIED);
        run("sethome", "base");
        verify(messages).send(player, "homes.claim-denied");
        when(claims.canSet(any(), any())).thenReturn(ClaimAccess.Result.ALLOWED);
        doAnswer(call -> {
            if (call.getArgument(0) instanceof HomeSetEvent event) event.setCancelled(true);
            return null;
        }).when(plugins).callEvent(any());
        run("sethome", "base");
        assertEquals(0, homes.count(player.getUniqueId()));
        config.set("homes.disabled-worlds", java.util.List.of("WORLD"));
        run("sethome", "base");
        verify(messages).send(player, "homes.world-disabled");
    }

    @Test void homeWithoutANameUsesHomeOrTheOnlyOne() throws Exception {
        run("home");
        verify(messages).send(eq(player), eq("homes.none"), anyMap());
        homes.put(new Home(player.getUniqueId(), "Base", SavedLocation.of(new Location(world, 5, 64, 5)), 1L));
        run("home");
        verify(teleports).request(eq(player), eq(TeleportKind.HOME), eq("Base"), any(), eq("/home Base force"), eq(false));
        run("home", "base", "force");
        verify(teleports).request(eq(player), eq(TeleportKind.HOME), eq("Base"), any(), anyString(), eq(true));
        run("home", "missing");
        verify(messages).send(eq(player), eq("homes.not-found"), anyMap());
    }

    @Test void otherPlayersHomesNeedTheAdminPermission() {
        run("home", "Steve:base");
        verify(messages).send(player, "general.no-permission");
        run("delhome", "Steve:base");
        verify(messages, times(2)).send(player, "general.no-permission");
    }

    @Test void delhomeRemovesTheHome() throws Exception {
        homes.put(new Home(player.getUniqueId(), "Base", SavedLocation.of(new Location(world, 5, 64, 5)), 1L));
        run("delhome", "BASE");
        assertEquals(0, homes.count(player.getUniqueId()));
        verify(messages).send(eq(player), eq("homes.deleted"), anyMap());
    }

    @Test void movingAPublicHomeKeepsItPublic() throws Exception {
        run("sethome", "shop");
        run("publichome", "shop");
        assertTrue(homes.home(player.getUniqueId(), "shop").orElseThrow().publicHome());
        assertEquals(1, publicIndex.entries().size());
        run("sethome", "shop");
        assertTrue(homes.home(player.getUniqueId(), "shop").orElseThrow().publicHome(), "moving keeps the public flag");
        run("publichome", "shop");
        assertFalse(homes.home(player.getUniqueId(), "shop").orElseThrow().publicHome());
        assertTrue(publicIndex.entries().isEmpty());
    }

    @Test void publicHomesRespectTheLimitAndAreRemovedWithTheHome() throws Exception {
        config.set("public-homes.max-per-player", 1);
        run("sethome", "one");
        run("sethome", "two");
        run("publichome", "one");
        run("publichome", "two");
        verify(messages).send(eq(player), eq("public.limit"), anyMap());
        run("delhome", "one");
        assertTrue(publicIndex.entries().isEmpty(), "deleting a public home removes it from the list");
    }

    @Test void visitingAPublicHomeChecksTheOwnersClaimAccess() throws Exception {
        UUID owner = UUID.randomUUID();
        Home shop = new Home(owner, "Shop", SavedLocation.of(new Location(world, 9, 64, 9)), 1L, true);
        homes.put(shop);
        publicIndex.update(shop, "Steve", true);
        run("phome", "steve:shop");
        verify(teleports).request(eq(player), eq(TeleportKind.HOME), eq("Steve:Shop"), any(), eq("/phome Steve:Shop force"), eq(false), eq(owner));
        run("phome", "shop");
        verify(teleports, times(2)).request(eq(player), eq(TeleportKind.HOME), eq("Steve:Shop"), any(), anyString(), eq(false), eq(owner));
        homes.put(shop.withPublic(false));
        run("phome", "steve:shop");
        verify(messages).send(eq(player), eq("public.not-found"), anyMap());
        assertTrue(publicIndex.entries().isEmpty(), "a home closed behind the list is dropped from it");
    }

    @Test void perWarpPermissionHidesWarps() throws Exception {
        WarpStore warps = new WarpStore(folder, Logger.getLogger("test"));
        warps.put(new Warp("Shop", SavedLocation.of(new Location(world, 0, 64, 0)), null, 1L));
        WarpCommands warpCommands = new WarpCommands(plugin, messages, warps, teleports);
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("warp");
        when(messages.get(any(), eq("commands.warp"))).thenReturn("/warp");
        config.set("warps.per-warp-permission", true);
        when(player.hasPermission("dyhomes.warp.shop")).thenReturn(false);
        when(player.hasPermission("dyhomes.warp.*")).thenReturn(false);
        warpCommands.onCommand(player, command, "warp", new String[]{"shop"});
        verify(messages).send(eq(player), eq("warps.not-found"), anyMap());
        assertTrue(warpCommands.onTabComplete(player, command, "warp", new String[]{""}).isEmpty());
        when(player.hasPermission("dyhomes.warp.shop")).thenReturn(true);
        warpCommands.onCommand(player, command, "warp", new String[]{"shop"});
        verify(teleports).request(eq(player), eq(TeleportKind.WARP), eq("Shop"), any(), anyString(), eq(false));
    }
}
