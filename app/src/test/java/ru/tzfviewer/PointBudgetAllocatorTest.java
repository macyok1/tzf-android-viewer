package ru.tzfviewer;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class PointBudgetAllocatorTest{
 @Test public void budgetIsGlobalAndHiddenScansGetZero(){List<PointBudgetAllocator.Item> i=Arrays.asList(new PointBudgetAllocator.Item("a",1_000_000,1,true,false),new PointBudgetAllocator.Item("b",1_000_000,1,true,false),new PointBudgetAllocator.Item("c",1_000_000,1,false,false));Map<String,Integer> r=PointBudgetAllocator.allocate(600_000,i);assertEquals(600_000,(int)r.get("a")+r.get("b")+r.get("c"));assertEquals(0,(int)r.get("c"));}
 @Test public void activeGetsPriorityButOtherScanKeepsQuota(){List<PointBudgetAllocator.Item> i=Arrays.asList(new PointBudgetAllocator.Item("a",1_000_000,1,true,true),new PointBudgetAllocator.Item("b",1_000_000,1,true,false));Map<String,Integer> r=PointBudgetAllocator.allocate(300_000,i);assertTrue(r.get("a")>r.get("b"));assertTrue(r.get("b")>=20_000);}
 @Test public void autoUsesHysteresis(){AutoPointBudget a=new AutoPointBudget();for(int i=0;i<7;i++)assertEquals(1_200_000,a.sample(60,false));assertEquals(2_500_000,a.sample(60,false));assertEquals(2_500_000,a.sample(10,false));assertEquals(2_500_000,a.sample(10,false));assertEquals(1_200_000,a.sample(10,false));}
}
