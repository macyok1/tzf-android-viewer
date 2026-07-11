package ru.tzfviewer;
import java.util.*;
final class SceneCloudRegistry{
 static final class Cloud{final String id;long available;boolean visible=true,active;float screenWeight=1;int quota;final float[] worldTransform=new float[4];Cloud(String id){this.id=id;}}
 private final LinkedHashMap<String,Cloud> clouds=new LinkedHashMap<>();
 Cloud upsert(String id){Cloud c=clouds.get(id);if(c==null){c=new Cloud(id);clouds.put(id,c);}return c;}
 void remove(String id){clouds.remove(id);}Cloud get(String id){return clouds.get(id);}Collection<Cloud> all(){return Collections.unmodifiableCollection(clouds.values());}
 int allocate(int budget){List<PointBudgetAllocator.Item> items=new ArrayList<>();for(Cloud c:clouds.values())items.add(new PointBudgetAllocator.Item(c.id,c.available,c.screenWeight,c.visible,c.active));Map<String,Integer> result=PointBudgetAllocator.allocate(budget,items);int total=0;for(Cloud c:clouds.values()){c.quota=result.get(c.id);total+=c.quota;}return total;}
 void sync(ProjectModel project){Set<String> live=new HashSet<>();syncGroup(project.root,live);clouds.keySet().retainAll(live);}
 private void syncGroup(ProjectModel.Group group,Set<String> live){for(ProjectModel.Node node:group.children())if(node instanceof ProjectModel.Scan){live.add(node.id);Cloud c=upsert(node.id);c.visible=node.visible;c.available=((ProjectModel.Scan)node).sourcePointCount;float[] world=node.worldTransform();System.arraycopy(world,0,c.worldTransform,0,4);}else syncGroup((ProjectModel.Group)node,live);}
}
