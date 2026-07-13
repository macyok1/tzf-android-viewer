package ru.tzfviewer;

import java.util.Arrays;

final class PointReservoir {
    private final int limit;
    private float[] xyz=new float[0];
    private int count;
    private long seen;

    PointReservoir(int limit){if(limit<=0)throw new IllegalArgumentException("invalid reservoir limit");this.limit=limit;}
    int size(){return count;}
    float x(int index){return xyz[index*3];}float y(int index){return xyz[index*3+1];}float z(int index){return xyz[index*3+2];}
    void clear(){count=0;seen=0;}
    void add(float[] points){for(int i=0;i<points.length;i+=3)add(points[i],points[i+1],points[i+2]);}
    private void add(float x,float y,float z){long position=seen++;int target;if(count<limit){target=count++;ensure(count*3);}else{long mixed=mix(position+1),candidate=Long.remainderUnsigned(mixed,position+1);if(candidate>=limit)return;target=(int)candidate;}int offset=target*3;xyz[offset]=x;xyz[offset+1]=y;xyz[offset+2]=z;}
    float[] copy(){return Arrays.copyOf(xyz,count*3);}
    private void ensure(int required){if(required<=xyz.length)return;int capacity=Math.min(limit*3,Math.max(required,Math.max(3072,xyz.length*2)));xyz=Arrays.copyOf(xyz,capacity);}
    private static long mix(long value){value^=value>>>33;value*=0xff51afd7ed558ccdL;value^=value>>>33;value*=0xc4ceb9fe1a85ec53L;return value^(value>>>33);}
}
