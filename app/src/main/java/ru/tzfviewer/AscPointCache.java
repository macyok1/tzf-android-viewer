package ru.tzfviewer;

import java.io.*;
import java.nio.charset.StandardCharsets;

final class AscPointCache {
    static final int MAGIC=0x41534342,VERSION=2,HEADER_BYTES=56;
    final File file;final long count;final float[] bounds;final long skipped;
    private AscPointCache(File file,long count,float[] bounds,long skipped){this.file=file;this.count=count;this.bounds=bounds;this.skipped=skipped;}

    interface Observer {boolean cancelled();void onProgress(long sourceBytes,long validPoints);}

    static AscPointCache open(File cache,long expectedSourceBytes)throws IOException{return read(cache,expectedSourceBytes);}

    static AscPointCache ensure(InputStream source,long expectedSourceBytes,File cache,Observer observer)throws IOException{
        if(cache.isFile())try{return read(cache,expectedSourceBytes);}catch(IOException ignored){}
        return build(source,expectedSourceBytes,cache,observer);
    }

    private static AscPointCache read(File cache,long expectedSourceBytes)throws IOException{
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(cache)))){
            if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("corrupt ASC cache");
            long count=in.readLong(),skipped=in.readLong(),sourceBytes=in.readLong();float[] bounds=new float[6];for(int i=0;i<6;i++)bounds[i]=in.readFloat();
            if(expectedSourceBytes>=0&&sourceBytes!=expectedSourceBytes)throw new IOException("ASC source changed");
            if(count<1||count>(Long.MAX_VALUE-HEADER_BYTES)/12L||!validBounds(bounds)||cache.length()!=HEADER_BYTES+count*12L)throw new IOException("corrupt ASC cache");
            return new AscPointCache(cache,count,bounds,skipped);
        }
    }

    private static AscPointCache build(InputStream source,long expectedSourceBytes,File cache,Observer observer)throws IOException{
        File parent=cache.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("cannot create ASC cache directory");
        File temp=new File(cache.getAbsolutePath()+".tmp");long count=0,skipped=0,lastProgress=0;float[] bounds=null;
        try(CountingInputStream counted=new CountingInputStream(source);BufferedReader reader=new BufferedReader(new InputStreamReader(counted,StandardCharsets.UTF_8),1024*1024);DataOutputStream points=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp),1024*1024))){
            for(int i=0;i<HEADER_BYTES;i++)points.writeByte(0);
            String line;float[] xyz=new float[3];
            while((line=reader.readLine())!=null){
                if(observer!=null&&observer.cancelled())throw new InterruptedIOException("ASC import cancelled");
                if(!AscLineParser.xyz(line,xyz)){String clean=line.trim();if(!clean.isEmpty()&&!clean.startsWith("#")&&!clean.startsWith("//")&&!clean.startsWith(";"))skipped++;continue;}
                points.writeFloat(xyz[0]);points.writeFloat(xyz[1]);points.writeFloat(xyz[2]);bounds=SceneBounds.merge(bounds,new float[]{xyz[0],xyz[1],xyz[2],xyz[0],xyz[1],xyz[2]});count++;
                if(observer!=null&&counted.count-lastProgress>=1024L*1024L){lastProgress=counted.count;observer.onProgress(counted.count,count);}
            }
            points.flush();
            if(observer!=null)observer.onProgress(counted.count,count);
            if(expectedSourceBytes<0)expectedSourceBytes=counted.count;
        }catch(IOException error){temp.delete();throw error;}
        if(count==0||bounds==null){temp.delete();throw new IOException("ASC contains no valid XYZ points");}
        try(RandomAccessFile header=new RandomAccessFile(temp,"rw")){
            header.seek(0);header.writeInt(MAGIC);header.writeInt(VERSION);header.writeLong(count);header.writeLong(skipped);header.writeLong(expectedSourceBytes);for(float value:bounds)header.writeFloat(value);header.getFD().sync();
        }
        if(cache.exists()&&!cache.delete()){temp.delete();throw new IOException("cannot replace ASC cache");}
        if(!temp.renameTo(cache)){temp.delete();throw new IOException("cannot save ASC cache");}
        return new AscPointCache(cache,count,bounds,skipped);
    }

    private static boolean validBounds(float[] bounds){if(bounds==null||bounds.length!=6)return false;for(float value:bounds)if(!Float.isFinite(value))return false;return bounds[0]<=bounds[3]&&bounds[1]<=bounds[4]&&bounds[2]<=bounds[5];}

    private static final class CountingInputStream extends FilterInputStream {
        long count;CountingInputStream(InputStream input){super(input);}
        @Override public int read()throws IOException{int value=super.read();if(value>=0)count++;return value;}
        @Override public int read(byte[] buffer,int offset,int length)throws IOException{int read=super.read(buffer,offset,length);if(read>0)count+=read;return read;}
    }
}