package dev.dyhomes.map;

import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

import java.util.ArrayList;
import java.util.List;

/** Dynmap's marker API is main-thread only; DyHomes calls it from the server thread and not on Folia. */
public final class DynmapLayer implements MapLayer {
    private static final String SET = "dyhomes.points";
    private volatile MarkerAPI markers;
    private volatile boolean fresh;
    private final DynmapCommonAPIListener listener = new DynmapCommonAPIListener() {
        @Override
        public void apiEnabled(DynmapCommonAPI api) {
            markers = api.markerAPIInitialized() ? api.getMarkerAPI() : null;
            fresh = true;
        }

        @Override
        public void apiDisabled(DynmapCommonAPI api) {
            markers = null;
        }
    };

    public DynmapLayer() {
        DynmapCommonAPIListener.register(listener);
    }

    @Override
    public String name() {
        return "Dynmap";
    }

    @Override
    public boolean lost() {
        return fresh;
    }

    @Override
    public void update(List<MapPoint> points, String layerName) {
        MarkerAPI api = markers;
        if (api == null) return;
        MarkerSet set = api.getMarkerSet(SET);
        if (set == null) set = api.createMarkerSet(SET, layerName, null, false);
        if (set == null) return;
        fresh = false;
        set.setMarkerSetLabel(layerName);
        for (Marker old : new ArrayList<>(set.getMarkers())) old.deleteMarker();
        MarkerIcon icon = api.getMarkerIcon("house");
        if (icon == null) icon = api.getMarkerIcon(MarkerIcon.DEFAULT);
        for (MapPoint point : points) {
            set.createMarker(point.id(), point.label(), point.world(), point.x(), point.y(), point.z(), icon, false);
        }
    }

    @Override
    public void close() {
        MarkerAPI api = markers;
        if (api != null) {
            MarkerSet set = api.getMarkerSet(SET);
            if (set != null) set.deleteMarkerSet();
        }
        DynmapCommonAPIListener.unregister(listener);
        markers = null;
    }
}
