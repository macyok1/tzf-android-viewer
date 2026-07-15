package ru.tzfviewer;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TrimbleX7PathsTest {
    @Test public void keepsObservedPerspectiveProjectPath() throws Exception {
        List<String> paths=TrimbleX7Paths.downloadCandidates("Проект X7","Проект X7/2.tzf");
        assertEquals("/ScanData/Проект X7/2.tzf",paths.get(0));
        assertTrue(paths.contains("Проект X7/2.tzf"));
    }

    @Test public void addsProjectForFirmwareReturningOnlyFilename() throws Exception {
        List<String> paths=TrimbleX7Paths.downloadCandidates("2026_test","2.tzf");
        assertEquals("/ScanData/2026_test/2.tzf",paths.get(0));
        assertTrue(paths.contains("/ScanData/2.tzf"));
    }

    @Test public void doesNotDuplicateScanDataPrefix() throws Exception {
        List<String> paths=TrimbleX7Paths.downloadCandidates("p","/ScanData/p/2.tzf");
        assertEquals("/ScanData/p/2.tzf",paths.get(0));
        assertFalse(paths.get(0).contains("ScanData/ScanData"));
    }

    @Test(expected=java.io.IOException.class)
    public void rejectsFtpCommandInjection() throws Exception {
        TrimbleX7Paths.downloadCandidates("p","2.tzf\r\nDELE 1.tzf");
    }
}
