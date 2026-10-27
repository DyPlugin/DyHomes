package dev.dyhomes.map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One layer per enabled squaremap world, drawn as small orange circles with the name as tooltip. */
public final class SquaremapLayer implements MapLayer {
    private static final Key KEY = Key.of("dyhomes_points");
    private static final Color ORANGE = new Color(0xF57C00);
    private final Map<String, SimpleLayerProvider> layers = new HashMap<>();
    private final Map<String, MapWorld> worlds = new HashMap<>();

    @Override
    public String name() {
        return "squaremap";
    }

    @Override
    public synchronized void update(List<MapPoint> points, String layerName) {
        Squaremap api = SquaremapProvider.get();
        for (World world : Bukkit.getWorlds()) {
            Optional<MapWorld> mapWorld = api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world));
            if (mapWorld.isEmpty()) continue;
            SimpleLayerProvider layer = layers.computeIfAbsent(world.getName(), name -> {
                SimpleLayerProvider created = SimpleLayerProvider.builder(layerName).showControls(true).defaultHidden(false)
                        .layerPriority(6).zIndex(260).build();
                if (!mapWorld.get().layerRegistry().hasEntry(KEY)) mapWorld.get().layerRegistry().register(KEY, created);
                worlds.put(name, mapWorld.get());
                return created;
            });
            layer.clearMarkers();
            for (MapPoint point : points) {
                if (!point.world().equals(world.getName())) continue;
                layer.addMarker(Key.of(point.id().replaceAll("[^a-zA-Z0-9_]", "_")),
                        Marker.circle(Point.of(point.x(), point.z()), 4).markerOptions(MarkerOptions.builder()
                                .fillColor(ORANGE).fillOpacity(0.8).strokeColor(Color.WHITE).strokeWeight(2).hoverTooltip(point.label())));
            }
        }
    }

    @Override
    public synchronized void close() {
        worlds.forEach((name, world) -> {
            if (world.layerRegistry().hasEntry(KEY)) world.layerRegistry().unregister(KEY);
        });
        worlds.clear();
        layers.clear();
    }
}
