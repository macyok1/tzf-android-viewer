package ru.tzfviewer;

import org.junit.Test;
import static org.junit.Assert.*;

public class AscExportMathTest {
    @Test public void lockedCutUsesInclusiveWorldBounds(){float[] b={0,0,0,10,20,30};assertTrue(AscExportMath.insideCut(0,20,15,b));assertFalse(AscExportMath.insideCut(-.01f,5,5,b));assertTrue(AscExportMath.insideCut(-999,999,0,null));}
    @Test public void spatialFilterKeepsOnlyOnePointPerGlobalVoxel(){AscExportMath.SpatialFilter filter=new AscExportMath.SpatialFilter(10);assertTrue(filter.accept(1,2,3));assertFalse(filter.accept(9,9,9));assertTrue(filter.accept(10,0,0));}
    @Test public void estimatedSpacingApproachesRequestedCount(){float[] sample=new float[300];for(int i=0;i<100;i++){sample[i*3]=i*10;sample[i*3+1]=(i%10)*10;}double spacing=AscExportMath.estimateSpacing(sample,1000,100);long estimate=AscExportMath.estimatedCount(sample,1000,spacing);assertTrue(spacing>0);assertTrue(Math.abs(estimate-100)<=20);}
}
