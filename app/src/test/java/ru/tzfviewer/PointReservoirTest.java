package ru.tzfviewer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PointReservoirTest {
    @Test public void capsMemoryAndKeepsPointsAcrossInput(){PointReservoir reservoir=new PointReservoir(100);float[] points=new float[30_000];for(int i=0;i<10_000;i++){points[i*3]=i;points[i*3+1]=i*2;points[i*3+2]=-i;}reservoir.add(points);assertEquals(100,reservoir.size());float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;for(int i=0;i<reservoir.size();i++){min=Math.min(min,reservoir.x(i));max=Math.max(max,reservoir.x(i));}assertTrue(min<4000);assertTrue(max>6000);assertEquals(300,reservoir.copy().length);reservoir.clear();assertEquals(0,reservoir.size());}
}
