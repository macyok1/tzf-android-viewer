package ru.tzfviewer;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public class RegistrationTransformTest {
    @Test public void pivotAndOriginConventionsRoundTrip() {
        float[] origin={12,-7,3,90},pivot={100,50,20};
        assertArrayEquals(origin,RegistrationTransform.fromPivot(RegistrationTransform.toPivot(origin,pivot),pivot),.001f);
    }
}
