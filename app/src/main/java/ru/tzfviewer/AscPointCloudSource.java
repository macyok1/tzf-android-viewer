package ru.tzfviewer;

import java.io.*;

final class AscPointCloudSource implements PointCloudSource {
    private final AscPointCache cache;private RandomAccessFile input;private long target,outputIndex,inputIndex;
    AscPointCloudSource(File cacheFile,long sourceBytes)throws IOException{cache=AscPointCache.open(cacheFile,sourceBytes);}
    AscPointCloudSource(InputStream source,long sourceBytes,File cacheFile,AscPointCache.Observer observer)throws IOException{cache=AscPointCache.ensure(source,sourceBytes,cacheFile,observer);}
    long skippedLineCount(){return cache.skipped;}
    @Override public long sourcePointCount(){return cache.count;}
    @Override public float[] localBounds(){return cache.bounds.clone();}
    @Override public float[] initialPose(){return null;}
    @Override public void prepare(int requested)throws IOException{closeInput();input=new RandomAccessFile(cache.file,"r");target=Math.min(cache.count,Math.max(1L,requested));outputIndex=0;inputIndex=-1;}
    @Override public float[] nextChunk(int maxPoints)throws IOException{
        if(input==null)throw new IOException("ASC is not prepared");int points=(int)Math.min(Math.max(1,maxPoints),target-outputIndex);if(points<=0)return new float[0];float[] xyz=new float[points*3];
        for(int i=0;i<points;i++,outputIndex++){long sourceIndex=Math.min(cache.count-1,(long)Math.floor(outputIndex*(double)cache.count/target));if(inputIndex!=sourceIndex)input.seek(AscPointCache.HEADER_BYTES+sourceIndex*12L);xyz[i*3]=input.readFloat();xyz[i*3+1]=input.readFloat();xyz[i*3+2]=input.readFloat();inputIndex=sourceIndex+1;}
        return xyz;
    }
    private void closeInput(){if(input!=null)try{input.close();}catch(IOException ignored){}input=null;}
    @Override public void close(){closeInput();}
}