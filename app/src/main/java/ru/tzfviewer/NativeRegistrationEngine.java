package ru.tzfviewer;

final class NativeRegistrationEngine implements RegistrationEngine {
    interface InputProvider { Input load(Request request)throws Exception; }
    static final class Input {
        final float[] referenceWorld,movingLocal,initialMovingWorld,embeddedMovingWorld;
        Input(float[] referenceWorld,float[] movingLocal,float[] initialMovingWorld,float[] embeddedMovingWorld){this.referenceWorld=referenceWorld;this.movingLocal=movingLocal;this.initialMovingWorld=initialMovingWorld;this.embeddedMovingWorld=embeddedMovingWorld;}
    }

    private final InputProvider provider;
    private final double rmsLimit,p95Limit;
    NativeRegistrationEngine(InputProvider provider,double rmsLimit,double p95Limit){this.provider=provider;this.rmsLimit=rmsLimit;this.p95Limit=p95Limit;}

    @Override public Result register(Request request)throws Exception {
        Input input=provider.load(request);if(input==null||!RegistrationPoseMath.finite(input.initialMovingWorld))throw new IllegalArgumentException("invalid registration input");
        float[] pivot=RegistrationTransform.centroid(input.movingLocal);RegistrationResult nativeResult;
        if(RegistrationPoseMath.finite(input.embeddedMovingWorld))nativeResult=TzfNative.registerPointClouds(input.referenceWorld,input.movingLocal,RegistrationTransform.toPivot(input.embeddedMovingWorld,pivot),rmsLimit,p95Limit);
        else nativeResult=TzfNative.registerPointCloudsGlobal(input.referenceWorld,input.movingLocal,rmsLimit,p95Limit);
        ProjectModel.RegistrationMetrics metrics=new ProjectModel.RegistrationMetrics((float)nativeResult.rms,(float)nativeResult.p95,(float)nativeResult.overlap,(float)nativeResult.consistency,(float)nativeResult.confidence);
        if(!nativeResult.accepted)return Result.rejected(metrics,nativeResult.reason);
        float[] directWorld=RegistrationTransform.fromPivot(nativeResult.transform,pivot);
        return "check registration".equals(nativeResult.reason)?Result.check(directWorld,metrics,nativeResult.reason):Result.accepted(directWorld,metrics,nativeResult.reason);
    }

    @Override public void cancel(){TzfNative.cancelRegistration();}
}
