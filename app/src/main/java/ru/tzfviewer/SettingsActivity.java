package ru.tzfviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;

public final class SettingsActivity extends Activity {
    private static final int PICK_STORAGE_TREE=201;
    private Button updateButton; private TextView updateStatus,versionView,channelView;
    private AppUpdater updater; private UpdateButtonState buttonState=UpdateButtonState.check();
    private UpdateInfo availableInfo; private File pendingUpdate;

    @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_settings);findViewById(R.id.backFromSettings).setOnClickListener(v->finish());versionView=findViewById(R.id.appVersion);channelView=findViewById(R.id.updateChannel);renderChannel();versionView.setOnLongClickListener(v->{showNightlyPin();return true;});findViewById(R.id.settingsHelp).setOnClickListener(v->startActivity(new Intent(this,HelpActivity.class)));findViewById(R.id.telegramContact).setOnClickListener(v->openTelegram());updateButton=findViewById(R.id.settingsUpdateApp);updateStatus=findViewById(R.id.settingsUpdateStatus);new File(new File(getCacheDir(),"updates"),"tzf-viewer-update.apk").delete();setButtonState(UpdateButtonState.check());bindUpdater();updateButton.setOnClickListener(v->performUpdateAction());findViewById(R.id.selectStorageFolder).setOnClickListener(v->selectStorageFolder());renderStorageFolder();}
    private boolean nightlyEnabled(){return getSharedPreferences("updates",MODE_PRIVATE).getBoolean("nightly",false);}
    private void renderChannel(){versionView.setText("Версия "+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")");channelView.setText("Канал обновлений: "+(nightlyEnabled()?"GitHub nightly":"публичный stable"));}
    private void showNightlyPin(){EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);input.setHint("PIN");new AlertDialog.Builder(this).setTitle("Канал разработчика").setMessage("Введите PIN для ночных обновлений").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Открыть",(d,w)->{if("1984".contentEquals(input.getText())){getSharedPreferences("updates",MODE_PRIVATE).edit().putBoolean("nightly",true).apply();renderChannel();bindUpdater();updateStatus.setText("Ночные обновления включены");}else updateStatus.setText("PIN не подходит");}).show();}
    private void openTelegram(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://t.me/macyok")));}catch(Exception error){updateStatus.setText("Не удалось открыть Telegram");}}
    private void selectStorageFolder(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,PICK_STORAGE_TREE);}
    private void renderStorageFolder(){String value=getSharedPreferences("x7",MODE_PRIVATE).getString("storage_tree","");((TextView)findViewById(R.id.storageFolderStatus)).setText(value.isEmpty()?"Папка не выбрана":Uri.parse(value).getLastPathSegment());}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICK_STORAGE_TREE&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);getSharedPreferences("x7",MODE_PRIVATE).edit().putString("storage_tree",uri.toString()).apply();renderStorageFolder();}}
    private void bindUpdater(){if(updater!=null)updater.close();updater=new AppUpdater(this,new AppUpdater.Listener(){public void status(String text){updateStatus.setText(text);setButtonState(UpdateButtonState.check());}public void available(UpdateInfo info){availableInfo=info;setButtonState(UpdateButtonState.download(info.versionName));InstrumentDialog.confirm(SettingsActivity.this,"Обновление","Доступна версия "+info.versionName,"APK будет загружен с GitHub и проверен по SHA-256.","Скачать",false,SettingsActivity.this::startDownload);}public void progress(int percent){setButtonState(UpdateButtonState.progress(percent));}public void ready(File apk,UpdateInfo info){pendingUpdate=apk;availableInfo=info;updateStatus.setText("Обновление проверено и готово к установке");setButtonState(UpdateButtonState.install(info.versionName));installPending();}public void error(String message){updateStatus.setText("Ошибка обновления: "+message);setButtonState(UpdateButtonState.retry());}},nightlyEnabled());}
    private void performUpdateAction(){if(buttonState.action==UpdateButtonState.Action.INSTALL)installPending();else if(buttonState.action==UpdateButtonState.Action.DOWNLOAD)startDownload();else if(buttonState.action==UpdateButtonState.Action.CHECK){availableInfo=null;setButtonState(UpdateButtonState.checking());updater.check();}}
    private void startDownload(){if(availableInfo==null){setButtonState(UpdateButtonState.check());return;}setButtonState(UpdateButtonState.progress(-1));updater.download(availableInfo);}
    private void installPending(){if(pendingUpdate==null||!pendingUpdate.isFile()){setButtonState(UpdateButtonState.check());return;}if(!AppUpdater.install(this,pendingUpdate)){updateStatus.setText("Разрешите установку из TZF Viewer, вернитесь и снова нажмите «Установить»");setButtonState(UpdateButtonState.install(availableInfo==null?null:availableInfo.versionName));}}
    private void setButtonState(UpdateButtonState state){buttonState=state;if(updateButton!=null){updateButton.setText(state.label);updateButton.setEnabled(state.enabled);}}
    @Override protected void onDestroy(){if(updater!=null)updater.close();super.onDestroy();}
}
