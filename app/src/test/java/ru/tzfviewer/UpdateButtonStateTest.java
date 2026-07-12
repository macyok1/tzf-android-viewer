package ru.tzfviewer;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateButtonStateTest {
    @Test public void idleAndErrorsRemainClickable(){assertTrue(UpdateButtonState.check().enabled);assertEquals(UpdateButtonState.Action.CHECK,UpdateButtonState.check().action);assertTrue(UpdateButtonState.retry().enabled);}
    @Test public void busyStatesAreDisabled(){assertFalse(UpdateButtonState.checking().enabled);assertFalse(UpdateButtonState.progress(45).enabled);assertEquals("45%",UpdateButtonState.progress(45).label);}
    @Test public void availableAndReadyExposeCorrectActions(){assertEquals(UpdateButtonState.Action.DOWNLOAD,UpdateButtonState.download("0.2.7").action);assertEquals(UpdateButtonState.Action.INSTALL,UpdateButtonState.install("0.2.7").action);}
}
