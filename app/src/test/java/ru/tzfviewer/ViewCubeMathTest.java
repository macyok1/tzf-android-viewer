package ru.tzfviewer;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class ViewCubeMathTest {
    private static final float T = 1e-4f;

    @Test public void allTwentySixDirectionsProduceFiniteUniqueAngles() {
        Set<String> angles = new HashSet<>(); int count=0;
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
            if(x==0&&y==0&&z==0)continue;
            float[] a=ViewCubeMath.directionToAngles(x,y,z);
            assertNotNull(a); assertTrue(Float.isFinite(a[0])); assertTrue(Float.isFinite(a[1]));
            angles.add(Math.round(a[0]*100)+":"+Math.round(a[1]*100)); count++;
        }
        assertEquals(26,count); assertEquals(26,angles.size());
    }

    @Test public void principalDirectionsMatchCameraConvention() {
        assertArrayEquals(new float[]{0,0},ViewCubeMath.directionToAngles(0,-1,0),T);
        assertArrayEquals(new float[]{90,0},ViewCubeMath.directionToAngles(1,0,0),T);
        assertArrayEquals(new float[]{-90,0},ViewCubeMath.directionToAngles(-1,0,0),T);
        assertArrayEquals(new float[]{180,0},ViewCubeMath.directionToAngles(0,1,0),T);
        assertArrayEquals(new float[]{0,-89},ViewCubeMath.directionToAngles(0,0,1),T);
        assertArrayEquals(new float[]{0,89},ViewCubeMath.directionToAngles(0,0,-1),T);
    }

    @Test public void yawUsesShortestPathAcrossBoundary() {
        assertEquals(20,ViewCubeMath.shortestYawDelta(170,-170),T);
        assertEquals(-20,ViewCubeMath.shortestYawDelta(-170,170),T);
        assertEquals(180,ViewCubeMath.normalizeYaw(540),T);
    }

    @Test public void eyeDirectionRoundTripsAngles() {
        float[] eye=ViewCubeMath.eyeDirection(45,-35.26439f);
        float n=(float)Math.sqrt(3);
        assertArrayEquals(new float[]{1/n,-1/n,1/n},eye,1e-3f);
    }

    @Test public void invalidDirectionIsRejectedAndPitchIsClamped() {
        assertNull(ViewCubeMath.directionToAngles(0,0,0));
        assertNull(ViewCubeMath.directionToAngles(2,0,0));
        assertEquals(-89,ViewCubeMath.clampPitch(-200),T);
        assertEquals(89,ViewCubeMath.clampPitch(200),T);
    }
}
