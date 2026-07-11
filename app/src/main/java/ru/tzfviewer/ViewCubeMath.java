package ru.tzfviewer;

final class ViewCubeMath {
    private ViewCubeMath() {}

    static float clampPitch(float pitch) {
        if (!Float.isFinite(pitch)) return 0f;
        return Math.max(-89f, Math.min(89f, pitch));
    }

    static float normalizeYaw(float yaw) {
        if (!Float.isFinite(yaw)) return 0f;
        float result = yaw % 360f;
        if (result <= -180f) result += 360f;
        if (result > 180f) result -= 360f;
        return result;
    }

    static float shortestYawDelta(float from, float to) {
        return normalizeYaw(to - from);
    }

    static float[] directionToAngles(int x, int y, int z) {
        if ((x == 0 && y == 0 && z == 0) || Math.abs(x) > 1 || Math.abs(y) > 1 || Math.abs(z) > 1)
            return null;
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        float yaw = (float) Math.toDegrees(Math.atan2(x, -y));
        float pitch = (float) -Math.toDegrees(Math.asin(z / length));
        return new float[]{normalizeYaw(yaw), clampPitch(pitch)};
    }

    static float[] eyeDirection(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return new float[]{0f, 0f, 1f};
        double yr = Math.toRadians(yaw), pr = Math.toRadians(clampPitch(pitch));
        return new float[]{
                (float) (Math.cos(pr) * Math.sin(yr)),
                (float) (-Math.cos(pr) * Math.cos(yr)),
                (float) -Math.sin(pr)
        };
    }
}
