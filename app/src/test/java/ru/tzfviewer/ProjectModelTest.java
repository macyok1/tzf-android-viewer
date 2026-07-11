package ru.tzfviewer;

import org.junit.Test;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class ProjectModelTest {
    private static final float T=1e-4f;
    @Test public void nestedGroupsComposeAndDissolveWithoutMovingScans(){
        ProjectModel p=new ProjectModel("p","Проект",1);ProjectModel.Scan one=new ProjectModel.Scan("1","One"),two=new ProjectModel.Scan("2","Two");ProjectModel.Group twelve=new ProjectModel.Group("12","12");p.root.add(twelve);twelve.add(one);twelve.add(two);twelve.transform[0]=10;twelve.transform[3]=90;one.transform[0]=2;float[] before=one.worldTransform();twelve.dissolveIntoParent();assertArrayEquals(before,one.worldTransform(),T);assertSame(p.root,one.parent());
    }
    @Test public void groupCannotCreateCycleOrHaveTwoParents(){
        ProjectModel.Group a=new ProjectModel.Group("a","a"),b=new ProjectModel.Group("b","b");a.add(b);try{b.add(a);fail();}catch(IllegalArgumentException expected){}ProjectModel.Group c=new ProjectModel.Group("c","c");try{c.add(b);fail();}catch(IllegalStateException expected){}
    }
    @Test public void codecRoundTripsHierarchyAndRussianText(){
        ProjectModel p=new ProjectModel("abc","Съёмка",10);ProjectModel.Group g=new ProjectModel.Group("g","Связка 12");ProjectModel.Scan s=new ProjectModel.Scan("s","Скан №1");s.uri="content://точки/1";s.transform[3]=42;p.root.add(g);g.add(s);String encoded=ProjectCodec.encode(p);ProjectModel restored=ProjectCodec.decode(encoded);assertEquals("Съёмка",restored.name);assertEquals(1,restored.scanCount());ProjectModel.Group rg=(ProjectModel.Group)restored.root.children().get(0);ProjectModel.Scan rs=(ProjectModel.Scan)rg.children().get(0);assertEquals(s.uri,rs.uri);assertEquals(42,rs.transform[3],T);
    }
    @Test public void storeSavesLoadsCopiesAndDeletesAtomically()throws Exception{
        java.io.File dir=Files.createTempDirectory("tzf-project-test").toFile();ProjectStore store=new ProjectStore(dir);ProjectModel p=new ProjectModel("abc-1","Original",1);p.root.add(new ProjectModel.Scan("s","Scan"));store.save(p);assertEquals(1,store.list().size());assertEquals(1,store.load(p.id).scanCount());ProjectModel copy=store.copy(p,"Copy",2);assertEquals(2,store.list().size());assertEquals("Copy",copy.name);store.delete(p.id);assertEquals(1,store.list().size());
    }
}
