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
        ProjectModel p=new ProjectModel("abc","Съёмка",10);ProjectModel.Group g=new ProjectModel.Group("g","Связка 12");ProjectModel.Scan s=new ProjectModel.Scan("s","Скан №1"),other=new ProjectModel.Scan("o","Другой");s.uri="content://точки/1";s.transform[3]=42;g.expanded=false;p.root.add(g);g.add(s);p.root.add(other);p.setReference(g);p.setMoving(other);p.clipEnabled=true;p.clipLocked=true;System.arraycopy(new float[]{1,2,3,4,5,6},0,p.clipBounds,0,6);String encoded=ProjectCodec.encode(p);ProjectModel restored=ProjectCodec.decode(encoded);assertEquals("Съёмка",restored.name);assertEquals(2,restored.scanCount());ProjectModel.Group rg=(ProjectModel.Group)restored.findNode("g");ProjectModel.Scan rs=(ProjectModel.Scan)restored.findNode("s");assertEquals(s.uri,rs.uri);assertEquals(42,rs.transform[3],T);assertFalse(rg.expanded);assertEquals("",restored.referenceNodeId);assertEquals("",restored.movingNodeId);assertTrue(restored.clipEnabled);assertTrue(restored.clipLocked);assertArrayEquals(p.clipBounds,restored.clipBounds,T);
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
    @Test public void embeddedPoseRoundTripsInV4(){
        ProjectModel p=new ProjectModel("p","pose",1);ProjectModel.Scan scan=new ProjectModel.Scan("s","scan");scan.embeddedPoseValid=true;scan.embeddedPoseApplied=true;System.arraycopy(new float[]{100,200,3,-45},0,scan.embeddedPose,0,4);p.root.add(scan);
        ProjectModel.Scan restored=(ProjectModel.Scan)ProjectCodec.decode(ProjectCodec.encode(p)).findNode("s");
        assertTrue(restored.embeddedPoseValid);assertTrue(restored.embeddedPoseApplied);assertArrayEquals(scan.embeddedPose,restored.embeddedPose,T);
    }

    @Test public void codecV5RoundTripsRegistrationRecords(){
        ProjectModel p=new ProjectModel("p","v5",100);RegistrationGraph graph=new RegistrationGraph(p);
        ProjectModel.Scan reference=new ProjectModel.Scan("r","reference"),moving=new ProjectModel.Scan("m","moving");
        graph.addScan(p.root,reference,110);graph.addScan(p.root,moving,120);
        moving.registrationState=ProjectModel.RegistrationState.CHECK;moving.attemptedReferenceId="r";moving.registrationMessage="check registration";
        moving.registrationMetrics=new ProjectModel.RegistrationMetrics(1.2f,3.4f,.56f,.78f,91);moving.pendingCandidateValid=true;System.arraycopy(new float[]{4,5,6,7},0,moving.pendingCandidateWorld,0,4);
        ProjectModel restored=ProjectCodec.decode(ProjectCodec.encode(p));ProjectModel.Scan rm=(ProjectModel.Scan)restored.findNode("m");
        assertEquals(5,ProjectModel.FORMAT_VERSION);assertEquals(2,restored.registrationSets.size());assertEquals(120,rm.acquiredAt);assertEquals(ProjectModel.RegistrationState.CHECK,rm.registrationState);assertEquals("r",rm.attemptedReferenceId);assertEquals("check registration",rm.registrationMessage);assertEquals(1.2f,rm.registrationMetrics.rms,T);assertTrue(rm.pendingCandidateValid);assertArrayEquals(moving.pendingCandidateWorld,rm.pendingCandidateWorld,T);
    }

    @Test public void legacyV4MigratesToStableSeparateSetsWithoutMovingScans(){
        ProjectModel p=new ProjectModel("legacy","legacy",100);ProjectModel.Group folder=new ProjectModel.Group("g","folder");folder.transform[0]=7;folder.transform[3]=30;p.root.add(folder);ProjectModel.Scan a=new ProjectModel.Scan("a","a"),b=new ProjectModel.Scan("b","b");folder.add(a);p.root.add(b);a.transform[0]=3;b.transform[1]=8;
        String v4=asLegacyV4(ProjectCodec.encode(p));float[] aw=a.worldTransform(),bw=b.worldTransform();
        ProjectModel first=ProjectCodec.decode(v4),second=ProjectCodec.decode(v4);
        assertArrayEquals(aw,first.findNode("a").worldTransform(),T);assertArrayEquals(bw,first.findNode("b").worldTransform(),T);assertEquals(2,first.registrationSets.size());assertEquals(first.registrationSets.get(0).id,second.registrationSets.get(0).id);assertEquals(ProjectModel.RegistrationState.LEGACY_UNLINKED,((ProjectModel.Scan)first.findNode("a")).registrationState);
    }

    @Test public void codecRejectsDuplicateRegistrationMembership(){
        ProjectModel p=new ProjectModel("p","p",1);RegistrationGraph graph=new RegistrationGraph(p);graph.addScan(p.root,new ProjectModel.Scan("s","s"),1);
        String encoded=ProjectCodec.encode(p);String setLine=null;for(String line:encoded.split("\n"))if(line.startsWith("RS\t"))setLine=line;
        try{ProjectCodec.decode(encoded+setLine+"\n");fail();}catch(IllegalArgumentException expected){}
    }

    private static String asLegacyV4(String encoded){
        StringBuilder out=new StringBuilder();for(String line:encoded.split("\n")){if(line.startsWith("TZF_PROJECT\t")){out.append("TZF_PROJECT\t4\n");continue;}if(line.startsWith("RS\t")||line.startsWith("RL\t"))continue;if(line.startsWith("S\t")){String[] fields=line.split("\t",-1);line=String.join("\t",java.util.Arrays.copyOf(fields,19));}out.append(line).append('\n');}return out.toString();
    }

    @Test public void legacyV3MigrationNeverMovesExistingScan(){
        ProjectModel p=new ProjectModel("p","legacy",1);ProjectModel.Scan scan=new ProjectModel.Scan("s","scan");System.arraycopy(new float[]{12,-8,3,77},0,scan.transform,0,4);p.root.add(scan);
        String[] lines=ProjectCodec.encode(p).split("\\n");lines[0]="TZF_PROJECT\t3";String[] fields=lines[2].split("\\t",-1);lines[2]=String.join("\t",java.util.Arrays.copyOf(fields,13));
        ProjectModel.Scan migrated=(ProjectModel.Scan)ProjectCodec.decode(String.join("\n",lines)).findNode("s");
        assertArrayEquals(scan.transform,migrated.transform,T);assertTrue(migrated.embeddedPoseApplied);assertFalse(migrated.embeddedPoseValid);
    }

    @Test public void embeddedPosesUseFirstScanAsLocalOrigin(){
        ProjectModel p=new ProjectModel("p","poses",1);ProjectModel.Scan anchor=new ProjectModel.Scan("a","a"),moving=new ProjectModel.Scan("m","m");p.root.add(anchor);
        assertTrue(p.initializeEmbeddedPose(anchor,new float[]{1500,1500,0,-177.364f}));assertArrayEquals(new float[4],anchor.transform,T);
        p.root.add(moving);assertTrue(p.initializeEmbeddedPose(moving,new float[]{1998.0331f,2288.1156f,3.8171f,59.0f}));
        assertEquals(-533.7f,moving.transform[0],.5f);assertEquals(-764.4f,moving.transform[1],.5f);assertEquals(3.8171f,moving.transform[2],T);assertEquals(-123.636f,moving.transform[3],.01f);
    }

    @Test public void invalidPoseAndLegacyProjectDoNotMoveNewScan(){
        ProjectModel p=new ProjectModel("p","mixed",1);ProjectModel.Scan legacy=new ProjectModel.Scan("l","legacy"),added=new ProjectModel.Scan("a","added");legacy.embeddedPoseApplied=true;p.root.add(legacy);p.root.add(added);
        assertTrue(p.initializeEmbeddedPose(added,new float[]{1,2,3,4}));assertArrayEquals(new float[4],added.transform,T);assertTrue(added.embeddedPoseValid);
        ProjectModel.Scan invalid=new ProjectModel.Scan("i","invalid");p.root.add(invalid);assertTrue(p.initializeEmbeddedPose(invalid,new float[]{Float.NaN,0,0,0}));assertFalse(invalid.embeddedPoseValid);assertArrayEquals(new float[4],invalid.transform,T);
    }

    @Test public void legacyScanCapturesMetadataWithoutChangingTransform(){
        ProjectModel p=new ProjectModel("p","legacy",1);ProjectModel.Scan scan=new ProjectModel.Scan("s","scan");scan.embeddedPoseApplied=true;System.arraycopy(new float[]{5,-2,1,30},0,scan.transform,0,4);p.root.add(scan);
        assertTrue(p.initializeEmbeddedPose(scan,new float[]{1500,1500,0,-177}));assertTrue(scan.embeddedPoseValid);assertArrayEquals(new float[]{5,-2,1,30},scan.transform,T);
    }

    @Test public void equalRawX7PosesAreNotARegistrationPrior(){
        assertFalse(ProjectModel.informativeEmbeddedOffset(new float[4]));
        assertFalse(ProjectModel.informativeEmbeddedOffset(new float[]{10,-10,5,.1f}));
        assertTrue(ProjectModel.informativeEmbeddedOffset(new float[]{500,0,0,0}));
        assertTrue(ProjectModel.informativeEmbeddedOffset(new float[]{0,0,0,12}));
    }

    @Test public void storeSavesLoadsCopiesAndDeletesAtomically()throws Exception{
        java.io.File dir=Files.createTempDirectory("tzf-project-test").toFile();ProjectStore store=new ProjectStore(dir);ProjectModel p=new ProjectModel("abc-1","Original",1);p.root.add(new ProjectModel.Scan("s","Scan"));store.save(p);assertEquals(1,store.list().size());assertEquals(1,store.load(p.id).scanCount());ProjectModel copy=store.copy(p,"Copy",2);assertEquals(2,store.list().size());assertEquals("Copy",copy.name);store.delete(p.id);assertEquals(1,store.list().size());
    }
}
