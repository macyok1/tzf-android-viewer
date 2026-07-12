package ru.tzfviewer;

final class RegistrationUiState {
    enum Stage { IDLE, PREPARING, SEARCHING, REFINING, VALIDATING, PREVIEW, ERROR }

    final Stage stage;
    final int progress;
    final String message;

    private RegistrationUiState(Stage stage,int progress,String message){
        this.stage=stage;
        this.progress=Math.max(0,Math.min(100,progress));
        this.message=message==null?"":message;
    }

    static RegistrationUiState idle(){return new RegistrationUiState(Stage.IDLE,0,"");}
    static RegistrationUiState running(Stage stage,int progress,String message){
        if(stage==Stage.IDLE||stage==Stage.PREVIEW||stage==Stage.ERROR)throw new IllegalArgumentException("not a running stage");
        return new RegistrationUiState(stage,progress,message);
    }
    static RegistrationUiState preview(String message){return new RegistrationUiState(Stage.PREVIEW,100,message);}
    static RegistrationUiState error(String message){return new RegistrationUiState(Stage.ERROR,0,message);}

    boolean running(){return stage==Stage.PREPARING||stage==Stage.SEARCHING||stage==Stage.REFINING||stage==Stage.VALIDATING;}
    boolean showProgress(){return running();}
    boolean showCancel(){return running();}
    boolean showDecision(){return stage==Stage.PREVIEW;}
    boolean actionsEnabled(){return stage==Stage.IDLE||stage==Stage.ERROR;}
}
