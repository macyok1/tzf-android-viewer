package ru.tzfviewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class RegistrationTreeModel {
    enum Kind { SET, STATION }
    enum Tone { GRAY, GREEN, ORANGE, RED }
    enum Action { ACCEPT, REJECT, RETRY, MANUAL_REGISTER, SET_REFERENCE, SET_MOVING, DETACH }
    static final class Row {
        final Kind kind;final String setId,stationId,title,status;final Tone tone;final Set<Action> actions;
        Row(Kind kind,String setId,String stationId,String title,String status,Tone tone,Set<Action> actions){this.kind=kind;this.setId=setId;this.stationId=stationId;this.title=title;this.status=status;this.tone=tone;this.actions=Collections.unmodifiableSet(actions);}
    }

    private RegistrationTreeModel(){}
    static List<Row> rows(ProjectModel project){List<Row> rows=new ArrayList<>();boolean multipleSets=project.registrationSets.size()>1;for(ProjectModel.RegistrationSet set:project.registrationSets){rows.add(new Row(Kind.SET,set.id,"",set.name,set.stationIds.size()+" станц.",Tone.GRAY,EnumSet.noneOf(Action.class)));for(String stationId:set.stationIds){ProjectModel.Node node=project.findNode(stationId);if(!(node instanceof ProjectModel.Scan))continue;ProjectModel.Scan scan=(ProjectModel.Scan)node;EnumSet<Action> actions=EnumSet.of(Action.SET_REFERENCE,Action.SET_MOVING);if(set.stationIds.size()>1)actions.add(Action.DETACH);if(scan.registrationState==ProjectModel.RegistrationState.CHECK){actions.add(Action.ACCEPT);actions.add(Action.REJECT);}else if(scan.registrationState==ProjectModel.RegistrationState.FAILED){if(!scan.attemptedReferenceId.isEmpty())actions.add(Action.RETRY);actions.add(Action.MANUAL_REGISTER);}else if(multipleSets&&(scan.registrationState==ProjectModel.RegistrationState.UNLINKED||scan.registrationState==ProjectModel.RegistrationState.LEGACY_UNLINKED))actions.add(Action.MANUAL_REGISTER);rows.add(new Row(Kind.STATION,set.id,scan.id,scan.name,label(scan.registrationState),tone(scan.registrationState),actions));}}return rows;}
    private static String label(ProjectModel.RegistrationState state){switch(state){case REGISTERED:return "сшит";case CHECK:return "проверить";case FAILED:return "не сшит";case PENDING:return "ожидает";case REGISTERING:return "сшивка";case LEGACY_UNLINKED:return "старый · не сшит";default:return "не сшит";}}
    private static Tone tone(ProjectModel.RegistrationState state){switch(state){case REGISTERED:return Tone.GREEN;case CHECK:case PENDING:case REGISTERING:return Tone.ORANGE;case FAILED:return Tone.RED;default:return Tone.GRAY;}}
}
