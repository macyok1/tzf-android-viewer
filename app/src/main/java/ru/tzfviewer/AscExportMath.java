package ru.tzfviewer;

import java.util.HashSet;
import java.util.Set;

final class AscExportMath {
    private AscExportMath(){}
    static boolean insideCut(float x,float y,float z,float[] bounds){return bounds==null||(x>=bounds[0]&&x<=bounds[3]&&y>=bounds[1]&&y<=bounds[4]&&z>=bounds[2]&&z<=bounds[5]);}
    static double estimateSpacing(float[] sample,long eligible,long target){if(sample==null||sample.length<3||eligible<=0||target<=0||target>=eligible)return 0;float[] bounds=SceneBounds.of(sample);double high=Math.max(1,Math.max(bounds[3]-bounds[0],Math.max(bounds[4]-bounds[1],bounds[5]-bounds[2]))),low=0;for(int expand=0;expand<12&&estimatedCount(sample,eligible,high)>target;expand++)high*=2;for(int i=0;i<28;i++){double mid=(low+high)*.5;if(estimatedCount(sample,eligible,mid)>target)low=mid;else high=mid;}return high;}
    static long estimatedCount(float[] sample,long eligible,double spacing){if(spacing<=0)return eligible;Set<Voxel> occupied=new HashSet<>();for(int i=0;i<sample.length;i+=3)occupied.add(new Voxel(sample[i],sample[i+1],sample[i+2],spacing));return Math.max(1,Math.round(eligible*(occupied.size()/(sample.length/3d))));}
    static final class SpatialFilter {private final double spacing;private final Set<Voxel> occupied=new HashSet<>();SpatialFilter(double spacing){this.spacing=spacing;}boolean accept(float x,float y,float z){return spacing<=0||occupied.add(new Voxel(x,y,z,spacing));}}
    private static final class Voxel {final long x,y,z;Voxel(float x,float y,float z,double spacing){this.x=(long)Math.floor(x/spacing);this.y=(long)Math.floor(y/spacing);this.z=(long)Math.floor(z/spacing);}@Override public boolean equals(Object other){if(!(other instanceof Voxel))return false;Voxel v=(Voxel)other;return x==v.x&&y==v.y&&z==v.z;}@Override public int hashCode(){long h=x*73856093L^y*19349663L^z*83492791L;return (int)(h^(h>>>32));}}
}
