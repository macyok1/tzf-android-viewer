package ru.tzfviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;

public final class SettingsActivity extends Activity {
    private Button updateButton;
    private TextView updateStatus;
    private AppUpdater updater;
    private UpdateButtonState buttonState=UpdateButtonState.check();
    private UpdateInfo availableInfo;
    private File pendingUpdate;

    @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_settings);findViewById(R.id.backFromSettings).setOnClickListener(v->finish());((TextView)findViewById(R.id.appVersion)).setText("Версия "+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")");updateButton=findViewById(R.id.settingsUpdateApp);updateStatus=findViewById(R.id.settingsUpdateStatus);File cached=new File(new File(getCacheDir(),"updates"),"tzf-viewer-update.apk");cached.delete();setButtonState(UpdateButtonState.check());bindUpdater();updateButton.setOnClickListener(v->performUpdateAction());}

    private void bindUpdater(){updater=new AppUpdater(this,new AppUpdater.Listener(){public void status(String text){updateStatus.setText(text);setButtonState(UpdateButtonState.check());}public void available(UpdateInfo info){availableInfo=info;setButtonState(UpdateButtonState.download(info.versionName));new AlertDialog.Builder(SettingsActivity.this).setTitle("Доступно обновление "+info.versionName).setMessage("APK будет загружен с GitHub и проверен по SHA-256.").setNegativeButton("Позже",null).setPositiveButton("Скачать",(d,w)->startDownload()).show();}public void progress(int percent){setButtonState(UpdateButtonState.progress(percent));}public void ready(File apk,UpdateInfo info){pendingUpdate=apk;availableInfo=info;updateStatus.setText("Обновление проверено и готово к установке");setButtonState(UpdateButtonState.install(info.versionName));installPending();}public void error(String message){updateStatus.setText("Ошибка обновления: "+message);setButtonState(UpdateButtonState.retry());}});}
    private void performUpdateAction(){if(buttonState.action==UpdateButtonState.Action.INSTALL)installPending();else if(buttonState.action==UpdateButtonState.Action.DOWNLOAD)startDownload();else if(buttonState.action==UpdateButtonState.Action.CHECK){availableInfo=null;setButtonState(UpdateButtonState.checking());updater.check();}}
    private void startDownload(){if(availableInfo==null){setButtonState(UpdateButtonState.check());return;}setButtonState(UpdateButtonState.progress(-1));updater.download(availableInfo);}
    private void installPending(){if(pendingUpdate==null||!pendingUpdate.isFile()){setButtonState(UpdateButtonState.check());return;}if(!AppUpdater.install(this,pendingUpdate)){updateStatus.setText("Разрешите установку из TZF Viewer, вернитесь и снова нажмите «Установить»");setButtonState(UpdateButtonState.install(availableInfo==null?null:availableInfo.versionName));}}
    private void setButtonState(UpdateButtonState state){buttonState=state;if(updateButton!=null){updateButton.setText(state.label);updateButton.setEnabled(state.enabled);}}
    @Override protected void onDestroy(){if(updater!=null)updater.close();super.onDestroy();}
}
