package ru.tzfviewer;

final class TzfNative {
    static {
        System.loadLibrary("tzfreader");
    }

    private TzfNative() {}

    static native float[] decodePreview(String localPath, int maxPoints, int tileStride)
            throws java.io.IOException;
    static native long openPreviewSession(String localPath) throws java.io.IOException;
    static native long previewSessionSourcePointCount(long handle) throws java.io.IOException;
    static native float[] previewSessionInitialPose(long handle) throws java.io.IOException;
    static native void preparePreviewSession(long handle,int maxPoints) throws java.io.IOException;
    static native float[] nextPreviewChunk(long handle,int maxPoints) throws java.io.IOException;
    static native void closePreviewSession(long handle);

    static native RegistrationResult registerScans(String referencePath, String movingPath,
            float[] initialTransform, double rmsLimit, double p95Limit)
            throws java.io.IOException;
    static native RegistrationResult registerPointClouds(float[] referenceXyz, float[] movingXyz,
            float[] initialTransform, double rmsLimit, double p95Limit)
            throws java.io.IOException;
    static native RegistrationResult registerPointCloudsGlobal(float[] referenceXyz, float[] movingXyz,
            double rmsLimit, double p95Limit) throws java.io.IOException;
    /** Edges are flattened as reference, moving, dx, dy, dz, yaw, weight. */
    static native float[] optimizePoseGraph(float[] initialPoses, float[] edges, int fixedStation)
            throws java.io.IOException;
    static native void cancelRegistration();
}
