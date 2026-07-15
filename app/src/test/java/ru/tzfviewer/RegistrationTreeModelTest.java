package ru.tzfviewer;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class RegistrationTreeModelTest {
    @Test public void rowsFollowSetAndAcquisitionOrderWithoutRoleActions(){
        ProjectModel project=new ProjectModel("p","p",1);RegistrationGraph graph=new RegistrationGraph(project);
        ProjectModel.Scan first=add(graph,"1",10),second=add(graph,"2",20);graph.acceptRegistration("1","2",new float[]{1,0,0,0},metrics(),ProjectModel.LinkSource.AUTO_X7,30);
        List<RegistrationTreeModel.Row> rows=RegistrationTreeModel.rows(project);
        assertEquals(3,rows.size());assertEquals(RegistrationTreeModel.Kind.SET,rows.get(0).kind);assertEquals("1",rows.get(1).stationId);assertEquals("2",rows.get(2).stationId);assertTrue(rows.get(1).actions.contains(RegistrationTreeModel.Action.SET_REFERENCE));assertTrue(rows.get(1).actions.contains(RegistrationTreeModel.Action.SET_MOVING));assertTrue(rows.get(1).actions.contains(RegistrationTreeModel.Action.DETACH));
        assertEquals(RegistrationTreeModel.Tone.GREEN,rows.get(2).tone);
    }

    @Test public void checkAndFailedExposeOnlyContextualActions(){
        ProjectModel project=new ProjectModel("p","p",1);RegistrationGraph graph=new RegistrationGraph(project);ProjectModel.Scan check=add(graph,"c",1),failed=add(graph,"f",2);check.registrationState=ProjectModel.RegistrationState.CHECK;failed.registrationState=ProjectModel.RegistrationState.FAILED;failed.attemptedReferenceId="c";
        List<RegistrationTreeModel.Row> rows=RegistrationTreeModel.rows(project);RegistrationTreeModel.Row checkRow=find(rows,"c"),failedRow=find(rows,"f");
        assertEquals(RegistrationTreeModel.Tone.ORANGE,checkRow.tone);assertTrue(checkRow.actions.contains(RegistrationTreeModel.Action.ACCEPT));assertTrue(checkRow.actions.contains(RegistrationTreeModel.Action.REJECT));assertEquals(RegistrationTreeModel.Tone.RED,failedRow.tone);assertTrue(failedRow.actions.contains(RegistrationTreeModel.Action.MANUAL_REGISTER));assertTrue(failedRow.actions.contains(RegistrationTreeModel.Action.RETRY));
    }

    private static RegistrationTreeModel.Row find(List<RegistrationTreeModel.Row> rows,String id){for(RegistrationTreeModel.Row row:rows)if(id.equals(row.stationId))return row;throw new AssertionError(id);}
    private static ProjectModel.Scan add(RegistrationGraph graph,String id,long at){ProjectModel.Scan scan=new ProjectModel.Scan(id,id);graph.addScan(graph.project().root,scan,at);return scan;}
    private static ProjectModel.RegistrationMetrics metrics(){return new ProjectModel.RegistrationMetrics(1,2,.6f,.8f,90);}
}
