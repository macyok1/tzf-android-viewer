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
        ProjectModel p=new ProjectModel("abc","Съёмка",10);ProjectModel.Group g=new ProjectModel.Group("g","Связка 12");ProjectModel.Scan s=new ProjectModel.Scan("s","Скан №1"),other=new ProjectModel.Scan("o","Другой");s.uri="content://точки/1";s.transform[3]=42;g.expanded=false;p.root.add(g);g.add(s);p.root.add(other);p.setReference(g);p.setMoving(other);p.clipEnabled=true;p.clipLocked=true;System.arraycopy(new float[]{1,2,3,4,5,6},0,p.clipBounds,0,6);String encoded=ProjectCodec.encode(p);ProjectModel restored=ProjectCodec.decode(encoded);assertEquals("Съёмка",restored.name);assertEquals(2,restored.scanCount());ProjectModel.Group rg=(ProjectModel.Group)restored.findNode("g");ProjectModel.Scan rs=(ProjectModel.Scan)restored.findNode("s");assertEquals(s.uri,rs.uri);assertEquals(42,rs.transform[3],T);assertFalse(rg.expanded);assertEquals("g",restored.referenceNodeId);assertEquals("o",restored.movingNodeId);assertTrue(restored.clipEnabled);assertTrue(restored.clipLocked);assertArrayEquals(p.clipBounds,restored.clipBounds,T);
    }
    @Test public void rolesAreUniqueAndRejectAncestorPair(){ProjectModel p=new ProjectModel("p","p",1);ProjectModel.Group g=new ProjectModel.Group("g","g");ProjectModel.Scan a=new ProjectModel.Scan("a","a"),b=new ProjectModel.Scan("b","b");p.root.add(g);g.add(a);p.root.add(b);p.setReference(a);p.setMoving(b);assertTrue(p.canRegister());p.setMoving(a);assertEquals("",p.referenceNodeId);assertEquals("a",p.movingNodeId);p.setReference(g);assertEquals("",p.movingNodeId);p.setMoving(a);assertEquals("",p.referenceNodeId);}

    @Test public void movingGroupChangesOnlyGroupWorldPose(){
        ProjectModel p=new ProjectModel("p","p",1);
        ProjectModel.Group moving=new ProjectModel.Group("m","moving");
        ProjectModel.Scan first=new ProjectModel.Scan("a","a"),second=new ProjectModel.Scan("b","b");
        first.transform[0]=2f; first.transform[1]=1f; first.transform[3]=15f;
        second.transform[0]=-3f; second.transform[1]=4f; second.transform[2]=.5f;
        p.root.add(moving); moving.add(first); moving.add(second);
        float[] firstLocal=first.transform.clone(),secondLocal=second.transform.clone();

        float[] requestedWorld={10f,-6f,2f,90f};
        float[] local=ProjectModel.relative(moving.parent().worldTransform(),requestedWorld);
        System.arraycopy(local,0,moving.transform,0,4);

        assertArrayEquals(firstLocal,first.transform,T);
        assertArrayEquals(secondLocal,second.transform,T);
        assertArrayEquals(requestedWorld,moving.worldTransform(),T);
        assertArrayEquals(ProjectModel.compose(requestedWorld,firstLocal),first.worldTransform(),T);
        assertArrayEquals(ProjectModel.compose(requestedWorld,secondLocal),second.worldTransform(),T);
    }

    @Test public void worldPoseConvertsOnceIntoNestedParentSpace(){
        ProjectModel p=new ProjectModel("p","p",1);
        ProjectModel.Group parent=new ProjectModel.Group("g","parent");
        ProjectModel.Scan moving=new ProjectModel.Scan("m","moving");
        parent.transform[0]=5f; parent.transform[1]=-2f; parent.transform[2]=1f; parent.transform[3]=35f;
        p.root.add(parent); parent.add(moving);
        float[] requestedWorld={12f,8f,3f,-110f};

        float[] local=ProjectModel.relative(parent.worldTransform(),requestedWorld);
        System.arraycopy(local,0,moving.transform,0,4);

        assertArrayEquals(requestedWorld,moving.worldTransform(),T);
        assertArrayEquals(new float[]{5f,-2f,1f,35f},parent.worldTransform(),T);
    }
    @Test public void storeSavesLoadsCopiesAndDeletesAtomically()throws Exception{
        java.io.File dir=Files.createTempDirectory("tzf-project-test").toFile();ProjectStore store=new ProjectStore(dir);ProjectModel p=new ProjectModel("abc-1","Original",1);p.root.add(new ProjectModel.Scan("s","Scan"));store.save(p);assertEquals(1,store.list().size());assertEquals(1,store.load(p.id).scanCount());ProjectModel copy=store.copy(p,"Copy",2);assertEquals(2,store.list().size());assertEquals("Copy",copy.name);store.delete(p.id);assertEquals(1,store.list().size());
    }
}
