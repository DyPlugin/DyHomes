package dev.dyhomes.teleport;

import dev.dyhomes.DyHomes;
import dev.dyhomes.claim.ClaimAccess;
import dev.dyhomes.economy.EconomyService;
import dev.dyhomes.lang.MessageService;
import dev.dyhomes.rank.RankRules;
import dev.dyhomes.scheduler.Scheduler;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeleportServiceTest {
    private final YamlConfiguration config = new YamlConfiguration();
    private final DyHomes plugin = mock(DyHomes.class);
    private final MessageService messages = mock(MessageService.class);
    private final EconomyService economy = mock(EconomyService.class);
    private final ClaimAccess claims = mock(ClaimAccess.class);
    private final Scheduler scheduler = mock(Scheduler.class);
    private final PluginManager plugins = mock(PluginManager.class);
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private final Scheduler.Task task = mock(Scheduler.Task.class);
    private final EconomyService.Withdrawal payment = mock(EconomyService.Withdrawal.class);
    private TeleportService service;
    private Location destination;

    @BeforeEach void setUp() {
        Server server = mock(Server.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        when(server.getPluginManager()).thenReturn(plugins);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenAnswer(call -> new Location(world, 0.5, 64, 0.5));
        when(messages.get(any(), anyString(), anyMap())).thenReturn("time");
        when(claims.canUse(any(), any())).thenReturn(ClaimAccess.Result.ALLOWED);
        when(economy.withdraw(any(), anyDouble())).thenReturn(payment);
        when(scheduler.loadChunk(any())).thenAnswer(call -> CompletableFuture.completedFuture(null));
        when(scheduler.supplyAt(any(), any())).thenAnswer(call -> CompletableFuture.completedFuture(SafetyCheck.Result.SAFE));
        when(scheduler.run(any(), any())).thenAnswer(call -> {
            ((Runnable) call.getArgument(1)).run();
            return task;
        });
        when(scheduler.runLater(any(), any(), anyLong())).thenReturn(task);
        when(scheduler.teleport(any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        config.set("warmup.seconds", 3);
        destination = new Location(world, 100.5, 70, 100.5);
        service = new TeleportService(plugin, messages, economy, claims, scheduler, new RankRules(() -> config));
    }

    private Runnable warmupCallback() {
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runLater(eq(player), callback.capture(), eq(60L));
        return callback.getValue();
    }

    @Test void warmupThenTeleportThenCooldown() {
        config.set("cooldown.home", 30);
        service.request(player, TeleportKind.HOME, "base", destination, "/home base force", false);
        verify(messages).send(eq(player), eq("teleport.warmup"), anyMap());
        assertTrue(service.hasPending(player));
        verify(scheduler, never()).teleport(any(), any());
        warmupCallback().run();
        verify(scheduler).teleport(player, destination);
        verify(messages).send(eq(player), eq("teleport.success-home"), anyMap());
        assertFalse(service.hasPending(player));
        assertTrue(service.cooldownRemaining(player, TeleportKind.HOME) > 25);
        assertEquals(0, service.cooldownRemaining(player, TeleportKind.WARP), "cooldowns are per kind");
        service.request(player, TeleportKind.HOME, "base", destination, "/home base force", false);
        verify(messages).send(eq(player), eq("teleport.cooldown"), anyMap());
    }

    @Test void movingCancelsTheWarmupAndTheLateCallbackDoesNothing() {
        service.request(player, TeleportKind.WARP, "shop", destination, null, false);
        Runnable callback = warmupCallback();
        service.cancel(player, "move");
        verify(task).cancel();
        verify(messages).send(eq(player), eq("teleport.cancel-move"), anyMap());
        callback.run();
        verify(scheduler, never()).teleport(any(), any());
    }

    @Test void anUnsafeDestinationIsRefusedWithAHintAndForceSkipsTheCheck() {
        when(scheduler.supplyAt(any(), any())).thenAnswer(call -> CompletableFuture.completedFuture(SafetyCheck.Result.HAZARD));
        config.set("warmup.seconds", 0);
        service.request(player, TeleportKind.HOME, "base", destination, "/home base force", false);
        verify(messages).send(eq(player), eq("teleport.unsafe-hazard"), anyMap());
        ArgumentCaptor<Map<String, String>> values = ArgumentCaptor.captor();
        verify(messages).send(eq(player), eq("teleport.unsafe-hint"), values.capture());
        assertEquals("/home base force", values.getValue().get("{command}"));
        verify(scheduler, never()).teleport(any(), any());
        service.request(player, TeleportKind.HOME, "base", destination, "/home base force", true);
        verify(scheduler).teleport(player, destination);
    }

    @Test void claimAccessIsCheckedForHomesOnly() {
        config.set("warmup.seconds", 0);
        when(claims.canUse(any(), any())).thenReturn(ClaimAccess.Result.DENIED);
        service.request(player, TeleportKind.HOME, "base", destination, null, false);
        verify(messages).send(eq(player), eq("teleport.claim-denied"), anyMap());
        when(claims.canUse(any(), any())).thenReturn(ClaimAccess.Result.UNAVAILABLE);
        service.request(player, TeleportKind.HOME, "base", destination, null, false);
        verify(messages).send(eq(player), eq("teleport.claim-unavailable"), anyMap());
        service.request(player, TeleportKind.WARP, "shop", destination, null, false);
        verify(scheduler).teleport(player, destination);
    }

    @Test void priceIsChargedOnlyOnSuccessAndRefundedOnFailure() {
        config.set("warmup.seconds", 0);
        when(economy.price(player, EconomyService.Action.SPAWN)).thenReturn(50.0);
        when(economy.has(player, 50.0)).thenReturn(false);
        service.request(player, TeleportKind.SPAWN, null, destination, null, false);
        verify(messages).send(eq(player), eq("teleport.not-enough-money"), anyMap());
        verify(economy, never()).withdraw(any(), anyDouble());
        when(economy.has(player, 50.0)).thenReturn(true);
        when(scheduler.teleport(any(), any())).thenReturn(CompletableFuture.completedFuture(false));
        service.request(player, TeleportKind.SPAWN, null, destination, null, false);
        verify(economy).withdraw(player, 50.0);
        verify(payment).refund();
        verify(messages).send(eq(player), eq("teleport.failed"), anyMap());
    }

    @Test void aMissingWorldIsReported() {
        service.request(player, TeleportKind.HOME, "old", null, null, false);
        verify(messages).send(eq(player), eq("teleport.world-missing"), anyMap());
    }
}
