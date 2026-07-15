package ru.tzfviewer;

import org.junit.Test;

import static org.junit.Assert.*;

public class RegistrationGraphTest {
    private static final float T=1e-4f;

    @Test public void everyAddedScanReceivesExactlyOneSet(){
        ProjectModel project=new ProjectModel("p","p",10);
        RegistrationGraph graph=new RegistrationGraph(project);
        ProjectModel.Scan scan=new ProjectModel.Scan("s","scan");
        ProjectModel.RegistrationSet set=graph.addScan(project.root,scan,20);
        assertSame(set,graph.setForScan("s"));
        assertEquals(1,project.registrationSets.size());
        assertEquals(java.util.Collections.singletonList("s"),set.stationIds);
        graph.validate();
    }

    @Test public void acceptedRegistrationMovesWholeSetRigidlyAndMergesIntoReference(){
        ProjectModel project=new ProjectModel("p","p",1);
        RegistrationGraph graph=new RegistrationGraph(project);
        ProjectModel.Scan one=scan(graph,"1",1,0,0,0);
        ProjectModel.Scan two=scan(graph,"2",2,10,0,15);
        ProjectModel.Scan three=scan(graph,"3",3,13,4,25);
        graph.acceptRegistration("1","2",new float[]{2,3,0,45},metrics(),ProjectModel.LinkSource.AUTO_X7,10);
        float[] beforeRelative=RegistrationPoseMath.relative(two.worldTransform(),three.worldTransform());

        // Build a second moving set containing stations 2 and 3, then merge 23 -> 1.
        ProjectModel project2=new ProjectModel("p2","p2",1);
        RegistrationGraph graph2=new RegistrationGraph(project2);
        ProjectModel.Scan reference=scan(graph2,"1",1,0,0,0);
        ProjectModel.Scan moving=scan(graph2,"2",2,10,0,15);
        ProjectModel.Scan child=scan(graph2,"3",3,13,4,25);
        graph2.acceptRegistration("2","3",new float[]{13,4,0,25},metrics(),ProjectModel.LinkSource.AUTO_X7,5);
        float[] oldMoving=moving.worldTransform(),oldChild=child.worldTransform();
        float[] oldInternal=RegistrationPoseMath.relative(oldMoving,oldChild);
        graph2.acceptRegistration("1","2",new float[]{2,3,0,45},metrics(),ProjectModel.LinkSource.MANUAL,10);

        assertArrayEquals(new float[]{0,0,0,0},reference.worldTransform(),T);
        assertArrayEquals(new float[]{2,3,0,45},moving.worldTransform(),T);
        assertArrayEquals(oldInternal,RegistrationPoseMath.relative(moving.worldTransform(),child.worldTransform()),T);
        assertEquals(graph2.setForScan("1"),graph2.setForScan("2"));
        assertEquals(graph2.setForScan("1"),graph2.setForScan("3"));
        assertEquals(1,project2.registrationSets.size());
        assertEquals(2,project2.registrationLinks.size());
        assertArrayEquals(beforeRelative,beforeRelative,T);
        assertFalse(java.util.Arrays.equals(oldMoving,moving.worldTransform()));
        assertFalse(java.util.Arrays.equals(oldChild,child.worldTransform()));
    }

    @Test public void mergePreservesUserFolderAndRejectsSameSet(){
        ProjectModel project=new ProjectModel("p","p",1);
        ProjectModel.Group folder=new ProjectModel.Group("g","folder");
        folder.transform[0]=50;folder.transform[3]=30;project.root.add(folder);
        RegistrationGraph graph=new RegistrationGraph(project);
        ProjectModel.Scan reference=scan(graph,"r",1,0,0,0);
        ProjectModel.Scan moving=new ProjectModel.Scan("m","m");
        graph.addScan(folder,moving,2);System.arraycopy(new float[]{4,2,1,10},0,moving.transform,0,4);
        graph.acceptRegistration("r","m",new float[]{5,-3,2,-20},metrics(),ProjectModel.LinkSource.AUTO_X7,3);
        assertSame(folder,moving.parent());
        assertArrayEquals(new float[]{5,-3,2,-20},moving.worldTransform(),T);
        try{graph.acceptRegistration("r","m",new float[4],metrics(),ProjectModel.LinkSource.MANUAL,4);fail();}catch(IllegalArgumentException expected){}
        assertArrayEquals(new float[]{0,0,0,0},reference.worldTransform(),T);
    }

    @Test public void deletingStationCleansMembershipLinksAndAttemptedReferences(){
        ProjectModel project=new ProjectModel("p","p",1);RegistrationGraph graph=new RegistrationGraph(project);
        ProjectModel.Scan a=scan(graph,"a",1,0,0,0),b=scan(graph,"b",2,1,0,0),c=scan(graph,"c",3,2,0,0);
        graph.acceptRegistration("a","b",new float[]{1,0,0,0},metrics(),ProjectModel.LinkSource.AUTO_X7,4);
        c.attemptedReferenceId="b";
        graph.removeNode(b);
        assertNull(project.findNode("b"));
        assertNull(graph.setForScan("b"));
        assertTrue(project.registrationLinks.isEmpty());
        assertEquals("",c.attemptedReferenceId);
        graph.validate();
    }

    private static ProjectModel.Scan scan(RegistrationGraph graph,String id,long at,float x,float y,float yaw){ProjectModel.Scan scan=new ProjectModel.Scan(id,id);graph.addScan(graph.project().root,scan,at);scan.transform[0]=x;scan.transform[1]=y;scan.transform[3]=yaw;return scan;}
    private static ProjectModel.RegistrationMetrics metrics(){return new ProjectModel.RegistrationMetrics(1,2,.6f,.8f,90);}
}
