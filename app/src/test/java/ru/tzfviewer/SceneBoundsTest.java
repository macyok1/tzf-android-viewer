package ru.tzfviewer;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class SceneBoundsTest {
    @Test public void combinesChunksAndAppliesWorldPose() {
        float[] first = SceneBounds.of(new float[]{0, 0, -1, 2, 4, 3});
        float[] combined = SceneBounds.merge(first, SceneBounds.of(new float[]{-2, 1, 0}));
        assertArrayEquals(new float[]{-2, 0, -1, 2, 4, 3}, combined, .001f);

        float[] world = SceneBounds.transformed(combined, new float[]{10, 20, 5, 90});
        assertArrayEquals(new float[]{6, 18, 4, 10, 22, 8}, world, .001f);
    }
}
