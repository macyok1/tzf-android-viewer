package ru.tzfviewer;

import org.junit.Test;

import static org.junit.Assert.*;

public class GizmoMathTest {
    private static final float TOLERANCE = 1e-4f;

    @Test public void identityMatrixBuildsCenterRay() {
        GizmoMath.Ray ray = GizmoMath.screenRay(50f, 50f, 100, 100, identity());
        assertNotNull(ray);
        assertArrayEquals(new float[]{0f, 0f, -1f}, ray.origin, TOLERANCE);
        assertArrayEquals(new float[]{0f, 0f, 1f}, ray.direction, TOLERANCE);
    }

    @Test public void orthographicRaysHaveDifferentOriginsAndParallelDirections() {
        GizmoMath.Ray left = GizmoMath.screenRay(0f, 50f, 100, 100, identity());
        GizmoMath.Ray right = GizmoMath.screenRay(100f, 50f, 100, 100, identity());
        assertEquals(-1f, left.origin[0], TOLERANCE);
        assertEquals(1f, right.origin[0], TOLERANCE);
        assertArrayEquals(left.direction, right.direction, TOLERANCE);
    }

    @Test public void intersectsHorizontalPlane() {
        GizmoMath.Ray ray = new GizmoMath.Ray(new float[]{2f, 3f, 5f}, new float[]{0f, 0f, -1f});
        float[] hit = GizmoMath.intersectPlane(ray, new float[]{0f, 0f, 1f}, new float[]{0f, 0f, 1f});
        assertArrayEquals(new float[]{2f, 3f, 1f}, hit, TOLERANCE);
    }

    @Test public void parallelRayDoesNotIntersectPlane() {
        GizmoMath.Ray ray = new GizmoMath.Ray(new float[]{0f, 0f, 1f}, new float[]{1f, 0f, 0f});
        assertNull(GizmoMath.intersectPlane(ray, new float[]{0f, 0f, 0f}, new float[]{0f, 0f, 1f}));
    }

    @Test public void signedAngleUsesWorldZ() {
        assertEquals(90f, GizmoMath.signedAngleZ(new float[]{1f, 0f, 0f}, new float[]{0f, 1f, 0f}), TOLERANCE);
        assertEquals(-90f, GizmoMath.signedAngleZ(new float[]{0f, 1f, 0f}, new float[]{1f, 0f, 0f}), TOLERANCE);
        assertTrue(Float.isNaN(GizmoMath.signedAngleZ(new float[3], new float[]{1f, 0f, 0f})));
    }

    @Test public void distanceToSegmentClampsToEndpoints() {
        assertEquals(2f, GizmoMath.distanceToSegment(5f, 2f, 0f, 0f, 10f, 0f), TOLERANCE);
        assertEquals(5f, GizmoMath.distanceToSegment(15f, 0f, 0f, 0f, 10f, 0f), TOLERANCE);
    }

    @Test public void invalidHomogeneousCoordinateDoesNotUnproject() {
        float[] zero = new float[16];
        assertNull(GizmoMath.unproject(0f, 0f, 0f, zero));
    }

    private static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }
}
