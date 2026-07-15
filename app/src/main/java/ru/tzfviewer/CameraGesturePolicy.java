package ru.tzfviewer;

/** Resolves pointer gestures without depending on Android UI classes. */
final class CameraGesturePolicy {
    enum Mode { PAN, ORBIT }
    enum Action { NONE, PAN, ORBIT, ZOOM, OBJECT }

    private Mode mode=Mode.PAN;
    private boolean manualRegistrationActive;

    Mode mode(){return mode;}
    boolean orbitEnabled(){return mode==Mode.ORBIT;}

    void setOrbitEnabled(boolean enabled){
        mode=enabled&&!manualRegistrationActive?Mode.ORBIT:Mode.PAN;
    }

    void setManualRegistrationActive(boolean active){
        manualRegistrationActive=active;
        mode=Mode.PAN;
    }

    Action actionFor(int pointerCount,boolean gizmoActive,boolean clipActive,boolean measureActive){
        if(pointerCount>=2)return Action.ZOOM;
        if(pointerCount!=1)return Action.NONE;
        if(gizmoActive||clipActive||measureActive||manualRegistrationActive)return Action.OBJECT;
        return mode==Mode.ORBIT?Action.ORBIT:Action.PAN;
    }
}
