package ru.tzfviewer;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenPanMathTest {
    @Test public void verticalDragFollowsScreenUpAtAnyView(){
        assertScreenDelta(25,-18,0,100);
        assertScreenDelta(120,-55,0,100);
        assertScreenDelta(-70,-89,0,100);
    }
    @Test public void horizontalDragFollowsScreenRightAtAnyView(){
        assertScreenDelta(25,-18,100,0);
        assertScreenDelta(170,40,100,0);
    }
    private static void assertScreenDelta(float yaw,float pitch,float dx,float dy){
        float[] delta=ScreenPanMath.delta(dx,dy,1,yaw,pitch);double yr=Math.toRadians(yaw),pr=Math.toRadians(pitch);float rx=(float)Math.cos(yr),ry=(float)Math.sin(yr),fx=-(float)(Math.cos(pr)*Math.sin(yr)),fy=(float)(Math.cos(pr)*Math.cos(yr)),fz=(float)Math.sin(pr),ux=ry*fz,uy=-rx*fz,uz=rx*fy-ry*fx;float screenX=-(delta[0]*rx+delta[1]*ry),screenY=delta[0]*ux+delta[1]*uy+delta[2]*uz;assertEquals(dx,screenX,1e-3);assertEquals(dy,screenY,1e-3);
    }
}
