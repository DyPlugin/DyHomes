package dev.dyhomes.map;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.List;
import java.util.function.Consumer;

/** BlueMap keeps markers in memory only; after BlueMap reloads, lost() makes DyHomes draw them again. */
public final class BlueMapLayer implements MapLayer {
    private static final String SET = "dyhomes";
    private volatile BlueMapAPI api;
    private volatile boolean fresh;
    private final Consumer<BlueMapAPI> enable = value -> {
        api = value;
        fresh = true;
    };
    private final Consumer<BlueMapAPI> disable = value -> api = null;

    public BlueMapLayer() {
        BlueMapAPI.onEnable(enable);
        BlueMapAPI.onDisable(disable);
    }

    @Override
    public String name() {
        return "BlueMap";
    }

    @Override
    public boolean lost() {
        return fresh;
    }

    @Override
    public void update(List<MapPoint> points, String layerName) {
        BlueMapAPI current = api;
        if (current == null) return;
        fresh = false;
        for (BlueMapMap map : current.getMaps()) {
            MarkerSet set = MarkerSet.builder().label(layerName).toggleable(true).defaultHidden(false).build();
            map.getMarkerSets().put(SET, set);
        }
        for (MapPoint point : points) {
            World world = Bukkit.getWorld(point.world());
            if (world == null) continue;
            current.getWorld(world).ifPresent(bmWorld -> bmWorld.getMaps().forEach(map -> {
                MarkerSet set = map.getMarkerSets().get(SET);
                if (set != null) set.put(point.id(), POIMarker.builder().label(point.label()).detail(point.label())
                        .position(point.x(), point.y(), point.z()).build());
            }));
        }
    }

    @Override
    public void close() {
        BlueMapAPI current = api;
        if (current != null) for (BlueMapMap map : current.getMaps()) map.getMarkerSets().remove(SET);
        BlueMapAPI.unregisterListener(enable);
        BlueMapAPI.unregisterListener(disable);
        api = null;
    }
}
