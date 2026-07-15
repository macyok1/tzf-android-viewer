package ru.tzfviewer;

import org.junit.Test;

import static org.junit.Assert.*;

public class ManualRegistrationSessionTest {
    private static final float T=1e-4f;

    @Test public void previewMovesWholeSetRigidlyWithoutMutatingProjectAndCancelRollsBack(){
        Fixture f=new Fixture();ProjectModel.Scan reference=f.add("r",1,0),moving=f.add("m",2,10),child=f.add("c",3,13);f.graph.acceptRegistration("m","c",new float[]{13,0,0,0},metrics(),ProjectModel.LinkSource.AUTO_X7,4);float[] movingBefore=moving.worldTransform(),childBefore=child.worldTransform(),internal=RegistrationPoseMath.relative(movingBefore,childBefore);
        ManualRegistrationSession session=new ManualRegistrationSession(f.graph,"r","m");session.previewAnchor(new float[]{2,5,0,30});
        assertArrayEquals(movingBefore,moving.worldTransform(),T);assertArrayEquals(childBefore,child.worldTransform(),T);assertArrayEquals(new float[]{2,5,0,30},session.previewWorld("m"),T);assertArrayEquals(internal,RegistrationPoseMath.relative(session.previewWorld("m"),session.previewWorld("c")),T);
        session.cancel();assertArrayEquals(movingBefore,moving.worldTransform(),T);assertArrayEquals(childBefore,child.worldTransform(),T);assertFalse(session.active());assertNotNull(reference);
    }

    @Test public void applyCommitsOneGraphMergeAndLink(){
        Fixture f=new Fixture();f.add("r",1,0);ProjectModel.Scan moving=f.add("m",2,10);ManualRegistrationSession session=new ManualRegistrationSession(f.graph,"r","m");session.previewAnchor(new float[]{3,-2,1,45});
        assertTrue(session.apply(metrics()));assertFalse(session.active());assertArrayEquals(new float[]{3,-2,1,45},moving.worldTransform(),T);assertEquals(1,f.project.registrationSets.size());assertEquals(1,f.project.registrationLinks.size());assertEquals(ProjectModel.LinkSource.MANUAL,f.project.registrationLinks.get(0).source);
    }

    @Test public void sameSetAndApplyAfterCancelAreRejected(){
        Fixture f=new Fixture();f.add("a",1,0);f.add("b",2,1);f.graph.acceptRegistration("a","b",new float[]{1,0,0,0},metrics(),ProjectModel.LinkSource.AUTO_X7,3);try{new ManualRegistrationSession(f.graph,"a","b");fail();}catch(IllegalArgumentException expected){}
        Fixture separate=new Fixture();separate.add("r",1,0);separate.add("m",2,1);ManualRegistrationSession session=new ManualRegistrationSession(separate.graph,"r","m");session.cancel();assertFalse(session.apply(metrics()));assertTrue(separate.project.registrationLinks.isEmpty());
    }

    private static ProjectModel.RegistrationMetrics metrics(){return new ProjectModel.RegistrationMetrics(1,2,.6f,.8f,90);}
    private static final class Fixture {final ProjectModel project=new ProjectModel("p","p",1);final RegistrationGraph graph=new RegistrationGraph(project);ProjectModel.Scan add(String id,long at,float x){ProjectModel.Scan scan=new ProjectModel.Scan(id,id);graph.addScan(project.root,scan,at);scan.transform[0]=x;return scan;}}
}
