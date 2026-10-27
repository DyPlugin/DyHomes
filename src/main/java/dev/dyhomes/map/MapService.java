package dev.dyhomes.map;

import dev.dyhomes.DyHomes;
import dev.dyhomes.model.Home;
import dev.dyhomes.model.Names;
import dev.dyhomes.model.Warp;
import dev.dyhomes.scheduler.Scheduler;
import dev.dyhomes.storage.PublicHomeIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Warps, and optionally public homes, as markers on BlueMap, Dynmap and squaremap. Points are collected off the
 * server thread; Dynmap and squaremap are drawn on it and are skipped on Folia, where BlueMap still works.
 */
public class MapService {
    private final DyHomes plugin;
    private final List<MapLayer> layers = new ArrayList<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile boolean again;
    private Scheduler.Task watcher;

    public MapService(DyHomes plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        stop();
        if (!plugin.getConfig().getBoolean("maps.enabled", true)) return;
        add("BlueMap", "bluemap", "BlueMapLayer");
        if (!Scheduler.FOLIA) {
            add("dynmap", "dynmap", "DynmapLayer");
            add("squaremap", "squaremap", "SquaremapLayer");
        }
        if (layers.isEmpty()) return;
        refresh();
        // Map plugins may reload and forget DyHomes' markers; check once a minute and draw them again.
        watcher = plugin.scheduler().runGlobalTimer(() -> {
            boolean lost;
            synchronized (this) {
                lost = layers.stream().anyMatch(MapLayer::lost);
            }
            if (lost) refresh();
        }, 1200L, 1200L);
    }

    /** Layers are loaded by name so a missing map plugin can never break DyHomes class loading. */
    private void add(String pluginName, String switchName, String layerClass) {
        if (!plugin.getConfig().getBoolean("maps." + switchName, true)) return;
        var other = plugin.getServer().getPluginManager().getPlugin(pluginName);
        if (other == null || !other.isEnabled()) return;
        try {
            MapLayer layer = (MapLayer) Class.forName("dev.dyhomes.map." + layerClass).getDeclaredConstructor().newInstance();
            layers.add(layer);
            plugin.getLogger().info(layer.name() + " warp markers enabled.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            plugin.getLogger().warning(pluginName + " markers could not start: " + failure);
        }
    }

    /** Collects the points off the server thread, then draws them; calls during a refresh are merged into one more. */
    public void refresh() {
        synchronized (this) {
            if (layers.isEmpty()) return;
        }
        if (!refreshing.compareAndSet(false, true)) {
            again = true;
            return;
        }
        plugin.scheduler().runAsync(() -> {
            List<MapPoint> points;
            try {
                points = points();
            } catch (RuntimeException failure) {
                plugin.getLogger().warning("Could not collect map markers: " + failure);
                refreshing.set(false);
                return;
            }
            String layerName = plain(plugin.messages().get(plugin.getServer().getConsoleSender(), "map.layer"));
            Runnable draw = () -> {
                try {
                    List<MapLayer> current;
                    synchronized (this) {
                        current = List.copyOf(layers);
                    }
                    for (MapLayer layer : current) {
                        try {
                            layer.update(points, layerName);
                        } catch (RuntimeException | LinkageError failure) {
                            plugin.getLogger().warning(layer.name() + " markers could not be updated: " + failure);
                        }
                    }
                } finally {
                    refreshing.set(false);
                    if (again) {
                        again = false;
                        refresh();
                    }
                }
            };
            if (Scheduler.FOLIA) draw.run();
            else plugin.scheduler().runGlobal(draw);
        });
    }

    List<MapPoint> points() {
        var console = plugin.getServer().getConsoleSender();
        List<MapPoint> points = new ArrayList<>();
        for (Warp warp : plugin.warpStore().warps()) {
            points.add(new MapPoint("dyhomes_warp_" + warp.key(), plain(plugin.messages().get(console, "map.warp", Map.of("{name}", warp.name()))),
                    warp.location().world(), warp.location().x(), warp.location().y(), warp.location().z()));
        }
        if (plugin.getConfig().getBoolean("maps.public-homes", false) && plugin.getConfig().getBoolean("public-homes.enabled", true)) {
            for (PublicHomeIndex.Entry entry : plugin.publicHomes().entries()) {
                Home home = plugin.homeStore().peek(entry.owner()).stream()
                        .filter(found -> found.key().equals(Names.key(entry.home())) && found.publicHome()).findFirst().orElse(null);
                if (home == null) continue;
                points.add(new MapPoint("dyhomes_home_" + entry.owner() + "_" + home.key(),
                        plain(plugin.messages().get(console, "map.home", Map.of("{name}", home.name(), "{player}", entry.ownerName()))),
                        home.location().world(), home.location().x(), home.location().y(), home.location().z()));
            }
        }
        return points;
    }

    /** Map labels are plain text; colour codes from message files are removed. */
    private static String plain(String text) {
        return org.bukkit.ChatColor.stripColor(text);
    }

    public synchronized void stop() {
        if (watcher != null) {
            watcher.cancel();
            watcher = null;
        }
        for (MapLayer layer : layers) {
            try {
                layer.close();
            } catch (RuntimeException | LinkageError ignored) {
                // The map plugin is already gone.
            }
        }
        layers.clear();
    }

    public synchronized List<String> active() {
        return layers.stream().map(MapLayer::name).toList();
    }
}
