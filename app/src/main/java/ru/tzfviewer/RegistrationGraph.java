package ru.tzfviewer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RegistrationGraph {
    private final ProjectModel project;

    RegistrationGraph(ProjectModel project){if(project==null)throw new IllegalArgumentException("project");this.project=project;}
    ProjectModel project(){return project;}

    ProjectModel.RegistrationSet addScan(ProjectModel.Group parent,ProjectModel.Scan scan,long acquiredAt){
        if(parent==null||scan==null)throw new IllegalArgumentException("scan parent");
        if(scan.parent()==null)parent.add(scan);else if(scan.parent()!=parent)throw new IllegalArgumentException("scan already belongs to another folder");
        if(setForScan(scan.id)!=null)throw new IllegalArgumentException("scan already belongs to a registration set");
        scan.acquiredAt=Math.max(1,acquiredAt);
        scan.registrationState=ProjectModel.RegistrationState.UNLINKED;
        return createSet(scan,UUID.randomUUID().toString(),scan.acquiredAt);
    }

    ProjectModel.RegistrationSet trackExistingScan(ProjectModel.Scan scan,long acquiredAt){
        if(scan==null||scan.parent()==null)throw new IllegalArgumentException("scan must be in project");
        if(setForScan(scan.id)!=null)throw new IllegalArgumentException("scan already tracked");
        scan.acquiredAt=Math.max(1,acquiredAt);return createSet(scan,UUID.randomUUID().toString(),scan.acquiredAt);
    }

    void ensureTrackedScans(long baseTime){
        long next=Math.max(1,baseTime);for(ProjectModel.Scan scan:allScans()){if(scan.acquiredAt<=0)scan.acquiredAt=next++;else next=Math.max(next,scan.acquiredAt+1);if(setForScan(scan.id)==null)createSet(scan,UUID.randomUUID().toString(),scan.acquiredAt);}
    }

    void migrateLegacyScans(){
        project.registrationSets.clear();project.registrationLinks.clear();long at=Math.max(1,project.createdAt);int ordinal=0;
        for(ProjectModel.Scan scan:allScans()){scan.acquiredAt=at+ordinal++;scan.registrationState=ProjectModel.RegistrationState.LEGACY_UNLINKED;scan.attemptedReferenceId="";scan.registrationMessage="";scan.registrationMetrics=ProjectModel.RegistrationMetrics.empty();scan.pendingCandidateValid=false;java.util.Arrays.fill(scan.pendingCandidateWorld,0);String seed=project.id+":legacy-set:"+scan.id;createSet(scan,UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString(),scan.acquiredAt);}
    }

    private ProjectModel.RegistrationSet createSet(ProjectModel.Scan scan,String id,long now){ProjectModel.RegistrationSet set=new ProjectModel.RegistrationSet(id,"Набор сшивки "+(project.registrationSets.size()+1),now);set.stationIds.add(scan.id);project.registrationSets.add(set);return set;}

    ProjectModel.RegistrationSet setForScan(String scanId){if(scanId==null)return null;for(ProjectModel.RegistrationSet set:project.registrationSets)if(set.stationIds.contains(scanId))return set;return null;}

    ProjectModel.Scan lastAcquiredScan(){ProjectModel.Scan latest=null;for(ProjectModel.Scan scan:allScans())if(latest==null||scan.acquiredAt>latest.acquiredAt)latest=scan;return latest;}

    List<ProjectModel.Scan> eligibleReferences(String movingId){ProjectModel.RegistrationSet moving=setForScan(movingId);List<ProjectModel.Scan> result=new ArrayList<>();for(ProjectModel.Scan scan:allScans())if(!scan.id.equals(movingId)&&setForScan(scan.id)!=moving)result.add(scan);result.sort(Comparator.comparingLong(s->s.acquiredAt));return result;}

    ProjectModel.RegistrationLink acceptRegistration(String referenceId,String movingId,float[] candidateMovingWorld,ProjectModel.RegistrationMetrics metrics,ProjectModel.LinkSource source,long now){
        validate();
        ProjectModel.Node referenceNode=project.findNode(referenceId),movingNode=project.findNode(movingId);
        if(!(referenceNode instanceof ProjectModel.Scan)||!(movingNode instanceof ProjectModel.Scan))throw new IllegalArgumentException("registration stations missing");
        ProjectModel.Scan reference=(ProjectModel.Scan)referenceNode,moving=(ProjectModel.Scan)movingNode;
        ProjectModel.RegistrationSet referenceSet=setForScan(referenceId),movingSet=setForScan(movingId);
        if(referenceSet==null||movingSet==null||referenceSet==movingSet)throw new IllegalArgumentException("stations must belong to different registration sets");
        if(!RegistrationPoseMath.finite(candidateMovingWorld)||metrics==null||!metrics.finite()||source==null)throw new IllegalArgumentException("invalid registration result");

        float[] correction=RegistrationPoseMath.correction(moving.worldTransform(),candidateMovingWorld);
        Map<ProjectModel.Scan,float[]> newLocals=new HashMap<>();
        for(String id:movingSet.stationIds){ProjectModel.Node node=project.findNode(id);if(!(node instanceof ProjectModel.Scan))throw new IllegalArgumentException("missing moving set station");ProjectModel.Scan scan=(ProjectModel.Scan)node;float[] newWorld=RegistrationPoseMath.compose(correction,scan.worldTransform());float[] local=RegistrationPoseMath.relative(scan.parent().worldTransform(),newWorld);if(!RegistrationPoseMath.finite(local))throw new IllegalArgumentException("invalid corrected pose");newLocals.put(scan,local);}
        float[] relative=RegistrationPoseMath.relative(reference.worldTransform(),candidateMovingWorld);
        ProjectModel.RegistrationLink link=new ProjectModel.RegistrationLink(UUID.randomUUID().toString(),movingId,referenceId,relative,metrics,source,now);

        for(Map.Entry<ProjectModel.Scan,float[]> entry:newLocals.entrySet())System.arraycopy(entry.getValue(),0,entry.getKey().transform,0,4);
        referenceSet.stationIds.addAll(movingSet.stationIds);referenceSet.stationIds.sort(Comparator.comparingLong(id->((ProjectModel.Scan)project.findNode(id)).acquiredAt));referenceSet.modifiedAt=now;project.registrationSets.remove(movingSet);project.registrationLinks.add(link);
        for(String id:referenceSet.stationIds){ProjectModel.Scan scan=(ProjectModel.Scan)project.findNode(id);scan.registrationState=ProjectModel.RegistrationState.REGISTERED;}
        moving.attemptedReferenceId=referenceId;moving.registrationMetrics=metrics;moving.registrationMessage="";moving.pendingCandidateValid=false;java.util.Arrays.fill(moving.pendingCandidateWorld,0);
        validate();return link;
    }

    void removeNode(ProjectModel.Node node){
        if(node==null||node.parent()==null)throw new IllegalArgumentException("cannot remove node");
        Set<String> removed=new HashSet<>();collectScanIds(node,removed);project.clearRoleFor(node);node.parent().remove(node);
        for(ProjectModel.RegistrationSet set:new ArrayList<>(project.registrationSets)){set.stationIds.removeIf(removed::contains);if(set.stationIds.isEmpty())project.registrationSets.remove(set);}
        project.registrationLinks.removeIf(link->removed.contains(link.movingStationId)||removed.contains(link.referenceStationId));
        for(ProjectModel.Scan scan:allScans())if(removed.contains(scan.attemptedReferenceId)){scan.attemptedReferenceId="";scan.pendingCandidateValid=false;java.util.Arrays.fill(scan.pendingCandidateWorld,0);if(scan.registrationState==ProjectModel.RegistrationState.CHECK||scan.registrationState==ProjectModel.RegistrationState.PENDING||scan.registrationState==ProjectModel.RegistrationState.REGISTERING)scan.registrationState=ProjectModel.RegistrationState.FAILED;}
        validate();
    }

    void validate(){
        Map<String,Integer> memberships=new HashMap<>();Set<String> setIds=new HashSet<>();
        for(ProjectModel.RegistrationSet set:project.registrationSets){if(set.id==null||set.id.isEmpty()||!setIds.add(set.id)||set.stationIds.isEmpty())throw new IllegalArgumentException("invalid registration set");Set<String> local=new HashSet<>();for(String id:set.stationIds){if(!local.add(id)||!(project.findNode(id) instanceof ProjectModel.Scan))throw new IllegalArgumentException("invalid registration membership");memberships.put(id,memberships.getOrDefault(id,0)+1);}}
        for(ProjectModel.Scan scan:allScans()){if(memberships.getOrDefault(scan.id,0)!=1)throw new IllegalArgumentException("scan must belong to exactly one registration set");if(scan.acquiredAt<=0||scan.registrationState==null||scan.registrationMetrics==null||!scan.registrationMetrics.finite())throw new IllegalArgumentException("invalid station registration state");if(!scan.attemptedReferenceId.isEmpty()&&!(project.findNode(scan.attemptedReferenceId) instanceof ProjectModel.Scan))throw new IllegalArgumentException("missing attempted reference");if(scan.pendingCandidateValid&&!RegistrationPoseMath.finite(scan.pendingCandidateWorld))throw new IllegalArgumentException("invalid pending candidate");}
        Set<String> linkIds=new HashSet<>();for(ProjectModel.RegistrationLink link:project.registrationLinks){if(link.id==null||link.id.isEmpty()||!linkIds.add(link.id)||!(project.findNode(link.movingStationId) instanceof ProjectModel.Scan)||!(project.findNode(link.referenceStationId) instanceof ProjectModel.Scan)||link.movingStationId.equals(link.referenceStationId)||!RegistrationPoseMath.finite(link.relativeTransform)||link.metrics==null||!link.metrics.finite()||link.source==null)throw new IllegalArgumentException("invalid registration link");}
    }

    List<ProjectModel.Scan> allScans(){List<ProjectModel.Scan> scans=new ArrayList<>();collectScans(project.root,scans);return scans;}
    private static void collectScans(ProjectModel.Group group,List<ProjectModel.Scan> out){for(ProjectModel.Node child:group.children())if(child instanceof ProjectModel.Scan)out.add((ProjectModel.Scan)child);else collectScans((ProjectModel.Group)child,out);}
    private static void collectScanIds(ProjectModel.Node node,Set<String> out){if(node instanceof ProjectModel.Scan){out.add(node.id);return;}for(ProjectModel.Node child:((ProjectModel.Group)node).children())collectScanIds(child,out);}
}
