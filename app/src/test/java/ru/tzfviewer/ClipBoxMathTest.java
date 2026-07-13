package ru.tzfviewer;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class ClipBoxMathTest {
    @Test public void centralSeventyPercentIgnoresDistantOutliers(){float[] points=new float[303];for(int i=0;i<100;i++){points[i*3]=i;points[i*3+1]=i*2;points[i*3+2]=-i;}points[300]=100000;points[301]=-100000;points[302]=500000;float[] bounds=ClipBoxMath.centralBounds(points,.15f,.85f);assertTrue(bounds[0]>10&&bounds[3]<90);assertTrue(bounds[1]>20&&bounds[4]<180);assertTrue(bounds[2]>-90&&bounds[5]<-10);}

    @Test public void recenterKeepsSizeAndFacesCannotCross(){float[] initial={0,2,4,10,12,14};float[] moved=ClipBoxMath.recentered(initial,100,200,300);assertArrayEquals(new float[]{95,195,295,105,205,305},moved,.001f);ClipBoxMath.moveFace(moved,0,200,2);assertArrayEquals(new float[]{103,195,295,105,205,305},moved,.001f);ClipBoxMath.moveFace(moved,5,290,2);assertArrayEquals(new float[]{103,195,295,105,205,297},moved,.001f);}
}
