package ru.tzfviewer;

final class TransformGizmo {
    static final int NONE = 0, X = 1, Y = 2, Z = 3, RZ = 4, XY = 5;
    private static final float TARGET_RADIUS_PX = 110f;
    private static final float CENTER_PICK_PX = 30f;
    private static final float AXIS_PICK_PX = 24f;
    private static final float RING_PICK_PX = 22f;
    private static final float MIN_AXIS_SCREEN_PX = 18f;
    private static final float[] AXIS_X = {1f, 0f, 0f};
    private static final float[] AXIS_Y = {0f, 1f, 0f};
    private static final float[] AXIS_Z = {0f, 0f, 1f};

    private final float[] startTransform = new float[4];
    private final float[] pivot = new float[3];
    private final float[] dragPlaneNormal = new float[3];
    private float[] initialHit;
    private int activeHandle;
    private float worldScale = 1f;

    int activeHandle() { return activeHandle; }
    float worldScale() { return worldScale; }

    void updateScale(float[] pivot, float[] mvp, float[] inverseMvp, int width, int height) {
        float[] screen = GizmoMath.project(pivot, mvp, width, height);
        if (screen == null) return;
        float nx = (screen[0] + TARGET_RADIUS_PX) * 2f / width - 1f;
        float ny = 1f - screen[1] * 2f / height;
        float[] offset = GizmoMath.unproject(nx, ny, screen[2], inverseMvp);
        if (offset == null) return;
        float candidate = (float) Math.sqrt(GizmoMath.dot(GizmoMath.subtract(offset, pivot), GizmoMath.subtract(offset, pivot)));
        if (GizmoMath.finite(candidate) && candidate > GizmoMath.EPSILON) worldScale = candidate;
    }

    boolean beginDrag(float screenX, float screenY, float[] transform, float[] currentPivot,
                      float[] mvp, float[] inverseMvp, int width, int height) {
        endDrag();
        int picked = pick(screenX, screenY, currentPivot, mvp, width, height);
        if (picked == NONE) return false;
        GizmoMath.Ray ray = GizmoMath.screenRay(screenX, screenY, width, height, inverseMvp);
        if (ray == null) return false;
        System.arraycopy(transform, 0, startTransform, 0, 4);
        System.arraycopy(currentPivot, 0, pivot, 0, 3);

        if (picked == XY || picked == RZ) {
            dragPlaneNormal[0] = 0f; dragPlaneNormal[1] = 0f; dragPlaneNormal[2] = 1f;
        } else {
            float[] axis = axis(picked);
            float projection = GizmoMath.dot(ray.direction, axis);
            dragPlaneNormal[0] = ray.direction[0] - axis[0] * projection;
            dragPlaneNormal[1] = ray.direction[1] - axis[1] * projection;
            dragPlaneNormal[2] = ray.direction[2] - axis[2] * projection;
            if (!GizmoMath.normalize(dragPlaneNormal)) return false;
        }
        initialHit = GizmoMath.intersectPlane(ray, pivot, dragPlaneNormal);
        if (initialHit == null) return false;
        if (picked == RZ && planarLength(GizmoMath.subtract(initialHit, pivot)) < GizmoMath.EPSILON) return false;
        activeHandle = picked;
        return true;
    }

    float[] updateDrag(float screenX, float screenY, float[] inverseMvp, int width, int height) {
        if (activeHandle == NONE || initialHit == null) return null;
        GizmoMath.Ray ray = GizmoMath.screenRay(screenX, screenY, width, height, inverseMvp);
        if (ray == null) return null;
        float[] hit = GizmoMath.intersectPlane(ray, pivot, dragPlaneNormal);
        if (hit == null) return null;
        float[] result = startTransform.clone();
        if (activeHandle == XY) {
            result[0] += hit[0] - initialHit[0];
            result[1] += hit[1] - initialHit[1];
        } else if (activeHandle == RZ) {
            float angle = GizmoMath.signedAngleZ(GizmoMath.subtract(initialHit, pivot), GizmoMath.subtract(hit, pivot));
            if (!GizmoMath.finite(angle)) return null;
            result[3] += angle;
        } else {
            float[] axis = axis(activeHandle);
            float delta = GizmoMath.dot(GizmoMath.subtract(hit, initialHit), axis);
            result[activeHandle - 1] += delta;
        }
        return GizmoMath.finite(result) ? result : null;
    }

    void endDrag() { activeHandle = NONE; initialHit = null; }

    private int pick(float x, float y, float[] pivot, float[] mvp, int width, int height) {
        float[] center = GizmoMath.project(pivot, mvp, width, height);
        if (center == null) return NONE;
        if (Math.hypot(x - center[0], y - center[1]) <= CENTER_PICK_PX) return XY;

        int bestAxis = NONE;
        float bestDistance = Float.MAX_VALUE;
        for (int handle = X; handle <= Z; handle++) {
            float[] endpoint = GizmoMath.add(pivot, GizmoMath.scale(axis(handle), worldScale));
            float[] projected = GizmoMath.project(endpoint, mvp, width, height);
            if (projected == null) continue;
            float screenLength = (float) Math.hypot(projected[0] - center[0], projected[1] - center[1]);
            if (screenLength < MIN_AXIS_SCREEN_PX) continue;
            float distance = GizmoMath.distanceToSegment(x, y, center[0], center[1], projected[0], projected[1]);
            if (distance <= AXIS_PICK_PX && distance < bestDistance) { bestDistance = distance; bestAxis = handle; }
        }
        if (bestAxis != NONE) return bestAxis;

        float ringRadius = worldScale * .75f;
        float bestRing = Float.MAX_VALUE;
        float[] previous = null;
        for (int i = 0; i <= 48; i++) {
            double angle = i * Math.PI * 2 / 48;
            float[] world = {pivot[0] + (float) Math.cos(angle) * ringRadius,
                    pivot[1] + (float) Math.sin(angle) * ringRadius, pivot[2]};
            float[] current = GizmoMath.project(world, mvp, width, height);
            if (previous != null && current != null) {
                bestRing = Math.min(bestRing, GizmoMath.distanceToSegment(x, y, previous[0], previous[1], current[0], current[1]));
            }
            previous = current;
        }
        if(bestRing <= RING_PICK_PX) return RZ;
        return Math.hypot(x-center[0],y-center[1]) <= TARGET_RADIUS_PX ? XY : NONE;
    }

    private static float[] axis(int handle) {
        if (handle == X) return AXIS_X;
        if (handle == Y) return AXIS_Y;
        return AXIS_Z;
    }

    private static float planarLength(float[] value) { return (float) Math.hypot(value[0], value[1]); }
}
