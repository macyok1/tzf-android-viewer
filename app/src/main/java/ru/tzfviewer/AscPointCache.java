package ru.tzfviewer;

import java.io.*;
import java.nio.charset.StandardCharsets;

final class AscPointCache {
    static final int MAGIC=0x41534342,VERSION=2,HEADER_BYTES=56;
    final File file;final long count;final float[] bounds;final long skipped;
    private AscPointCache(File file,long count,float[] bounds,long skipped){this.file=file;this.count=count;this.bounds=bounds;this.skipped=skipped;}

    static AscPointCache ensure(File source,File cache) throws IOException {
        if(cache.isFile())try{return read(source,cache);}catch(IOException ignored){}
        return build(source,cache);
    }
    private static AscPointCache read(File source,File cache)throws IOException{
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(cache)))){
            if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("повреждён кэш ASC");
            long count=in.readLong(),skipped=in.readLong(),sourceBytes=in.readLong();float[] bounds=new float[6];for(int i=0;i<6;i++)bounds[i]=in.readFloat();
            if(sourceBytes!=source.length())throw new IOException("ASC изменён");
            if(count<1||!validBounds(bounds)||cache.length()!=HEADER_BYTES+count*12L)throw new IOException("повреждён кэш ASC");
            return new AscPointCache(cache,count,bounds,skipped);
        }
    }
    private static AscPointCache build(File source,File cache)throws IOException{
        File parent=cache.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("не удалось создать кэш ASC");
        File body=new File(cache.getAbsolutePath()+".body"),temp=new File(cache.getAbsolutePath()+".tmp");long count=0,skipped=0;float[] bounds=null;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(source),StandardCharsets.UTF_8),1024*1024);DataOutputStream points=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(body),1024*1024))){
            String line;while((line=reader.readLine())!=null){float[] xyz=AscLineParser.xyz(line);if(xyz==null){String clean=line.trim();if(!clean.isEmpty()&&!clean.startsWith("#")&&!clean.startsWith("//")&&!clean.startsWith(";"))skipped++;continue;}for(float v:xyz)points.writeFloat(v);bounds=SceneBounds.merge(bounds,new float[]{xyz[0],xyz[1],xyz[2],xyz[0],xyz[1],xyz[2]});count++;}
        }catch(IOException error){body.delete();throw error;}
        if(count==0||bounds==null){body.delete();throw new IOException("ASC не содержит корректных XYZ точек");}
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp),1024*1024));InputStream in=new BufferedInputStream(new FileInputStream(body),1024*1024)){
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeLong(count);out.writeLong(skipped);out.writeLong(source.length());for(float v:bounds)out.writeFloat(v);byte[] buffer=new byte[1024*1024];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        }finally{body.delete();}
        if(cache.exists()&&!cache.delete()){temp.delete();throw new IOException("не удалось заменить кэш ASC");}
        if(!temp.renameTo(cache)){temp.delete();throw new IOException("не удалось сохранить кэш ASC");}
        cache.setLastModified(Math.max(cache.lastModified(),source.lastModified()));return new AscPointCache(cache,count,bounds,skipped);
    }
    private static boolean validBounds(float[] bounds){if(bounds==null||bounds.length!=6)return false;for(float value:bounds)if(!Float.isFinite(value))return false;return bounds[0]<=bounds[3]&&bounds[1]<=bounds[4]&&bounds[2]<=bounds[5];}
}
