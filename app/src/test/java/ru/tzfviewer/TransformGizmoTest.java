package ru.tzfviewer;

import org.junit.Test;

import static org.junit.Assert.*;

public class TransformGizmoTest {
    private static final float TOLERANCE = 1e-3f;
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    @Test public void xDragOnlyChangesX() {
        TransformGizmo gizmo = new TransformGizmo();
        float[] start = {2f, 3f, 4f, 15f};
        assertTrue(gizmo.beginDrag(90f, 50f, start, new float[3], IDENTITY, IDENTITY, 100, 100));
        assertEquals(TransformGizmo.X, gizmo.activeHandle());
        float[] result = gizmo.updateDrag(100f, 50f, IDENTITY, 100, 100);
        assertArrayEquals(new float[]{2.2f, 3f, 4f, 15f}, result, TOLERANCE);
    }

    @Test public void centerDragOnlyChangesXY() {
        TransformGizmo gizmo = new TransformGizmo();
        float[] start = {2f, 3f, 4f, 15f};
        assertTrue(gizmo.beginDrag(50f, 50f, start, new float[3], IDENTITY, IDENTITY, 100, 100));
        assertEquals(TransformGizmo.XY, gizmo.activeHandle());
        float[] result = gizmo.updateDrag(60f, 40f, IDENTITY, 100, 100);
        assertArrayEquals(new float[]{2.2f, 3.2f, 4f, 15f}, result, TOLERANCE);
    }

    @Test public void ringDragOnlyChangesRz() {
        TransformGizmo gizmo = new TransformGizmo();
        float[] start = {2f, 3f, 4f, 15f};
        assertTrue(gizmo.beginDrag(76.5f, 23.5f, start, new float[3], IDENTITY, IDENTITY, 100, 100));
        assertEquals(TransformGizmo.RZ, gizmo.activeHandle());
        float[] result = gizmo.updateDrag(23.5f, 23.5f, IDENTITY, 100, 100);
        assertArrayEquals(new float[]{2f, 3f, 4f, 105f}, result, .1f);
    }

    @Test public void collapsedZAxisDoesNotOverrideCenterPriority() {
        TransformGizmo gizmo = new TransformGizmo();
        assertTrue(gizmo.beginDrag(50f, 50f, new float[4], new float[3], IDENTITY, IDENTITY, 100, 100));
        assertEquals(TransformGizmo.XY, gizmo.activeHandle());
    }

    @Test public void cancelClearsActiveHandle() {
        TransformGizmo gizmo = new TransformGizmo();
        assertTrue(gizmo.beginDrag(50f, 50f, new float[4], new float[3], IDENTITY, IDENTITY, 100, 100));
        gizmo.endDrag();
        assertEquals(TransformGizmo.NONE, gizmo.activeHandle());
        assertNull(gizmo.updateDrag(60f, 60f, IDENTITY, 100, 100));
    }
}
