package ru.tzfviewer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class TrimbleX7Paths {
    private TrimbleX7Paths() {}

    static List<String> downloadCandidates(String projectName, String apiFile) throws IOException {
        String file = clean(apiFile, "путь TZF");
        String project = clean(projectName, "имя проекта");
        while (file.startsWith("/")) file = file.substring(1);
        if (file.startsWith("mnt/sdcard/")) file = file.substring("mnt/sdcard/".length());
        if (file.startsWith("ScanData/")) file = file.substring("ScanData/".length());

        String baseName = file.substring(file.lastIndexOf('/') + 1);
        if (baseName.isEmpty()) throw new IOException("X7 вернул пустое имя TZF");

        LinkedHashSet<String> paths = new LinkedHashSet<>();
        // Current X7 firmware returns "project/file.tzf". Some versions return only file.tzf.
        if (file.indexOf('/') >= 0) paths.add("/ScanData/" + file);
        paths.add("/ScanData/" + project + "/" + baseName);
        paths.add("/ScanData/" + file);
        // Compatibility with FTP servers chrooted directly into ScanData.
        if (file.indexOf('/') >= 0) paths.add(file);
        paths.add(project + "/" + baseName);
        paths.add(baseName);
        return new ArrayList<>(paths);
    }

    private static String clean(String value, String label) throws IOException {
        String result = value == null ? "" : value.trim().replace('\\', '/');
        if (result.isEmpty()) throw new IOException("X7 вернул пустой " + label);
        if (result.indexOf('\r') >= 0 || result.indexOf('\n') >= 0 || result.contains("../") ||
                result.equals("..") || result.endsWith("/..")) {
            throw new IOException("X7 вернул недопустимый " + label);
        }
        while (result.contains("//")) result = result.replace("//", "/");
        return result;
    }
}
