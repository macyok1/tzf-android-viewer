package ru.tzfviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;

public final class SettingsActivity extends Activity {
    private static final int PICK_STORAGE_TREE=201;
    private Button updateButton;
    private TextView updateStatus;
    private AppUpdater updater;
    private UpdateButtonState buttonState=UpdateButtonState.check();
    private UpdateInfo availableInfo;
    private File pendingUpdate;

    @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_settings);findViewById(R.id.backFromSettings).setOnClickListener(v->finish());((TextView)findViewById(R.id.appVersion)).setText("Версия "+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")");updateButton=findViewById(R.id.settingsUpdateApp);updateStatus=findViewById(R.id.settingsUpdateStatus);File cached=new File(new File(getCacheDir(),"updates"),"tzf-viewer-update.apk");cached.delete();setButtonState(UpdateButtonState.check());bindUpdater();updateButton.setOnClickListener(v->performUpdateAction());findViewById(R.id.selectStorageFolder).setOnClickListener(v->selectStorageFolder());renderStorageFolder();}
    private void selectStorageFolder(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,PICK_STORAGE_TREE);}
    private void renderStorageFolder(){String value=getSharedPreferences("x7",MODE_PRIVATE).getString("storage_tree","");((TextView)findViewById(R.id.storageFolderStatus)).setText(value.isEmpty()?"Папка не выбрана":Uri.parse(value).getLastPathSegment());}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICK_STORAGE_TREE&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);getSharedPreferences("x7",MODE_PRIVATE).edit().putString("storage_tree",uri.toString()).apply();renderStorageFolder();}}

    private void bindUpdater(){updater=new AppUpdater(this,new AppUpdater.Listener(){public void status(String text){updateStatus.setText(text);setButtonState(UpdateButtonState.check());}public void available(UpdateInfo info){availableInfo=info;setButtonState(UpdateButtonState.download(info.versionName));new AlertDialog.Builder(SettingsActivity.this).setTitle("Доступно обновление "+info.versionName).setMessage("APK будет загружен с GitHub и проверен по SHA-256.").setNegativeButton("Позже",null).setPositiveButton("Скачать",(d,w)->startDownload()).show();}public void progress(int percent){setButtonState(UpdateButtonState.progress(percent));}public void ready(File apk,UpdateInfo info){pendingUpdate=apk;availableInfo=info;updateStatus.setText("Обновление проверено и готово к установке");setButtonState(UpdateButtonState.install(info.versionName));installPending();}public void error(String message){updateStatus.setText("Ошибка обновления: "+message);setButtonState(UpdateButtonState.retry());}});}
    private void performUpdateAction(){if(buttonState.action==UpdateButtonState.Action.INSTALL)installPending();else if(buttonState.action==UpdateButtonState.Action.DOWNLOAD)startDownload();else if(buttonState.action==UpdateButtonState.Action.CHECK){availableInfo=null;setButtonState(UpdateButtonState.checking());updater.check();}}
    private void startDownload(){if(availableInfo==null){setButtonState(UpdateButtonState.check());return;}setButtonState(UpdateButtonState.progress(-1));updater.download(availableInfo);}
    private void installPending(){if(pendingUpdate==null||!pendingUpdate.isFile()){setButtonState(UpdateButtonState.check());return;}if(!AppUpdater.install(this,pendingUpdate)){updateStatus.setText("Разрешите установку из TZF Viewer, вернитесь и снова нажмите «Установить»");setButtonState(UpdateButtonState.install(availableInfo==null?null:availableInfo.versionName));}}
    private void setButtonState(UpdateButtonState state){buttonState=state;if(updateButton!=null){updateButton.setText(state.label);updateButton.setEnabled(state.enabled);}}
    @Override protected void onDestroy(){if(updater!=null)updater.close();super.onDestroy();}
}
