package ru.tzfviewer;
final class AutoPointBudget{
 private static final int[] LEVELS={150_000,300_000,600_000,1_200_000,2_500_000,5_000_000,10_000_000};private int index=3,good,bad;
 int current(){return LEVELS[index];}
 int sample(float fps,boolean memoryPressure){if(memoryPressure||fps<22){bad++;good=0;if(bad>=3&&index>0){index--;bad=0;}}else if(fps>48){good++;bad=0;if(good>=8&&index<LEVELS.length-1){index++;good=0;}}else{good=bad=0;}return current();}
}
