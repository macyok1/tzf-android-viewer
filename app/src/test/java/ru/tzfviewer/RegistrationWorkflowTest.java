package ru.tzfviewer;

import org.junit.Test;

import static org.junit.Assert.*;

public class RegistrationWorkflowTest {
    @Test public void firstCapturedScanSkipsRegistration(){
        Fixture f=new Fixture();ProjectModel.Scan first=f.add("1",1);
        RegistrationWorkflow.Outcome outcome=f.workflow.start(first.id,new RegistrationWorkflow.CaptureTicket(1,""));
        assertEquals(RegistrationWorkflow.Outcome.SKIPPED,outcome);assertEquals(ProjectModel.RegistrationState.UNLINKED,first.registrationState);assertEquals(0,f.engine.calls);
    }

    @Test public void acceptedResultCreatesExactlyOneLinkAndMerge(){
        Fixture f=new Fixture();ProjectModel.Scan reference=f.add("r",1),moving=f.add("m",2);
        f.engine.result=RegistrationEngine.Result.accepted(new float[]{3,4,0,25},metrics(),"registered");
        RegistrationWorkflow.Outcome outcome=f.workflow.start("m",new RegistrationWorkflow.CaptureTicket(7,"r"));
        assertEquals(RegistrationWorkflow.Outcome.COMPLETE,outcome);assertEquals(1,f.project.registrationLinks.size());assertSame(f.graph.setForScan("r"),f.graph.setForScan("m"));assertEquals(ProjectModel.RegistrationState.REGISTERED,moving.registrationState);assertEquals("r",moving.attemptedReferenceId);assertArrayEquals(new float[]{3,4,0,25},moving.worldTransform(),1e-4f);assertNotNull(reference);
    }

    @Test public void checkStoresDirectCandidateUntilExplicitConfirmation(){
        Fixture f=new Fixture();f.add("r",1);ProjectModel.Scan moving=f.add("m",2);float[] before=moving.worldTransform();
        f.engine.result=RegistrationEngine.Result.check(new float[]{8,-2,1,90},metrics(),"check registration");
        assertEquals(RegistrationWorkflow.Outcome.CHECK,f.workflow.start("m",new RegistrationWorkflow.CaptureTicket(3,"r")));
        assertArrayEquals(before,moving.worldTransform(),1e-4f);assertEquals(2,f.project.registrationSets.size());assertTrue(f.project.registrationLinks.isEmpty());assertTrue(moving.pendingCandidateValid);assertArrayEquals(new float[]{8,-2,1,90},moving.pendingCandidateWorld,1e-4f);
        assertTrue(f.workflow.confirmCheck("m",ProjectModel.LinkSource.AUTO_X7));assertArrayEquals(new float[]{8,-2,1,90},moving.worldTransform(),1e-4f);assertEquals(1,f.project.registrationLinks.size());
    }

    @Test public void rejectionFailsWithoutOpeningOrMutatingRegistration(){
        Fixture f=new Fixture();f.add("r",1);ProjectModel.Scan moving=f.add("m",2);float[] before=moving.worldTransform();
        f.engine.result=RegistrationEngine.Result.rejected(metrics(),"low confidence");
        assertEquals(RegistrationWorkflow.Outcome.FAILED,f.workflow.start("m",new RegistrationWorkflow.CaptureTicket(4,"r")));
        assertEquals(ProjectModel.RegistrationState.FAILED,moving.registrationState);assertArrayEquals(before,moving.worldTransform(),1e-4f);assertEquals(2,f.project.registrationSets.size());assertTrue(f.project.registrationLinks.isEmpty());
    }

    @Test public void staleCompletionAfterCancelCannotMutateProject(){
        Fixture f=new Fixture();f.add("r",1);ProjectModel.Scan moving=f.add("m",2);
        f.engine.result=RegistrationEngine.Result.accepted(new float[]{7,0,0,0},metrics(),"registered");f.engine.duringRegister=f.workflow::cancel;
        assertEquals(RegistrationWorkflow.Outcome.STALE,f.workflow.start("m",new RegistrationWorkflow.CaptureTicket(9,"r")));
        assertEquals(ProjectModel.RegistrationState.PENDING,moving.registrationState);assertTrue(f.project.registrationLinks.isEmpty());assertEquals(2,f.project.registrationSets.size());
    }

    @Test public void missingReferenceProducesActionableFailure(){
        Fixture f=new Fixture();ProjectModel.Scan moving=f.add("m",2);
        assertEquals(RegistrationWorkflow.Outcome.FAILED,f.workflow.start("m",new RegistrationWorkflow.CaptureTicket(2,"missing")));
        assertEquals(ProjectModel.RegistrationState.FAILED,moving.registrationState);assertTrue(moving.registrationMessage.contains("missing"));assertEquals(0,f.engine.calls);
    }

    private static ProjectModel.RegistrationMetrics metrics(){return new ProjectModel.RegistrationMetrics(1,2,.6f,.8f,90);}
    private static final class Fixture {
        final ProjectModel project=new ProjectModel("p","p",1);final RegistrationGraph graph=new RegistrationGraph(project);final FakeEngine engine=new FakeEngine();final RegistrationWorkflow workflow=new RegistrationWorkflow(graph,engine,()->{});
        ProjectModel.Scan add(String id,long at){ProjectModel.Scan scan=new ProjectModel.Scan(id,id);graph.addScan(project.root,scan,at);return scan;}
    }
    private static final class FakeEngine implements RegistrationEngine {
        int calls;Runnable duringRegister;Result result=Result.rejected(metrics(),"rejected");
        @Override public Result register(Request request){calls++;if(duringRegister!=null)duringRegister.run();return result;}
        @Override public void cancel(){}
    }
}
