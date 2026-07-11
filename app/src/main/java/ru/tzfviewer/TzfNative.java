package ru.tzfviewer;

final class TzfNative {
    static {
        System.loadLibrary("tzfreader");
    }

    private TzfNative() {}

    static native float[] decodePreview(String localPath, int maxPoints)
            throws java.io.IOException;
}
