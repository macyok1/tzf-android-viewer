package ru.tzfviewer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ProjectStore {
    private final File directory;
    ProjectStore(File filesDir){directory=new File(filesDir,"projects");}
    List<ProjectModel> list(){List<ProjectModel> result=new ArrayList<>();File[] files=directory.listFiles((d,n)->n.endsWith(".tzfp"));if(files!=null)for(File file:files)try{result.add(loadFile(file));}catch(Exception ignored){}result.sort(Comparator.comparingLong((ProjectModel p)->p.modifiedAt).reversed());return result;}
    ProjectModel load(String id)throws IOException{return loadFile(file(id));}
    void save(ProjectModel project)throws IOException{
        if(!directory.exists()&&!directory.mkdirs())throw new IOException("cannot create projects directory");
        File target=file(project.id),temp=new File(directory,project.id+".tmp"),backup=new File(directory,project.id+".bak");
        Files.write(temp.toPath(),ProjectCodec.encode(project).getBytes(StandardCharsets.UTF_8));
        if(target.exists())Files.copy(target.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING);
        try{Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(IOException unsupported){Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    }
    void delete(String id)throws IOException{Files.deleteIfExists(file(id).toPath());Files.deleteIfExists(new File(directory,id+".bak").toPath());}
    ProjectModel copy(ProjectModel source,String newName,long now)throws IOException{ProjectModel copy=ProjectCodec.decode(ProjectCodec.encode(source));ProjectModel created=new ProjectModel(java.util.UUID.randomUUID().toString(),newName,now);created.pointBudget=copy.pointBudget;created.pointSize=copy.pointSize;created.cameraYaw=copy.cameraYaw;created.cameraPitch=copy.cameraPitch;created.cameraZoom=copy.cameraZoom;created.orthographic=copy.orthographic;created.gridVisible=copy.gridVisible;created.referenceNodeId=copy.referenceNodeId;created.movingNodeId=copy.movingNodeId;for(ProjectModel.Node child:new ArrayList<>(copy.root.children()))created.root.reparentPreservingWorld(child);save(created);return created;}
    private ProjectModel loadFile(File file)throws IOException{return ProjectCodec.decode(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));}
    private File file(String id){if(!id.matches("[A-Za-z0-9-]+"))throw new IllegalArgumentException("invalid project id");return new File(directory,id+".tzfp");}
}
