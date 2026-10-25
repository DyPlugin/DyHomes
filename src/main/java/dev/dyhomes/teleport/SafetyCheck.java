package dev.dyhomes.teleport;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Set;

/**
 * Checks a saved destination just before teleporting: room for the body, nothing burning at the feet and
 * ground within a short fall. Builds change after a home is set; this catches lava, walls and removed floors.
 * Must run on the thread that owns the destination.
 */
public final class SafetyCheck {
    public enum Result { SAFE, SUFFOCATION, HAZARD, FALL, VOID }

    private static final Set<String> HAZARDS = Set.of("LAVA", "FIRE", "SOUL_FIRE", "CAMPFIRE", "SOUL_CAMPFIRE",
            "MAGMA_BLOCK", "CACTUS", "SWEET_BERRY_BUSH", "WITHER_ROSE", "POWDER_SNOW", "POINTED_DRIPSTONE");

    private SafetyCheck() {
    }

    public static Result check(Location location, int maxFall) {
        World world = location.getWorld();
        if (world == null) return Result.VOID;
        int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
        int top = (int) Math.floor(location.getY() + 1.79D);
        if (y <= world.getMinHeight() || top >= world.getMaxHeight()) return Result.VOID;
        Block feet = world.getBlockAt(x, y, z);
        // A home set on a slab, carpet or path stands inside the lower block's cell, on top of its shape.
        boolean standsOnFeetBlock = !feet.isPassable() && feet.getBoundingBox().getMaxY() <= location.getY() + 1.0E-3D;
        for (int bodyY = y; bodyY <= top; bodyY++) {
            Block body = world.getBlockAt(x, bodyY, z);
            if (hazard(body.getType())) return Result.HAZARD;
            if (!body.isPassable() && !(bodyY == y && standsOnFeetBlock)) return Result.SUFFOCATION;
        }
        if (standsOnFeetBlock || feet.isLiquid()) return Result.SAFE; // Water at the feet: the player swims, no fall.
        for (int depth = 1; depth <= Math.max(0, maxFall) + 1; depth++) {
            int groundY = y - depth;
            if (groundY < world.getMinHeight()) return Result.VOID;
            Block ground = world.getBlockAt(x, groundY, z);
            if (hazard(ground.getType())) return Result.HAZARD;
            if (!ground.isPassable() || ground.isLiquid()) return Result.SAFE;
        }
        return Result.FALL;
    }

    static boolean hazard(Material material) {
        return HAZARDS.contains(material.name());
    }
}
