package ru.tzfviewer;
import org.junit.Test;import static org.junit.Assert.*;
public class SceneCloudRegistryTest{
 @Test public void syncFlattensGroupsAndKeepsWorldTransforms(){ProjectModel p=new ProjectModel("p","p",1);ProjectModel.Group g=new ProjectModel.Group("g","g");g.transform[0]=10;ProjectModel.Scan a=new ProjectModel.Scan("a","a"),b=new ProjectModel.Scan("b","b");a.sourcePointCount=1_000_000;b.sourcePointCount=2_000_000;a.transform[0]=2;p.root.add(g);g.add(a);g.add(b);SceneCloudRegistry r=new SceneCloudRegistry();r.sync(p);assertEquals(2,r.all().size());assertEquals(12,r.get("a").worldTransform[0],.001);assertEquals(300_000,r.allocate(300_000));}
 @Test public void removedAndHiddenScansLeaveNoQuota(){ProjectModel p=new ProjectModel("p","p",1);ProjectModel.Scan a=new ProjectModel.Scan("a","a");a.sourcePointCount=1_000_000;a.visible=false;p.root.add(a);SceneCloudRegistry r=new SceneCloudRegistry();r.sync(p);assertEquals(0,r.allocate(300_000));p.root.remove(a);r.sync(p);assertNull(r.get("a"));}
}
