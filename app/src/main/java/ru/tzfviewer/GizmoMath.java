package ru.tzfviewer;

final class GizmoMath {
    static final float EPSILON = 1e-5f;

    private GizmoMath() {}

    static final class Ray {
        final float[] origin;
        final float[] direction;

        Ray(float[] origin, float[] direction) {
            this.origin = origin;
            this.direction = direction;
        }
    }

    static Ray screenRay(float x, float y, int width, int height, float[] inverseMvp) {
        if (width <= 0 || height <= 0 || inverseMvp == null || inverseMvp.length < 16) return null;
        float nx = x * 2f / width - 1f;
        float ny = 1f - y * 2f / height;
        float[] near = unproject(nx, ny, -1f, inverseMvp);
        float[] far = unproject(nx, ny, 1f, inverseMvp);
        if (near == null || far == null) return null;
        float[] direction = subtract(far, near);
        if (!normalize(direction)) return null;
        return new Ray(near, direction);
    }

    static float[] unproject(float x, float y, float z, float[] inverseMvp) {
        float[] out = multiply(inverseMvp, new float[]{x, y, z, 1f});
        if (!finite(out[3]) || Math.abs(out[3]) < EPSILON) return null;
        float inverseW = 1f / out[3];
        float[] point = {out[0] * inverseW, out[1] * inverseW, out[2] * inverseW};
        return finite(point) ? point : null;
    }

    static float[] project(float[] point, float[] mvp, int width, int height) {
        float[] out = multiply(mvp, new float[]{point[0], point[1], point[2], 1f});
        if (!finite(out[3]) || Math.abs(out[3]) < EPSILON) return null;
        float inverseW = 1f / out[3];
        float nx = out[0] * inverseW;
        float ny = out[1] * inverseW;
        float nz = out[2] * inverseW;
        float[] result = {(nx * .5f + .5f) * width, (1f - (ny * .5f + .5f)) * height, nz};
        return finite(result) ? result : null;
    }

    static float[] intersectPlane(Ray ray, float[] planePoint, float[] planeNormal) {
        float denominator = dot(ray.direction, planeNormal);
        if (!finite(denominator) || Math.abs(denominator) < EPSILON) return null;
        float distance = dot(subtract(planePoint, ray.origin), planeNormal) / denominator;
        if (!finite(distance)) return null;
        return add(ray.origin, scale(ray.direction, distance));
    }

    static float signedAngleZ(float[] from, float[] to) {
        float fromLength = (float) Math.hypot(from[0], from[1]);
        float toLength = (float) Math.hypot(to[0], to[1]);
        if (fromLength < EPSILON || toLength < EPSILON) return Float.NaN;
        float cross = from[0] * to[1] - from[1] * to[0];
        float dot = from[0] * to[0] + from[1] * to[1];
        return (float) Math.toDegrees(Math.atan2(cross, dot));
    }

    static float distanceToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < EPSILON) return (float) Math.hypot(px - ax, py - ay);
        float t = Math.max(0f, Math.min(1f, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
        return (float) Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    static float[] multiply(float[] matrix, float[] vector) {
        float[] out = new float[4];
        for (int row = 0; row < 4; row++) {
            out[row] = matrix[row] * vector[0] + matrix[4 + row] * vector[1]
                    + matrix[8 + row] * vector[2] + matrix[12 + row] * vector[3];
        }
        return out;
    }

    static float dot(float[] a, float[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static float[] add(float[] a, float[] b) { return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static float[] subtract(float[] a, float[] b) { return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static float[] scale(float[] a, float value) { return new float[]{a[0] * value, a[1] * value, a[2] * value}; }

    static boolean normalize(float[] value) {
        float length = (float) Math.sqrt(dot(value, value));
        if (!finite(length) || length < EPSILON) return false;
        value[0] /= length; value[1] /= length; value[2] /= length;
        return true;
    }

    static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
    static boolean finite(float[] values) {
        for (float value : values) if (!finite(value)) return false;
        return true;
    }
}
