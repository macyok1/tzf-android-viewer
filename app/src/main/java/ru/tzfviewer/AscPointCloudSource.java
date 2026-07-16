package ru.tzfviewer;

import java.io.*;

final class AscPointCloudSource implements PointCloudSource {
    private final AscPointCache cache;private RandomAccessFile input;private long target,outputIndex;
    AscPointCloudSource(File source,File cacheFile)throws IOException{cache=AscPointCache.ensure(source,cacheFile);}
    long skippedLineCount(){return cache.skipped;}
    @Override public long sourcePointCount(){return cache.count;}
    @Override public float[] localBounds(){return cache.bounds.clone();}
    @Override public float[] initialPose(){return null;}
    @Override public void prepare(int requested)throws IOException{closeInput();input=new RandomAccessFile(cache.file,"r");target=Math.min(cache.count,Math.max(1L,requested));outputIndex=0;}
    @Override public float[] nextChunk(int maxPoints)throws IOException{
        if(input==null)throw new IOException("ASC не подготовлен");int points=(int)Math.min(Math.max(1,maxPoints),target-outputIndex);if(points<=0)return new float[0];float[] xyz=new float[points*3];
        for(int i=0;i<points;i++,outputIndex++){long sourceIndex=Math.min(cache.count-1,(long)Math.floor(outputIndex*(double)cache.count/target));input.seek(AscPointCache.HEADER_BYTES+sourceIndex*12L);xyz[i*3]=input.readFloat();xyz[i*3+1]=input.readFloat();xyz[i*3+2]=input.readFloat();}
        return xyz;
    }
    private void closeInput(){if(input!=null)try{input.close();}catch(IOException ignored){}input=null;}
    @Override public void close(){closeInput();}
}
