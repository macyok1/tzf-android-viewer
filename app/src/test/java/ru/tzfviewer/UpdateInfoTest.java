package ru.tzfviewer;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateInfoTest {
    @Test public void parsesAndComparesManifest(){UpdateInfo info=UpdateInfo.parse("{\"versionCode\":42,\"versionName\":\"0.2.42\",\"apkUrl\":\"https://github.com/macyok1/tzf-android-viewer/releases/download/nightly/tzf-viewer-latest.apk\",\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"minSdk\":26}");assertEquals(42,info.versionCode);assertTrue(info.isNewerThan(41));assertFalse(info.isNewerThan(42));}
    @Test public void rejectsInsecureOrForeignUrls(){assertFalse(UpdateInfo.isAllowedUrl("http://github.com/a.apk"));assertFalse(UpdateInfo.isAllowedUrl("https://evil.example/a.apk"));assertFalse(UpdateInfo.isAllowedUrl("https://github.com.evil.example/a.apk"));assertTrue(UpdateInfo.isAllowedUrl("https://release-assets.githubusercontent.com/a.apk"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsShortHash(){new UpdateInfo(2,"2","https://github.com/a.apk","abc",26);}
}
