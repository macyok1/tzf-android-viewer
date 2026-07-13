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
        StringBuilder out=new StringBuilder("TZF_PROJECT\t3\n");
        out.append("P\t").append(e(project.id)).append('\t').append(e(project.name)).append('\t').append(project.createdAt).append('\t').append(project.modifiedAt).append('\t').append(project.pointBudget).append('\t').append(project.pointSize).append('\t').append(project.cameraYaw).append('\t').append(project.cameraPitch).append('\t').append(project.cameraZoom).append('\t').append(project.orthographic).append('\t').append(project.gridVisible).append('\t').append(e(project.referenceNodeId)).append('\t').append(e(project.movingNodeId)).append('\t').append(project.clipEnabled).append('\t').append(project.clipLocked);for(float value:project.clipBounds)out.append('\t').append(value);out.append('\n');
        for(ProjectModel.Node child:project.root.children())writeNode(out,child,"root");
        return out.toString();
    }
    private static void writeNode(StringBuilder out,ProjectModel.Node node,String parent){
        out.append(node.isGroup()?"G":"S").append('\t').append(e(node.id)).append('\t').append(e(parent)).append('\t').append(e(node.name)).append('\t').append(node.visible).append('\t').append(node.expanded);
        for(float v:node.transform)out.append('\t').append(v);
        if(node instanceof ProjectModel.Scan){ProjectModel.Scan s=(ProjectModel.Scan)node;out.append('\t').append(e(s.uri)).append('\t').append(s.color).append('\t').append(s.sourcePointCount);}
        out.append('\n');
        if(node instanceof ProjectModel.Group)for(ProjectModel.Node child:((ProjectModel.Group)node).children())writeNode(out,child,node.id);
    }
    static ProjectModel decode(String text){
        String[] lines=text.split("\\r?\\n");if(lines.length<2)throw new IllegalArgumentException("unsupported project format");boolean v3="TZF_PROJECT\t3".equals(lines[0]),v2="TZF_PROJECT\t2".equals(lines[0]);if(!v3&&!v2&&!"TZF_PROJECT\t1".equals(lines[0]))throw new IllegalArgumentException("unsupported project format");
        String[] p=lines[1].split("\\t",-1);if((v3?p.length!=22:v2?p.length!=14:p.length!=12)||!"P".equals(p[0]))throw new IllegalArgumentException("invalid project header");
        ProjectModel project=new ProjectModel(d(p[1]),d(p[2]),lng(p[3]));project.modifiedAt=lng(p[4]);project.pointBudget=num(p[5]);project.pointSize=num(p[6]);project.cameraYaw=flt(p[7]);project.cameraPitch=flt(p[8]);project.cameraZoom=flt(p[9]);project.orthographic=Boolean.parseBoolean(p[10]);project.gridVisible=Boolean.parseBoolean(p[11]);
        if(v2||v3){project.referenceNodeId=d(p[12]);project.movingNodeId=d(p[13]);}if(v3){project.clipEnabled=Boolean.parseBoolean(p[14]);project.clipLocked=Boolean.parseBoolean(p[15]);for(int i=0;i<6;i++)project.clipBounds[i]=flt(p[16+i]);if(!ClipBoxMath.valid(project.clipBounds)){project.clipEnabled=false;project.clipLocked=false;java.util.Arrays.fill(project.clipBounds,0);}}
        Map<String,ProjectModel.Node> nodes=new LinkedHashMap<>();Map<String,String> parents=new LinkedHashMap<>();
        for(int i=2;i<lines.length;i++){if(lines[i].isEmpty())continue;String[] a=lines[i].split("\\t",-1);int shift=(v2||v3)?1:0;if(a.length<9+shift)throw new IllegalArgumentException("invalid node");ProjectModel.Node node;if("G".equals(a[0]))node=new ProjectModel.Group(d(a[1]),d(a[3]));else if("S".equals(a[0])&&a.length==12+shift){ProjectModel.Scan s=new ProjectModel.Scan(d(a[1]),d(a[3]));s.uri=d(a[9+shift]);s.color=num(a[10+shift]);s.sourcePointCount=lng(a[11+shift]);node=s;}else throw new IllegalArgumentException("invalid node type");node.visible=Boolean.parseBoolean(a[4]);node.expanded=!(v2||v3)||Boolean.parseBoolean(a[5]);for(int j=0;j<4;j++)node.transform[j]=flt(a[5+shift+j]);if(nodes.put(node.id,node)!=null)throw new IllegalArgumentException("duplicate id");parents.put(node.id,d(a[2]));}
        for(ProjectModel.Node node:nodes.values()){String parentId=parents.get(node.id);ProjectModel.Group parent="root".equals(parentId)?project.root:(nodes.get(parentId) instanceof ProjectModel.Group?(ProjectModel.Group)nodes.get(parentId):null);if(parent==null)throw new IllegalArgumentException("orphan node");parent.add(node);}if(project.findNode(project.referenceNodeId)==null)project.referenceNodeId="";if(project.findNode(project.movingNodeId)==null)project.movingNodeId="";return project;
    }
    private static String e(String value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    private static String d(String value){return new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8);}
    private static int num(String v){return Integer.parseInt(v);}private static long lng(String v){return Long.parseLong(v);}private static float flt(String v){float f=Float.parseFloat(v);if(!Float.isFinite(f))throw new IllegalArgumentException("non-finite value");return f;}
}
