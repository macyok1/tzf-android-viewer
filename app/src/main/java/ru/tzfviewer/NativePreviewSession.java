package ru.tzfviewer;
import java.io.IOException;
final class NativePreviewSession implements AutoCloseable{
 private long handle;NativePreviewSession(String path)throws IOException{handle=TzfNative.openPreviewSession(path);if(handle==0)throw new IOException("native preview session was not created");}
 synchronized long sourcePointCount()throws IOException{ensureOpen();return TzfNative.previewSessionSourcePointCount(handle);}
 synchronized void prepare(int points)throws IOException{ensureOpen();TzfNative.preparePreviewSession(handle,points);}
 synchronized float[] nextChunk(int points)throws IOException{ensureOpen();return TzfNative.nextPreviewChunk(handle,points);}
 synchronized boolean isOpen(){return handle!=0;}private void ensureOpen()throws IOException{if(handle==0)throw new IOException("preview session is closed");}
 @Override public synchronized void close(){if(handle!=0){TzfNative.closePreviewSession(handle);handle=0;}}
}
