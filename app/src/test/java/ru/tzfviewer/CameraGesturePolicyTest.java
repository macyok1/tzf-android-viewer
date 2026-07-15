package ru.tzfviewer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CameraGesturePolicyTest {
    @Test public void defaultsToPan(){
        CameraGesturePolicy policy=new CameraGesturePolicy();
        assertEquals(CameraGesturePolicy.Mode.PAN,policy.mode());
        assertEquals(CameraGesturePolicy.Action.PAN,policy.actionFor(1,false,false,false));
    }

    @Test public void freeSinglePointerUsesSelectedCameraMode(){
        CameraGesturePolicy policy=new CameraGesturePolicy();
        policy.setOrbitEnabled(true);
        assertTrue(policy.orbitEnabled());
        assertEquals(CameraGesturePolicy.Action.ORBIT,policy.actionFor(1,false,false,false));
        policy.setOrbitEnabled(false);
        assertEquals(CameraGesturePolicy.Action.PAN,policy.actionFor(1,false,false,false));
    }

    @Test public void objectToolsOverrideSinglePointerCameraActions(){
        CameraGesturePolicy policy=new CameraGesturePolicy();
        policy.setOrbitEnabled(true);
        assertEquals(CameraGesturePolicy.Action.OBJECT,policy.actionFor(1,true,false,false));
        assertEquals(CameraGesturePolicy.Action.OBJECT,policy.actionFor(1,false,true,false));
        assertEquals(CameraGesturePolicy.Action.OBJECT,policy.actionFor(1,false,false,true));
        policy.setManualRegistrationActive(true);
        assertEquals(CameraGesturePolicy.Action.OBJECT,policy.actionFor(1,false,false,false));
    }

    @Test public void manualModeNeverPersistsOrbit(){
        CameraGesturePolicy policy=new CameraGesturePolicy();
        policy.setOrbitEnabled(true);
        policy.setManualRegistrationActive(true);
        assertFalse(policy.orbitEnabled());
        policy.setManualRegistrationActive(false);
        assertEquals(CameraGesturePolicy.Mode.PAN,policy.mode());
        assertEquals(CameraGesturePolicy.Action.PAN,policy.actionFor(1,false,false,false));
    }

    @Test public void twoPointersOnlyZoom(){
        CameraGesturePolicy policy=new CameraGesturePolicy();
        policy.setOrbitEnabled(true);
        assertEquals(CameraGesturePolicy.Action.ZOOM,policy.actionFor(2,true,true,true));
        policy.setManualRegistrationActive(true);
        assertEquals(CameraGesturePolicy.Action.ZOOM,policy.actionFor(2,false,false,false));
    }
}
