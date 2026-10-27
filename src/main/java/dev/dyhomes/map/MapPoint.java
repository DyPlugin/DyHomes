package dev.dyhomes.map;

/** One marker on a web map: a warp or a public home. {@code id} is stable across refreshes. */
public record MapPoint(String id, String label, String world, double x, double y, double z) {
}
