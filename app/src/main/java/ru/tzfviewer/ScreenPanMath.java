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
    static float[] fromInverseViewProjection(float dx,float dy,int width,int height,float ndcZ,float[] inverse){
        if(width<=0||height<=0||inverse==null||inverse.length!=16)return new float[3];
        float[] before=unproject(0,0,ndcZ,inverse),after=unproject(2f*dx/width,-2f*dy/height,ndcZ,inverse);
        if(before==null||after==null)return new float[3];
        return new float[]{before[0]-after[0],before[1]-after[1],before[2]-after[2]};
    }
    private static float[] unproject(float x,float y,float z,float[] m){
        float[] out=new float[4],v={x,y,z,1};
        for(int row=0;row<4;row++)out[row]=m[row]*v[0]+m[4+row]*v[1]+m[8+row]*v[2]+m[12+row]*v[3];
        if(!Float.isFinite(out[3])||Math.abs(out[3])<1e-7f)return null;
        return new float[]{out[0]/out[3],out[1]/out[3],out[2]/out[3]};
    }
}
