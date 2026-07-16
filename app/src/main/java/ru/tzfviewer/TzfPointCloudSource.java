package ru.tzfviewer;

import java.io.File;
import java.io.IOException;

final class TzfPointCloudSource implements PointCloudSource {
    private final NativePreviewSession session;
    TzfPointCloudSource(File file) throws IOException { session=new NativePreviewSession(file.getAbsolutePath()); }
    @Override public long sourcePointCount() throws IOException { return session.sourcePointCount(); }
    @Override public float[] localBounds() { return null; }
    @Override public float[] initialPose() throws IOException { return session.initialPose(); }
    @Override public void prepare(int requestedPoints) throws IOException { session.prepare(requestedPoints); }
    @Override public float[] nextChunk(int maxPoints) throws IOException { return session.nextChunk(maxPoints); }
    @Override public void close(){ session.close(); }
}
