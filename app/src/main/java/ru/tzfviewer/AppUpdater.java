package ru.tzfviewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AppUpdater implements AutoCloseable {
    static final String NIGHTLY_MANIFEST_URL="https://github.com/macyok1/tzf-android-viewer/releases/download/nightly/update.json";
    static final String STABLE_MANIFEST_URL="https://github.com/macyok1/tzf-android-viewer/releases/latest/download/update.json";
    interface Listener {void status(String text);void available(UpdateInfo info);void progress(int percent);void ready(File apk,UpdateInfo info);void error(String message);}
    private final Context context;
    private final Listener listener;
    private final String manifestUrl;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();

    AppUpdater(Context context,Listener listener,boolean nightly){this.context=context.getApplicationContext();this.listener=listener;this.manifestUrl=nightly?NIGHTLY_MANIFEST_URL:STABLE_MANIFEST_URL;}
    void check(){worker.execute(()->{try{UpdateInfo info=UpdateInfo.parse(readText(manifestUrl));if(info.minSdk>Build.VERSION.SDK_INT)throw new IOException("обновление требует Android API "+info.minSdk);if(info.isNewerThan(BuildConfig.VERSION_CODE))post(()->listener.available(info));else post(()->listener.status("Установлена актуальная версия "+BuildConfig.VERSION_NAME));}catch(Exception error){fail(error);}});}
    void download(UpdateInfo info){worker.execute(()->{File directory=new File(context.getCacheDir(),"updates");if(!directory.exists()&&!directory.mkdirs()){fail(new IOException("не удалось создать каталог обновления"));return;}File temp=new File(directory,"update.tmp"),target=new File(directory,"tzf-viewer-update.apk");temp.delete();target.delete();try{downloadFile(info.apkUrl,temp);String actual=UpdateSecurity.sha256(temp);if(!actual.equals(info.sha256))throw new IOException("SHA-256 загруженного APK не совпадает");try{Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(IOException unsupported){Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}post(()->listener.ready(target,info));}catch(Exception error){temp.delete();fail(error);}});}
    static boolean install(Activity activity,File apk){if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName()));activity.startActivity(settings);return false;}Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",apk);Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);activity.startActivity(intent);return true;}
    private String readText(String url)throws IOException{HttpURLConnection connection=connection(url);try(InputStream input=connection.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count,total=0;while((count=input.read(buffer))!=-1){total+=count;if(total>65536)throw new IOException("update.json слишком большой");out.write(buffer,0,count);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}finally{connection.disconnect();}}
    private void downloadFile(String url,File target)throws IOException{HttpURLConnection connection=connection(url);long expected=connection.getContentLengthLong(),received=0;try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(target)){byte[] buffer=new byte[1024*256];int count,last=-1;while((count=input.read(buffer))!=-1){received+=count;if(received>600L*1024*1024)throw new IOException("APK превышает 600 MB");output.write(buffer,0,count);int percent=expected>0?(int)Math.min(100,received*100/expected):-1;if(percent!=last){last=percent;int value=percent;post(()->listener.progress(value));}}}finally{connection.disconnect();}}
    private HttpURLConnection connection(String value)throws IOException{URL url=new URL(value);for(int redirects=0;redirects<6;redirects++){if(!UpdateInfo.isAllowedUrl(url.toString()))throw new IOException("запрещённый адрес обновления");HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(60000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","TZF-Viewer/"+BuildConfig.VERSION_NAME);int code=connection.getResponseCode();if(code>=300&&code<400){String location=connection.getHeaderField("Location");connection.disconnect();if(location==null)throw new IOException("redirect без адреса");url=new URL(url,location);continue;}if(code!=200){connection.disconnect();throw new IOException("GitHub вернул HTTP "+code);}return connection;}throw new IOException("слишком много redirect");}
    private void fail(Exception error){post(()->listener.error(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()));}
    private void post(Runnable action){main.post(action);}
    @Override public void close(){worker.shutdownNow();}
}
