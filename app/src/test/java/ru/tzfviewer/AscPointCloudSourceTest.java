package ru.tzfviewer;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class AscPointCloudSourceTest {
    @Test public void sourceTypeIsInferredByExtension(){assertEquals(ProjectModel.SourceType.ASC,PointCloudSources.infer("survey.ASC"));assertEquals(ProjectModel.SourceType.TZF,PointCloudSources.infer("scan.tzf"));}
    @Test public void parsesCommonDelimitersAndSkipsBadRows()throws Exception{
        File source=File.createTempFile("cloud",".asc"),cache=File.createTempFile("cloud",".bin");cache.delete();
        try(FileOutputStream out=new FileOutputStream(source)){out.write(("# cloud\n1 2 3\n4,5,6,255\n7;8;9;1;2;3\nbad row\n").getBytes(StandardCharsets.UTF_8));}
        try(AscPointCloudSource points=new AscPointCloudSource(source,cache)){assertEquals(3,points.sourcePointCount());assertEquals(1,points.skippedLineCount());assertArrayEquals(new float[]{1,2,3,7,8,9},points.localBounds(),0);points.prepare(2);float[] sample=points.nextChunk(10);assertEquals(6,sample.length);assertArrayEquals(new float[]{1,2,3},java.util.Arrays.copyOfRange(sample,0,3),0);}
        source.delete();cache.delete();
    }
    @Test public void rejectsFilesWithoutPoints()throws Exception{File source=File.createTempFile("empty",".asc"),cache=new File(source.getParentFile(),source.getName()+".bin");try(FileWriter w=new FileWriter(source)){w.write("header only\n");}try{new AscPointCloudSource(source,cache);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("XYZ"));}source.delete();cache.delete();}
    @Test public void flatPointCloudCacheReopens()throws Exception{File source=File.createTempFile("flat",".asc"),cache=new File(source.getParentFile(),source.getName()+".bin");try(FileWriter w=new FileWriter(source)){w.write("0 0 5\n1 2 5\n");}try(AscPointCloudSource first=new AscPointCloudSource(source,cache)){assertEquals(2,first.sourcePointCount());}try(AscPointCloudSource reopened=new AscPointCloudSource(source,cache)){assertEquals(2,reopened.sourcePointCount());}source.delete();cache.delete();}
}
