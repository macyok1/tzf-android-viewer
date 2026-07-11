package ru.tzfviewer;
import org.junit.Test;import static org.junit.Assert.*;
public class PreviewLevelCacheTest{
 @Test public void largerLevelServesSmallerWithoutDecode(){PreviewLevelCache c=new PreviewLevelCache();float[] p=new float[300];for(int i=0;i<p.length;i++)p[i]=i;c.put(p);float[] small=c.get(25);assertEquals(75,small.length);assertEquals(0,small[0],0);assertTrue(small[72]>small[3]);}
 @Test public void missingLargerLevelReturnsNull(){PreviewLevelCache c=new PreviewLevelCache();c.put(new float[30]);assertNull(c.get(20));assertEquals(10,c.largest());}
}
