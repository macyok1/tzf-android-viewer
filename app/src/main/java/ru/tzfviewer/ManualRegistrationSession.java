package ru.tzfviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ManualRegistrationSession {
    private final RegistrationGraph graph;
    private final String referenceStationId,movingStationId;
    private final List<String> movingStationIds;
    private final Map<String,float[]> originalWorld=new LinkedHashMap<>(),previewWorld=new LinkedHashMap<>();
    private final float[] originalAnchor;
    private boolean active=true;

    ManualRegistrationSession(RegistrationGraph graph,String referenceStationId,String movingStationId){this.graph=graph;ProjectModel.Node reference=graph.project().findNode(referenceStationId),moving=graph.project().findNode(movingStationId);ProjectModel.RegistrationSet referenceSet=graph.setForScan(referenceStationId),movingSet=graph.setForScan(movingStationId);if(!(reference instanceof ProjectModel.Scan)||!(moving instanceof ProjectModel.Scan)||referenceSet==null||movingSet==null||referenceSet==movingSet)throw new IllegalArgumentException("manual R and M must belong to different sets");this.referenceStationId=referenceStationId;this.movingStationId=movingStationId;this.movingStationIds=new ArrayList<>(movingSet.stationIds);for(String id:movingStationIds){ProjectModel.Node node=graph.project().findNode(id);float[] world=node.worldTransform();originalWorld.put(id,world);previewWorld.put(id,world.clone());}originalAnchor=originalWorld.get(movingStationId).clone();}

    boolean active(){return active;}
    String referenceStationId(){return referenceStationId;}
    String movingStationId(){return movingStationId;}
    String[] movingStationIds(){return movingStationIds.toArray(new String[0]);}
    float[] previewAnchorWorld(){return previewWorld(movingStationId);}
    float[] previewWorld(String stationId){float[] pose=previewWorld.get(stationId);return pose==null?null:pose.clone();}

    void previewAnchor(float[] candidateWorld){if(!active||!RegistrationPoseMath.finite(candidateWorld))return;float[] correction=RegistrationPoseMath.correction(originalAnchor,candidateWorld);for(String id:movingStationIds)previewWorld.put(id,RegistrationPoseMath.compose(correction,originalWorld.get(id)));}
    boolean apply(ProjectModel.RegistrationMetrics metrics){if(!active)return false;float[] candidate=previewWorld.get(movingStationId);graph.acceptRegistration(referenceStationId,movingStationId,candidate,metrics,ProjectModel.LinkSource.MANUAL,System.currentTimeMillis());active=false;return true;}
    void cancel(){active=false;previewWorld.clear();}
}
