package ru.tzfviewer;
import java.util.*;
final class PreviewLevelCache{
 private final NavigableMap<Integer,float[]> levels=new TreeMap<>();
 synchronized void put(float[] xyz){if(xyz==null||xyz.length%3!=0)return;levels.put(xyz.length/3,xyz);trim();}
 synchronized float[] get(int points){Map.Entry<Integer,float[]> e=levels.ceilingEntry(points);if(e==null)return null;if(e.getKey()==points)return e.getValue();float[] source=e.getValue(),out=new float[Math.min(points,e.getKey())*3];double step=(double)e.getKey()/(out.length/3);for(int i=0;i<out.length/3;i++){int s=Math.min(e.getKey()-1,(int)Math.floor(i*step))*3;System.arraycopy(source,s,out,i*3,3);}return out;}
 synchronized int largest(){return levels.isEmpty()?0:levels.lastKey();}
 private void trim(){while(levels.size()>3){Integer first=levels.firstKey(),last=levels.lastKey();Integer remove=levels.higherKey(first);if(remove==null||remove.equals(last))remove=first;levels.remove(remove);}}
}
