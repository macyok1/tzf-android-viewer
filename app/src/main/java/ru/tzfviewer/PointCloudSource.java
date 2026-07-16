package ru.tzfviewer;

import java.io.IOException;

interface PointCloudSource extends AutoCloseable {
    long sourcePointCount() throws IOException;
    float[] localBounds() throws IOException;
    float[] initialPose() throws IOException;
    void prepare(int requestedPoints) throws IOException;
    float[] nextChunk(int maxPoints) throws IOException;
    @Override void close();
}
