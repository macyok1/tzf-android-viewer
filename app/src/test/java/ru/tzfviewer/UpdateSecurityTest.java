package ru.tzfviewer;

import org.junit.Test;
import java.nio.file.Files;
import static org.junit.Assert.assertEquals;

public class UpdateSecurityTest {
    @Test public void computesSha256()throws Exception{java.io.File file=Files.createTempFile("tzf-update","bin").toFile();Files.write(file.toPath(),"abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",UpdateSecurity.sha256(file));}
}
