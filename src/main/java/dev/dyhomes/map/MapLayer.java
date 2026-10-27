package dev.dyhomes.map;

import java.util.List;

/** One web-map plugin. Implementations are created only when that plugin is enabled. */
public interface MapLayer {
    String name();

    /** Replaces every DyHomes marker on this map with {@code points}. */
    void update(List<MapPoint> points, String layerName);

    /** True when the map plugin restarted and dropped DyHomes' markers since the last update. */
    default boolean lost() {
        return false;
    }

    void close();
}
