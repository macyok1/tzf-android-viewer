package ru.tzfviewer;

final class ScreenPanMath {
    private ScreenPanMath(){}
    static float[] delta(float dx,float dy,float unitsPerPixel,float yaw,float pitch){
        double yr=Math.toRadians(yaw),pr=Math.toRadians(pitch);
        float rightX=(float)Math.cos(yr),rightY=(float)Math.sin(yr);
        float forwardX=-(float)(Math.cos(pr)*Math.sin(yr));
        float forwardY=(float)(Math.cos(pr)*Math.cos(yr));
        float forwardZ=(float)Math.sin(pr);
        float upX=rightY*forwardZ,upY=-rightX*forwardZ;
        float upZ=rightX*forwardY-rightY*forwardX;
        return new float[]{(-rightX*dx+upX*dy)*unitsPerPixel,(-rightY*dx+upY*dy)*unitsPerPixel,upZ*dy*unitsPerPixel};
    }
}
