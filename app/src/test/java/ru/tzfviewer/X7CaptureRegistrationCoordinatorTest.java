package ru.tzfviewer;

import org.junit.Test;

import static org.junit.Assert.*;

public class X7CaptureRegistrationCoordinatorTest {
    @Test public void firstScanHasNoTargetAndLastTracksNewestDownload(){
        ProjectModel project=new ProjectModel("p","p",1);RegistrationGraph graph=new RegistrationGraph(project);X7CaptureRegistrationCoordinator coordinator=new X7CaptureRegistrationCoordinator(project);
        assertEquals("",coordinator.snapshotForNewCapture().resolvedReferenceId);assertEquals("Первый скан · без сшивки",coordinator.targetLabel());
        add(graph,"1","One",10);assertTrue(coordinator.targetLabel().contains("One"));assertEquals("1",coordinator.snapshotForNewCapture().resolvedReferenceId);
        add(graph,"2","Two",20);assertEquals("2",coordinator.snapshotForNewCapture().resolvedReferenceId);
    }

    @Test public void captureSnapshotsDynamicOrExplicitTarget(){
        ProjectModel project=new ProjectModel("p","p",1);RegistrationGraph graph=new RegistrationGraph(project);X7CaptureRegistrationCoordinator coordinator=new X7CaptureRegistrationCoordinator(project);add(graph,"1","One",10);
        RegistrationWorkflow.CaptureTicket fixed=coordinator.snapshotForNewCapture();add(graph,"2","Two",20);assertEquals("1",fixed.resolvedReferenceId);assertEquals("2",coordinator.snapshotForNewCapture().resolvedReferenceId);
        coordinator.selectExplicit("1");assertEquals("1",coordinator.snapshotForNewCapture().resolvedReferenceId);assertTrue(coordinator.targetLabel().contains("One"));
    }

    @Test public void onlyTicketedNewCaptureStartsOnceWhenDecodeReady(){
        ProjectModel project=new ProjectModel("p","p",1);RegistrationGraph graph=new RegistrationGraph(project);add(graph,"1","One",10);X7CaptureRegistrationCoordinator coordinator=new X7CaptureRegistrationCoordinator(project);RegistrationWorkflow.CaptureTicket ticket=coordinator.snapshotForNewCapture();final int[] calls={0};
        assertFalse(coordinator.onScanReady("manual",null,(id,t)->calls[0]++));assertTrue(coordinator.onScanReady("new",ticket,(id,t)->calls[0]++));assertFalse(coordinator.onScanReady("new",ticket,(id,t)->calls[0]++));assertEquals(1,calls[0]);
    }

    private static ProjectModel.Scan add(RegistrationGraph graph,String id,String name,long at){ProjectModel.Scan scan=new ProjectModel.Scan(id,name);graph.addScan(graph.project().root,scan,at);return scan;}
}
