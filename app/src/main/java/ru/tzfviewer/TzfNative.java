package ru.tzfviewer;

final class TzfNative {
    static {
        System.loadLibrary("tzfreader");
    }

    private TzfNative() {}

    static native float[] decodePreview(String localPath, int maxPoints, int tileStride)
            throws java.io.IOException;

    static native RegistrationResult registerScans(String referencePath, String movingPath,
            float[] initialTransform, double rmsLimit, double p95Limit)
            throws java.io.IOException;
}
