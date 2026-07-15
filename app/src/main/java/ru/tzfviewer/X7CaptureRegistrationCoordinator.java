package ru.tzfviewer;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

final class X7CaptureRegistrationCoordinator {
    interface Starter { void start(String movingStationId,RegistrationWorkflow.CaptureTicket ticket); }
    private final ProjectModel project;
    private final RegistrationGraph graph;
    private final AtomicLong generation=new AtomicLong();
    private final Set<String> delivered=new HashSet<>();
    private String explicitReferenceId="";

    X7CaptureRegistrationCoordinator(ProjectModel project){this.project=project;graph=new RegistrationGraph(project);}
    void selectLast(){explicitReferenceId="";}
    void selectExplicit(String stationId){explicitReferenceId=stationId==null?"":stationId;}
    boolean usesLast(){return explicitReferenceId.isEmpty();}

    RegistrationWorkflow.CaptureTicket snapshotForNewCapture(){ProjectModel.Scan last=graph.lastAcquiredScan();String resolved=usesLast()?(last==null?"":last.id):explicitReferenceId;return new RegistrationWorkflow.CaptureTicket(generation.incrementAndGet(),resolved);}
    String targetLabel(){if(usesLast()){ProjectModel.Scan last=graph.lastAcquiredScan();return last==null?"Первый скан · без сшивки":"Последний: "+last.name;}ProjectModel.Node node=project.findNode(explicitReferenceId);return node instanceof ProjectModel.Scan?"Сшить с: "+node.name:"Сшить с: недоступен";}
    synchronized boolean onScanReady(String movingStationId,RegistrationWorkflow.CaptureTicket ticket,Starter starter){if(ticket==null||starter==null)return false;String key=ticket.generation+":"+movingStationId;if(!delivered.add(key))return false;starter.start(movingStationId,ticket);return true;}
}
