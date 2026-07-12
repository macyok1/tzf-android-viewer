package ru.tzfviewer;

import org.junit.Test;
import static org.junit.Assert.*;

public class RegistrationUiStateTest {
    @Test public void idleEnablesRegistrationActions(){
        RegistrationUiState state=RegistrationUiState.idle();
        assertTrue(state.actionsEnabled());assertFalse(state.running());assertFalse(state.showDecision());
    }

    @Test public void runningClampsProgressAndOffersOnlyCancel(){
        RegistrationUiState state=RegistrationUiState.running(RegistrationUiState.Stage.SEARCHING,140,"Поиск");
        assertEquals(100,state.progress);assertTrue(state.running());assertTrue(state.showProgress());
        assertTrue(state.showCancel());assertFalse(state.actionsEnabled());assertFalse(state.showDecision());
    }

    @Test public void previewRequiresExplicitDecision(){
        RegistrationUiState state=RegistrationUiState.preview("Кандидат");
        assertTrue(state.showDecision());assertFalse(state.actionsEnabled());assertFalse(state.showCancel());
    }

    @Test(expected=IllegalArgumentException.class)
    public void idleCannotBeConstructedAsRunning(){RegistrationUiState.running(RegistrationUiState.Stage.IDLE,0,"");}
}
