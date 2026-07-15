package ru.tzfviewer;

final class RegistrationWorkflow {
    enum Outcome { SKIPPED, COMPLETE, CHECK, FAILED, STALE }
    static final class CaptureTicket {
        final long generation;
        final String resolvedReferenceId;
        CaptureTicket(long generation,String resolvedReferenceId){this.generation=generation;this.resolvedReferenceId=resolvedReferenceId==null?"":resolvedReferenceId;}
    }

    private final RegistrationGraph graph;
    private final RegistrationEngine engine;
    private final Runnable persist;
    private volatile long activeGeneration=Long.MIN_VALUE;
    private volatile String activeMovingId="";

    RegistrationWorkflow(RegistrationGraph graph,RegistrationEngine engine,Runnable persist){this.graph=graph;this.engine=engine;this.persist=persist==null?()->{}:persist;}

    Outcome start(String movingId,CaptureTicket ticket){
        ProjectModel.Node movingNode=graph.project().findNode(movingId);if(!(movingNode instanceof ProjectModel.Scan))return Outcome.FAILED;ProjectModel.Scan moving=(ProjectModel.Scan)movingNode;
        if(ticket==null||ticket.resolvedReferenceId.isEmpty()){moving.registrationState=ProjectModel.RegistrationState.UNLINKED;moving.attemptedReferenceId="";moving.registrationMessage="";persist.run();return Outcome.SKIPPED;}
        activeGeneration=ticket.generation;activeMovingId=movingId;ProjectModel.Node reference=graph.project().findNode(ticket.resolvedReferenceId);if(!(reference instanceof ProjectModel.Scan)||graph.setForScan(ticket.resolvedReferenceId)==graph.setForScan(movingId)){moving.attemptedReferenceId="";return fail(moving,"missing or ineligible reference: "+ticket.resolvedReferenceId);}
        moving.registrationState=ProjectModel.RegistrationState.PENDING;moving.attemptedReferenceId=ticket.resolvedReferenceId;moving.registrationMessage="";moving.pendingCandidateValid=false;java.util.Arrays.fill(moving.pendingCandidateWorld,0);persist.run();
        moving.registrationState=ProjectModel.RegistrationState.REGISTERING;persist.run();
        RegistrationEngine.Result result;try{result=engine.register(new RegistrationEngine.Request(ticket.resolvedReferenceId,movingId,ticket.generation));}catch(Exception error){if(!current(ticket,movingId))return Outcome.STALE;return fail(moving,error.getMessage()==null?"registration failed":error.getMessage());}
        if(!current(ticket,movingId))return Outcome.STALE;if(result==null||result.metrics==null||!result.metrics.finite())return fail(moving,"invalid registration result");moving.registrationMetrics=result.metrics;moving.registrationMessage=result.message;
        if(result.decision==RegistrationEngine.Result.Decision.REJECT)return fail(moving,result.message.isEmpty()?"registration rejected":result.message);
        if(!RegistrationPoseMath.finite(result.candidateMovingWorld))return fail(moving,"invalid registration candidate");
        if(result.decision==RegistrationEngine.Result.Decision.CHECK){moving.registrationState=ProjectModel.RegistrationState.CHECK;moving.pendingCandidateValid=true;System.arraycopy(result.candidateMovingWorld,0,moving.pendingCandidateWorld,0,4);persist.run();return Outcome.CHECK;}
        graph.acceptRegistration(ticket.resolvedReferenceId,movingId,result.candidateMovingWorld,result.metrics,ProjectModel.LinkSource.AUTO_X7,System.currentTimeMillis());moving.registrationMessage=result.message;persist.run();clearActive(ticket.generation);return Outcome.COMPLETE;
    }

    boolean confirmCheck(String movingId,ProjectModel.LinkSource source){ProjectModel.Node node=graph.project().findNode(movingId);if(!(node instanceof ProjectModel.Scan))return false;ProjectModel.Scan moving=(ProjectModel.Scan)node;if(moving.registrationState!=ProjectModel.RegistrationState.CHECK||!moving.pendingCandidateValid||moving.attemptedReferenceId.isEmpty())return false;graph.acceptRegistration(moving.attemptedReferenceId,moving.id,moving.pendingCandidateWorld,moving.registrationMetrics,source,System.currentTimeMillis());persist.run();return true;}
    boolean rejectCheck(String movingId){ProjectModel.Node node=graph.project().findNode(movingId);if(!(node instanceof ProjectModel.Scan))return false;ProjectModel.Scan moving=(ProjectModel.Scan)node;if(moving.registrationState!=ProjectModel.RegistrationState.CHECK)return false;fail(moving,"candidate rejected");return true;}

    void cancel(){long generation=activeGeneration;activeGeneration=generation==Long.MAX_VALUE?Long.MIN_VALUE:generation+1;String movingId=activeMovingId;activeMovingId="";engine.cancel();ProjectModel.Node node=graph.project().findNode(movingId);if(node instanceof ProjectModel.Scan){ProjectModel.Scan moving=(ProjectModel.Scan)node;if(moving.registrationState==ProjectModel.RegistrationState.REGISTERING)moving.registrationState=ProjectModel.RegistrationState.PENDING;persist.run();}}

    private Outcome fail(ProjectModel.Scan moving,String message){moving.registrationState=ProjectModel.RegistrationState.FAILED;moving.registrationMessage=message==null?"registration failed":message;moving.pendingCandidateValid=false;java.util.Arrays.fill(moving.pendingCandidateWorld,0);persist.run();activeMovingId="";return Outcome.FAILED;}
    private boolean current(CaptureTicket ticket,String movingId){return activeGeneration==ticket.generation&&movingId.equals(activeMovingId);}
    private void clearActive(long generation){if(activeGeneration==generation){activeMovingId="";activeGeneration=Long.MIN_VALUE;}}
}
