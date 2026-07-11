package ru.tzfviewer;

final class SceneBounds {
    private SceneBounds() {}

    static float[] of(float[] xyz) {
        if (xyz.length == 0 || xyz.length % 3 != 0) throw new IllegalArgumentException("invalid XYZ array");
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < xyz.length; i += 3) {
            for (int axis = 0; axis < 3; axis++) {
                bounds[axis] = Math.min(bounds[axis], xyz[i + axis]);
                bounds[axis + 3] = Math.max(bounds[axis + 3], xyz[i + axis]);
            }
        }
        return bounds;
    }

    static float[] merge(float[] target, float[] addition) {
        if (target == null) return addition.clone();
        for (int axis = 0; axis < 3; axis++) {
            target[axis] = Math.min(target[axis], addition[axis]);
            target[axis + 3] = Math.max(target[axis + 3], addition[axis + 3]);
        }
        return target;
    }

    static float[] transformed(float[] bounds, float[] pose) {
        float[] result = null;
        double angle = Math.toRadians(pose[3]);
        double cos = Math.cos(angle), sin = Math.sin(angle);
        for (int xi = 0; xi < 2; xi++) for (int yi = 0; yi < 2; yi++) for (int zi = 0; zi < 2; zi++) {
            float x = bounds[xi == 0 ? 0 : 3], y = bounds[yi == 0 ? 1 : 4], z = bounds[zi == 0 ? 2 : 5];
            float wx = (float) (x * cos - y * sin) + pose[0];
            float wy = (float) (x * sin + y * cos) + pose[1];
            float wz = z + pose[2];
            result = merge(result, new float[]{wx, wy, wz, wx, wy, wz});
        }
        return result;
    }
}
