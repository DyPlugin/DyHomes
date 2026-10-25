package dev.dyhomes.teleport;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class SafetyCheckTest {
    private final World world = mock(World.class);
    private final Map<Integer, Block> column = new HashMap<>();

    @BeforeEach void setUp() {
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> column.computeIfAbsent(call.getArgument(1),
                y -> block(Material.AIR, y)));
    }

    private Block block(Material material, int y) {
        Block block = mock(Block.class);
        boolean solid = material != Material.AIR && material != Material.WATER && material != Material.LAVA
                && material != Material.FIRE;
        when(block.getType()).thenReturn(material);
        when(block.isPassable()).thenReturn(!solid);
        when(block.isLiquid()).thenReturn(material == Material.WATER || material == Material.LAVA);
        double top = material.name().endsWith("_SLAB") ? y + 0.5 : y + 1.0;
        when(block.getBoundingBox()).thenReturn(new BoundingBox(0, y, 0, 1, top, 1));
        return block;
    }

    private void put(int y, Material material) {
        column.put(y, block(material, y));
    }

    private SafetyCheck.Result at(double y) {
        return SafetyCheck.check(new Location(world, 0.5, y, 0.5), 3);
    }

    @Test void standingOnGroundIsSafe() {
        put(63, Material.STONE);
        assertEquals(SafetyCheck.Result.SAFE, at(64));
    }

    @Test void aWallOrLavaIsRefused() {
        put(63, Material.STONE);
        put(65, Material.STONE);
        assertEquals(SafetyCheck.Result.SUFFOCATION, at(64));
        column.clear();
        put(63, Material.STONE);
        put(64, Material.LAVA);
        assertEquals(SafetyCheck.Result.HAZARD, at(64));
        column.clear();
        put(63, Material.MAGMA_BLOCK);
        assertEquals(SafetyCheck.Result.HAZARD, at(64));
    }

    @Test void shortFallsAreFineLongOnesAreNot() {
        put(60, Material.STONE);
        assertEquals(SafetyCheck.Result.SAFE, at(64), "three blocks of air below is still safe");
        column.clear();
        put(55, Material.STONE);
        assertEquals(SafetyCheck.Result.FALL, at(64));
    }

    @Test void slabsAndWaterCount() {
        put(64, Material.OAK_SLAB);
        assertEquals(SafetyCheck.Result.SAFE, at(64.5), "a home set on a bottom slab");
        column.clear();
        put(64, Material.WATER);
        assertEquals(SafetyCheck.Result.SAFE, at(64), "swimming at the destination");
    }

    @Test void outsideTheWorldHeightIsVoid() {
        assertEquals(SafetyCheck.Result.VOID, at(-64));
        assertEquals(SafetyCheck.Result.VOID, at(319));
    }
}
