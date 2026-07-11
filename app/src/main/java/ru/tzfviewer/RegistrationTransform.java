package ru.tzfviewer;

final class RegistrationTransform {
    private RegistrationTransform() {}

    static float[] toPivot(float[] origin, float[] pivot) {
        double radians=Math.toRadians(origin[3]),c=Math.cos(radians),s=Math.sin(radians);
        float rx=(float)(c*pivot[0]-s*pivot[1]),ry=(float)(s*pivot[0]+c*pivot[1]);
        return new float[]{origin[0]-pivot[0]+rx,origin[1]-pivot[1]+ry,origin[2],origin[3]};
    }

    static float[] fromPivot(float[] transform, float[] pivot) {
        double radians=Math.toRadians(transform[3]),c=Math.cos(radians),s=Math.sin(radians);
        float rx=(float)(c*pivot[0]-s*pivot[1]),ry=(float)(s*pivot[0]+c*pivot[1]);
        return new float[]{transform[0]+pivot[0]-rx,transform[1]+pivot[1]-ry,transform[2],transform[3]};
    }
}
