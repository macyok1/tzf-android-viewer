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
        StringBuilder out=new StringBuilder("TZF_PROJECT\t1\n");
        out.append("P\t").append(e(project.id)).append('\t').append(e(project.name)).append('\t').append(project.createdAt).append('\t').append(project.modifiedAt).append('\t').append(project.pointBudget).append('\t').append(project.pointSize).append('\t').append(project.cameraYaw).append('\t').append(project.cameraPitch).append('\t').append(project.cameraZoom).append('\t').append(project.orthographic).append('\t').append(project.gridVisible).append('\n');
        for(ProjectModel.Node child:project.root.children())writeNode(out,child,"root");
        return out.toString();
    }
    private static void writeNode(StringBuilder out,ProjectModel.Node node,String parent){
        out.append(node.isGroup()?"G":"S").append('\t').append(e(node.id)).append('\t').append(e(parent)).append('\t').append(e(node.name)).append('\t').append(node.visible);
        for(float v:node.transform)out.append('\t').append(v);
        if(node instanceof ProjectModel.Scan){ProjectModel.Scan s=(ProjectModel.Scan)node;out.append('\t').append(e(s.uri)).append('\t').append(s.color).append('\t').append(s.sourcePointCount);}
        out.append('\n');
        if(node instanceof ProjectModel.Group)for(ProjectModel.Node child:((ProjectModel.Group)node).children())writeNode(out,child,node.id);
    }
    static ProjectModel decode(String text){
        String[] lines=text.split("\\r?\\n");if(lines.length<2||!"TZF_PROJECT\t1".equals(lines[0]))throw new IllegalArgumentException("unsupported project format");
        String[] p=lines[1].split("\\t",-1);if(p.length!=12||!"P".equals(p[0]))throw new IllegalArgumentException("invalid project header");
        ProjectModel project=new ProjectModel(d(p[1]),d(p[2]),lng(p[3]));project.modifiedAt=lng(p[4]);project.pointBudget=num(p[5]);project.pointSize=num(p[6]);project.cameraYaw=flt(p[7]);project.cameraPitch=flt(p[8]);project.cameraZoom=flt(p[9]);project.orthographic=Boolean.parseBoolean(p[10]);project.gridVisible=Boolean.parseBoolean(p[11]);
        Map<String,ProjectModel.Node> nodes=new LinkedHashMap<>();Map<String,String> parents=new LinkedHashMap<>();
        for(int i=2;i<lines.length;i++){if(lines[i].isEmpty())continue;String[] a=lines[i].split("\\t",-1);if(a.length<9)throw new IllegalArgumentException("invalid node");ProjectModel.Node node;if("G".equals(a[0]))node=new ProjectModel.Group(d(a[1]),d(a[3]));else if("S".equals(a[0])&&a.length==12){ProjectModel.Scan s=new ProjectModel.Scan(d(a[1]),d(a[3]));s.uri=d(a[9]);s.color=num(a[10]);s.sourcePointCount=lng(a[11]);node=s;}else throw new IllegalArgumentException("invalid node type");node.visible=Boolean.parseBoolean(a[4]);for(int j=0;j<4;j++)node.transform[j]=flt(a[5+j]);if(nodes.put(node.id,node)!=null)throw new IllegalArgumentException("duplicate id");parents.put(node.id,d(a[2]));}
        List<ProjectModel.Node> pending=new ArrayList<>(nodes.values());int previous=-1;while(!pending.isEmpty()&&pending.size()!=previous){previous=pending.size();for(int i=pending.size()-1;i>=0;i--){ProjectModel.Node node=pending.get(i);String parentId=parents.get(node.id);ProjectModel.Group parent="root".equals(parentId)?project.root:(nodes.get(parentId) instanceof ProjectModel.Group?(ProjectModel.Group)nodes.get(parentId):null);if(parent!=null){parent.add(node);pending.remove(i);}}}if(!pending.isEmpty())throw new IllegalArgumentException("orphan or cyclic nodes");return project;
    }
    private static String e(String value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    private static String d(String value){return new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8);}
    private static int num(String v){return Integer.parseInt(v);}private static long lng(String v){return Long.parseLong(v);}private static float flt(String v){float f=Float.parseFloat(v);if(!Float.isFinite(f))throw new IllegalArgumentException("non-finite value");return f;}
}
