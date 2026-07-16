package ru.tzfviewer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProjectCodec {
    private ProjectCodec() {}

    static String encode(ProjectModel project){
        RegistrationGraph graph=new RegistrationGraph(project);graph.ensureTrackedScans(Math.max(project.createdAt,project.modifiedAt));graph.validate();
        StringBuilder out=new StringBuilder("TZF_PROJECT\t").append(ProjectModel.FORMAT_VERSION).append('\n');
        out.append("P\t").append(e(project.id)).append('\t').append(e(project.name)).append('\t').append(project.createdAt).append('\t').append(project.modifiedAt).append('\t').append(project.pointBudget).append('\t').append(project.pointSize).append('\t').append(project.cameraYaw).append('\t').append(project.cameraPitch).append('\t').append(project.cameraZoom).append('\t').append(project.orthographic).append('\t').append(project.gridVisible).append('\t').append(e("")).append('\t').append(e("")).append('\t').append(project.clipEnabled).append('\t').append(project.clipLocked);for(float value:project.clipBounds)out.append('\t').append(value);out.append('\n');
        for(ProjectModel.Node child:project.root.children())writeNode(out,child,"root");
        for(ProjectModel.RegistrationSet set:project.registrationSets){out.append("RS\t").append(e(set.id)).append('\t').append(e(set.name)).append('\t').append(set.createdAt).append('\t').append(set.modifiedAt).append('\t').append(set.stationIds.size());for(String stationId:set.stationIds)out.append('\t').append(e(stationId));out.append('\n');}
        for(ProjectModel.RegistrationLink link:project.registrationLinks){out.append("RL\t").append(e(link.id)).append('\t').append(e(link.movingStationId)).append('\t').append(e(link.referenceStationId));for(float value:link.relativeTransform)out.append('\t').append(value);appendMetrics(out,link.metrics);out.append('\t').append(link.source.name()).append('\t').append(link.acceptedAt).append('\n');}
        return out.toString();
    }

    private static void writeNode(StringBuilder out,ProjectModel.Node node,String parent){
        out.append(node.isGroup()?"G":"S").append('\t').append(e(node.id)).append('\t').append(e(parent)).append('\t').append(e(node.name)).append('\t').append(node.visible).append('\t').append(node.expanded);for(float v:node.transform)out.append('\t').append(v);
        if(node instanceof ProjectModel.Scan){ProjectModel.Scan s=(ProjectModel.Scan)node;out.append('\t').append(e(s.uri)).append('\t').append(s.color).append('\t').append(s.sourcePointCount).append('\t').append(s.embeddedPoseValid).append('\t').append(s.embeddedPoseApplied);for(float value:s.embeddedPose)out.append('\t').append(value);ProjectModel.RegistrationState persisted=s.registrationState==ProjectModel.RegistrationState.REGISTERING?ProjectModel.RegistrationState.PENDING:s.registrationState;out.append('\t').append(s.acquiredAt).append('\t').append(persisted.name()).append('\t').append(e(s.attemptedReferenceId)).append('\t').append(e(s.registrationMessage));appendMetrics(out,s.registrationMetrics);out.append('\t').append(s.pendingCandidateValid);for(float value:s.pendingCandidateWorld)out.append('\t').append(value);out.append('\t').append(s.sourceType.name());}
        out.append('\n');if(node instanceof ProjectModel.Group)for(ProjectModel.Node child:((ProjectModel.Group)node).children())writeNode(out,child,node.id);
    }

    private static void appendMetrics(StringBuilder out,ProjectModel.RegistrationMetrics metrics){out.append('\t').append(metrics.rms).append('\t').append(metrics.p95).append('\t').append(metrics.overlap).append('\t').append(metrics.consistency).append('\t').append(metrics.confidence);}

    static ProjectModel decode(String text){
        String[] lines=text.split("\\r?\\n");if(lines.length<2)throw new IllegalArgumentException("unsupported project format");String[] signature=lines[0].split("\\t",-1);if(signature.length!=2||!"TZF_PROJECT".equals(signature[0]))throw new IllegalArgumentException("unsupported project format");int version=num(signature[1]);if(version<1||version>6)throw new IllegalArgumentException("unsupported project format");
        boolean v6=version==6,v5=version==5,v4=version==4,v3=version==3,v2=version==2;String[] p=lines[1].split("\\t",-1);int expected=(v6||v5||v4||v3)?22:v2?14:12;if(p.length!=expected||!"P".equals(p[0]))throw new IllegalArgumentException("invalid project header");
        ProjectModel project=new ProjectModel(d(p[1]),d(p[2]),lng(p[3]));project.modifiedAt=lng(p[4]);project.pointBudget=num(p[5]);project.pointSize=num(p[6]);project.cameraYaw=flt(p[7]);project.cameraPitch=flt(p[8]);project.cameraZoom=flt(p[9]);project.orthographic=v6?bool(p[10]):true;project.gridVisible=bool(p[11]);
        if(v2||v3||v4){project.referenceNodeId=d(p[12]);project.movingNodeId=d(p[13]);}if(v3||v4||v5||v6){project.clipEnabled=bool(p[14]);project.clipLocked=bool(p[15]);for(int i=0;i<6;i++)project.clipBounds[i]=flt(p[16+i]);if(!ClipBoxMath.valid(project.clipBounds)){project.clipEnabled=false;project.clipLocked=false;java.util.Arrays.fill(project.clipBounds,0);}}

        Map<String,ProjectModel.Node> nodes=new LinkedHashMap<>();Map<String,String> parents=new LinkedHashMap<>();List<String[]> setRecords=new ArrayList<>(),linkRecords=new ArrayList<>();
        for(int i=2;i<lines.length;i++){
            if(lines[i].isEmpty())continue;String[] a=lines[i].split("\\t",-1);
            if("RS".equals(a[0])){if(v5||v6)setRecords.add(a);continue;}if("RL".equals(a[0])){if(v5||v6)linkRecords.add(a);continue;}
            int shift=version>=2?1:0;if(a.length<9+shift)throw new IllegalArgumentException("invalid node");ProjectModel.Node node;
            if("G".equals(a[0])&&a.length==9+shift)node=new ProjectModel.Group(d(a[1]),d(a[3]));
            else if("S".equals(a[0])&&a.length==(v6?34:v5?33:v4?19:12+shift)){ProjectModel.Scan s=new ProjectModel.Scan(d(a[1]),d(a[3]));s.uri=d(a[9+shift]);s.color=num(a[10+shift]);s.sourcePointCount=lng(a[11+shift]);if(v4||v5||v6){s.embeddedPoseValid=bool(a[13]);s.embeddedPoseApplied=bool(a[14]);for(int j=0;j<4;j++)s.embeddedPose[j]=flt(a[15+j]);if(!s.embeddedPoseValid)java.util.Arrays.fill(s.embeddedPose,0);}else s.embeddedPoseApplied=true;if(v5||v6){s.acquiredAt=lng(a[19]);s.registrationState=registrationState(a[20]);if(s.registrationState==ProjectModel.RegistrationState.REGISTERING)s.registrationState=ProjectModel.RegistrationState.PENDING;s.attemptedReferenceId=d(a[21]);s.registrationMessage=d(a[22]);s.registrationMetrics=metrics(a,23);s.pendingCandidateValid=bool(a[28]);for(int j=0;j<4;j++)s.pendingCandidateWorld[j]=flt(a[29+j]);if(!s.pendingCandidateValid)java.util.Arrays.fill(s.pendingCandidateWorld,0);}if(v6){try{s.sourceType=ProjectModel.SourceType.valueOf(a[33]);}catch(Exception error){throw new IllegalArgumentException("invalid source type",error);}}else s.sourceType=PointCloudSources.infer(s.name);node=s;}
            else throw new IllegalArgumentException("invalid node type");
            node.visible=bool(a[4]);node.expanded=version<2||bool(a[5]);for(int j=0;j<4;j++)node.transform[j]=flt(a[5+shift+j]);if(nodes.put(node.id,node)!=null)throw new IllegalArgumentException("duplicate id");parents.put(node.id,d(a[2]));
        }
        for(ProjectModel.Node node:nodes.values()){String parentId=parents.get(node.id);ProjectModel.Group parent="root".equals(parentId)?project.root:(nodes.get(parentId) instanceof ProjectModel.Group?(ProjectModel.Group)nodes.get(parentId):null);if(parent==null)throw new IllegalArgumentException("orphan node");parent.add(node);}

        RegistrationGraph graph=new RegistrationGraph(project);
        if(v5||v6){for(String[] a:setRecords)readSet(project,a);for(String[] a:linkRecords)readLink(project,a);project.referenceNodeId="";project.movingNodeId="";graph.validate();}
        else{if(project.findNode(project.referenceNodeId)==null)project.referenceNodeId="";if(project.findNode(project.movingNodeId)==null)project.movingNodeId="";graph.migrateLegacyScans();graph.validate();}
        return project;
    }

    private static void readSet(ProjectModel project,String[] a){if(a.length<6)throw new IllegalArgumentException("invalid registration set");int count=num(a[5]);if(count<1||a.length!=6+count)throw new IllegalArgumentException("invalid registration set");ProjectModel.RegistrationSet set=new ProjectModel.RegistrationSet(d(a[1]),d(a[2]),lng(a[3]));set.modifiedAt=lng(a[4]);for(int i=0;i<count;i++)set.stationIds.add(d(a[6+i]));project.registrationSets.add(set);}
    private static void readLink(ProjectModel project,String[] a){if(a.length!=15)throw new IllegalArgumentException("invalid registration link");float[] relative=new float[4];for(int i=0;i<4;i++)relative[i]=flt(a[4+i]);ProjectModel.RegistrationMetrics metrics=metrics(a,8);ProjectModel.LinkSource source;try{source=ProjectModel.LinkSource.valueOf(a[13]);}catch(Exception error){throw new IllegalArgumentException("invalid registration link source",error);}project.registrationLinks.add(new ProjectModel.RegistrationLink(d(a[1]),d(a[2]),d(a[3]),relative,metrics,source,lng(a[14])));}
    private static ProjectModel.RegistrationMetrics metrics(String[] values,int offset){return new ProjectModel.RegistrationMetrics(flt(values[offset]),flt(values[offset+1]),flt(values[offset+2]),flt(values[offset+3]),flt(values[offset+4]));}
    private static ProjectModel.RegistrationState registrationState(String value){try{return ProjectModel.RegistrationState.valueOf(value);}catch(Exception error){throw new IllegalArgumentException("invalid registration state",error);}}
    private static boolean bool(String value){if(!"true".equals(value)&&!"false".equals(value))throw new IllegalArgumentException("invalid boolean");return Boolean.parseBoolean(value);}
    private static String e(String value){return Base64.getUrlEncoder().withoutPadding().encodeToString((value==null?"":value).getBytes(StandardCharsets.UTF_8));}
    private static String d(String value){try{return new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8);}catch(Exception error){throw new IllegalArgumentException("invalid text field",error);}}
    private static int num(String v){return Integer.parseInt(v);}private static long lng(String v){return Long.parseLong(v);}private static float flt(String v){float f=Float.parseFloat(v);if(!Float.isFinite(f))throw new IllegalArgumentException("non-finite value");return f;}
}
