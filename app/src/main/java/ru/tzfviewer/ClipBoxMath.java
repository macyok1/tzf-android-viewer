package ru.tzfviewer;

import java.util.Arrays;

final class ClipBoxMath {
    private ClipBoxMath() {}

    static boolean valid(float[] bounds){return bounds!=null&&bounds.length==6&&Float.isFinite(bounds[0])&&Float.isFinite(bounds[1])&&Float.isFinite(bounds[2])&&Float.isFinite(bounds[3])&&Float.isFinite(bounds[4])&&Float.isFinite(bounds[5])&&bounds[0]<bounds[3]&&bounds[1]<bounds[4]&&bounds[2]<bounds[5];}

    static float[] centralBounds(float[] xyz,float lowerFraction,float upperFraction){
        if(xyz==null||xyz.length<6||xyz.length%3!=0)throw new IllegalArgumentException("invalid XYZ sample");
        int count=xyz.length/3;float[] result=new float[6];
        for(int axis=0;axis<3;axis++){float[] values=new float[count];int n=0;for(int i=axis;i<xyz.length;i+=3)if(Float.isFinite(xyz[i]))values[n++]=xyz[i];if(n<2)throw new IllegalArgumentException("not enough finite points");Arrays.sort(values,0,n);result[axis]=percentile(values,n,lowerFraction);result[axis+3]=percentile(values,n,upperFraction);if(result[axis]>=result[axis+3]){float center=result[axis],epsilon=Math.max(.001f,Math.abs(center)*1e-5f);result[axis]=center-epsilon;result[axis+3]=center+epsilon;}}
        return result;
    }

    static float[] recentered(float[] bounds,float x,float y,float z){if(!valid(bounds))throw new IllegalArgumentException("invalid bounds");float[] result=bounds.clone();float[] center={x,y,z};for(int axis=0;axis<3;axis++){float half=(bounds[axis+3]-bounds[axis])*.5f;result[axis]=center[axis]-half;result[axis+3]=center[axis]+half;}return result;}

    static void moveFace(float[] bounds,int face,float value,float minimumThickness){if(!valid(bounds)||face<0||face>5)return;int axis=face%3;float minimum=Math.max(.0001f,minimumThickness);if(face<3)bounds[axis]=Math.min(value,bounds[axis+3]-minimum);else bounds[axis+3]=Math.max(value,bounds[axis]+minimum);}

    private static float percentile(float[] sorted,int count,float fraction){float position=Math.max(0,Math.min(1,fraction))*(count-1),lo=(float)Math.floor(position),weight=position-(int)lo;int index=(int)lo;return sorted[index]+(sorted[Math.min(count-1,index+1)]-sorted[index])*weight;}
}
