package ru.tzfviewer;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public class RegistrationTransformTest {
    @Test public void pivotAndOriginConventionsRoundTrip() {
        float[] origin={12,-7,3,90},pivot={100,50,20};
        assertArrayEquals(origin,RegistrationTransform.fromPivot(RegistrationTransform.toPivot(origin,pivot),pivot),.001f);
    }

    @Test public void centroidMatchesNativeRegistrationPivot() {
        float[] xyz={0,0,0, 0,0,0, 12,6,3};
        float[] centroid=RegistrationTransform.centroid(xyz);
        assertArrayEquals(new float[]{4,2,1},centroid,.0001f);

        float[] origin={120,-80,5,127};
        float[] solver=RegistrationTransform.toPivot(origin,centroid);
        assertArrayEquals(origin,RegistrationTransform.fromPivot(solver,centroid),.001f);
    }
}
