package ru.tzfviewer;

interface RegistrationEngine {
    final class Request {
        final String referenceStationId,movingStationId;
        final long generation;
        Request(String referenceStationId,String movingStationId,long generation){this.referenceStationId=referenceStationId;this.movingStationId=movingStationId;this.generation=generation;}
    }

    final class Result {
        enum Decision { ACCEPT, CHECK, REJECT }
        final Decision decision;
        final float[] candidateMovingWorld;
        final ProjectModel.RegistrationMetrics metrics;
        final String message;
        private Result(Decision decision,float[] candidateMovingWorld,ProjectModel.RegistrationMetrics metrics,String message){this.decision=decision;this.candidateMovingWorld=candidateMovingWorld==null?null:candidateMovingWorld.clone();this.metrics=metrics;this.message=message==null?"":message;}
        static Result accepted(float[] candidate,ProjectModel.RegistrationMetrics metrics,String message){return new Result(Decision.ACCEPT,candidate,metrics,message);}
        static Result check(float[] candidate,ProjectModel.RegistrationMetrics metrics,String message){return new Result(Decision.CHECK,candidate,metrics,message);}
        static Result rejected(ProjectModel.RegistrationMetrics metrics,String message){return new Result(Decision.REJECT,null,metrics,message);}
    }

    Result register(Request request)throws Exception;
    void cancel();
}
