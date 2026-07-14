package ru.tzfviewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class ProjectModel {
    static final int FORMAT_VERSION = 4;
    final String id;
    String name;
    long createdAt;
    long modifiedAt;
    int pointBudget = -1;
    int pointSize = 2;
    float cameraYaw = 25f, cameraPitch = -18f, cameraZoom = 1f;
    boolean orthographic, gridVisible = true;
    boolean clipEnabled, clipLocked;
    final float[] clipBounds = new float[6];
    String referenceNodeId = "", movingNodeId = "";
    final Group root;

    ProjectModel(String id, String name, long now) {
        this.id = id; this.name = name; createdAt = modifiedAt = now;
        root = new Group("root", "Сцена");
    }

    static ProjectModel create(String name, long now) {
        return new ProjectModel(UUID.randomUUID().toString(), name, now);
    }

    int scanCount() { return root.scanCount(); }
    void touch(long now) { modifiedAt = Math.max(modifiedAt, now); }
    Node findNode(String id) { return id == null || id.isEmpty() ? null : findNode(root,id); }
    private static Node findNode(Group group,String id){for(Node child:group.children){if(child.id.equals(id))return child;if(child instanceof Group){Node found=findNode((Group)child,id);if(found!=null)return found;}}return null;}
    boolean canRegister(){Node r=findNode(referenceNodeId),m=findNode(movingNodeId);return r!=null&&m!=null&&r!=m&&!isAncestor(r,m)&&!isAncestor(m,r);}
    void setReference(Node node){Node moving=findNode(movingNodeId);if(node!=null&&(node==moving||isAncestor(node,moving)||isAncestor(moving,node)))movingNodeId="";referenceNodeId=node==null?"":node.id;}
    void setMoving(Node node){Node reference=findNode(referenceNodeId);if(node!=null&&(node==reference||isAncestor(node,reference)||isAncestor(reference,node)))referenceNodeId="";movingNodeId=node==null?"":node.id;}
    void clearRoleFor(Node node){if(node==null)return;if(node.id.equals(referenceNodeId)||isAncestor(node,findNode(referenceNodeId)))referenceNodeId="";if(node.id.equals(movingNodeId)||isAncestor(node,findNode(movingNodeId)))movingNodeId="";}
    static boolean isAncestor(Node ancestor,Node node){if(ancestor==null||node==null)return false;for(Group p=node.parent();p!=null;p=p.parent())if(p==ancestor)return true;return false;}

    abstract static class Node {
        final String id;
        String name;
        boolean visible = true;
        boolean expanded = true;
        final float[] transform = new float[4];
        private Group parent;
        Node(String id, String name) { this.id=id; this.name=name; }
        Group parent() { return parent; }
        abstract boolean isGroup();
        abstract int scanCount();
        float[] worldTransform() {
            if (parent == null || "root".equals(id)) return transform.clone();
            return compose(parent.worldTransform(), transform);
        }
    }

    static final class Scan extends Node {
        static final int WAITING=0,LOADING=1,READY=2,ERROR=3;
        String uri = "";
        int color = 0xff38c9e8;
        long sourcePointCount;
        boolean embeddedPoseValid;
        boolean embeddedPoseApplied;
        final float[] embeddedPose = new float[4];
        transient int loadState=WAITING;
        transient String loadError="";
        Scan(String id, String name) { super(id,name); }
        @Override boolean isGroup(){return false;}
        @Override int scanCount(){return 1;}
    }

    static final class Group extends Node {
        private final List<Node> children = new ArrayList<>();
        Group(String id,String name){super(id,name);}
        @Override boolean isGroup(){return true;}
        @Override int scanCount(){int count=0;for(Node child:children)count+=child.scanCount();return count;}
        List<Node> children(){return Collections.unmodifiableList(children);}
        void add(Node node){
            if(node==this || (node instanceof Group && ((Group)node).contains(this)))throw new IllegalArgumentException("cycle");
            if(node.parent!=null)throw new IllegalStateException("node already has parent");
            children.add(node); node.parent=this;
        }
        void remove(Node node){if(children.remove(node))node.parent=null;}
        boolean contains(Node target){if(this==target)return true;for(Node n:children)if(n==target||(n instanceof Group&&((Group)n).contains(target)))return true;return false;}
        void reparentPreservingWorld(Node node){
            float[] world=node.worldTransform();
            if(node.parent!=null)node.parent.remove(node);
            float[] local=relative(worldTransform(),world);
            System.arraycopy(local,0,node.transform,0,4); add(node);
        }
        void dissolveIntoParent(){
            if(parent()==null)throw new IllegalStateException("root cannot be dissolved");
            Group destination=parent(); List<Node> moving=new ArrayList<>(children);
            for(Node child:moving)destination.reparentPreservingWorld(child);
            destination.remove(this);
        }
    }

    static float[] compose(float[] parent,float[] local){
        double r=Math.toRadians(parent[3]);float c=(float)Math.cos(r),s=(float)Math.sin(r);
        return new float[]{parent[0]+c*local[0]-s*local[1],parent[1]+s*local[0]+c*local[1],parent[2]+local[2],ViewCubeMath.normalizeYaw(parent[3]+local[3])};
    }

    static float[] relative(float[] parentWorld,float[] childWorld){
        float dx=childWorld[0]-parentWorld[0],dy=childWorld[1]-parentWorld[1];double r=Math.toRadians(-parentWorld[3]);float c=(float)Math.cos(r),s=(float)Math.sin(r);
        return new float[]{c*dx-s*dy,s*dx+c*dy,childWorld[2]-parentWorld[2],ViewCubeMath.normalizeYaw(childWorld[3]-parentWorld[3])};
    }

    boolean initializeEmbeddedPose(Scan scan,float[] pose){
        if(scan==null)return false;
        if(scan.embeddedPoseApplied){
            if(scan.embeddedPoseValid||!validPose(pose))return false;
            scan.embeddedPoseValid=true;
            System.arraycopy(pose,0,scan.embeddedPose,0,4);
            return true;
        }
        scan.embeddedPoseApplied=true;
        if(!validPose(pose))return true;
        scan.embeddedPoseValid=true;
        System.arraycopy(pose,0,scan.embeddedPose,0,4);
        Scan anchor=findEmbeddedPoseAnchor(root,scan);
        if(anchor==null){
            if(root.scanCount()==1) return true;
            return true;
        }
        float[] offset=relative(anchor.embeddedPose,scan.embeddedPose);
        float[] requestedWorld=compose(anchor.worldTransform(),offset);
        float[] local=relative(scan.parent().worldTransform(),requestedWorld);
        System.arraycopy(local,0,scan.transform,0,4);
        return true;
    }

    private static Scan findEmbeddedPoseAnchor(Group group,Scan excluded){
        for(Node node:group.children){
            if(node instanceof Scan){Scan scan=(Scan)node;if(scan!=excluded&&scan.embeddedPoseValid&&scan.embeddedPoseApplied)return scan;}
            else {Scan found=findEmbeddedPoseAnchor((Group)node,excluded);if(found!=null)return found;}
        }
        return null;
    }

    private static boolean validPose(float[] pose){
        if(pose==null||pose.length!=4)return false;
        for(float value:pose)if(!Float.isFinite(value))return false;
        return true;
    }

    static boolean informativeEmbeddedOffset(float[] offset){
        return validPose(offset)&&(Math.hypot(offset[0],offset[1])>=20f||
            Math.abs(offset[2])>=20f||Math.abs(offset[3])>=.25f);
    }
}
