package ru.tzfviewer;

final class RegistrationPoseMath {
    private RegistrationPoseMath(){}

    static float[] compose(float[] first,float[] second){return ProjectModel.compose(first,second);}
    static float[] relative(float[] parent,float[] child){return ProjectModel.relative(parent,child);}
    static float[] inverse(float[] pose){return ProjectModel.relative(pose,new float[4]);}
    static float[] correction(float[] oldMovingWorld,float[] candidateMovingWorld){return compose(candidateMovingWorld,inverse(oldMovingWorld));}
    static boolean finite(float[] pose){if(pose==null||pose.length!=4)return false;for(float value:pose)if(!Float.isFinite(value))return false;return true;}
}
