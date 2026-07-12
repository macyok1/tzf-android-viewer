package ru.tzfviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    static final String EXTRA_PROJECT_ID = "project_id";
    private static final int PICK_TZF = 103;
    private static final int[] POINT_BUDGETS={150_000,300_000,600_000,1_200_000,2_500_000,5_000_000,10_000_000,-1};
    private static final int[] POINT_SIZES={1,2,3,5};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger registrationGeneration=new AtomicInteger();
    private final ConcurrentHashMap<String,AtomicInteger> sceneGenerations=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,NativePreviewSession> sessions=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,File> localFiles=new ConcurrentHashMap<>();
    private ProjectStore store;
    private ProjectModel project;
    private PointCloudView cloud;
    private ScanTreePanel tree;
    private TextView status,referenceLabel,movingLabel,transformSummary;
    private EditText rmsLimit,p95Limit;
    private Button registerButton,cancelButton,pointSizeButton,budgetButton;
    private ProgressBar registrationProgress;
    private View scanPanel,settingsPanel;
    private Button updateButton;
    private AppUpdater updater;
    private File pendingUpdate;
    private int budgetIndex,pointSizeIndex;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        store=new ProjectStore(getFilesDir());String id=getIntent().getStringExtra(EXTRA_PROJECT_ID);if(id==null){finish();return;}
        try{project=store.load(id);}catch(Exception error){finish();return;}
        setContentView(R.layout.activity_main);
        cloud=new PointCloudView(this);
        ((FrameLayout)findViewById(R.id.viewportContainer)).addView(cloud,0,new FrameLayout.LayoutParams(-1,-1));
        bindViews();bindCamera();bindTools();bindTree();bindUpdater();
        cloud.setTransformListener(this::applyMovingWorldTransform);
        cloud.setPointSize(project.pointSize);cloud.setGridVisible(project.gridVisible);cloud.restoreOrientation(project.cameraYaw,project.cameraPitch);if(project.orthographic)cloud.toggleProjection();
        updateRoleUi();syncScene();restoreProjectScans();sizeOverlays();
    }

    private void bindViews(){
        ((TextView)findViewById(R.id.projectTitle)).setText(project.name);
        status=findViewById(R.id.status);referenceLabel=findViewById(R.id.referenceLabel);movingLabel=findViewById(R.id.movingLabel);transformSummary=findViewById(R.id.transformSummary);
        rmsLimit=findViewById(R.id.rmsLimit);p95Limit=findViewById(R.id.p95Limit);registerButton=findViewById(R.id.registerScans);cancelButton=findViewById(R.id.cancelRegistration);registrationProgress=findViewById(R.id.registrationProgress);
        scanPanel=findViewById(R.id.scanPanel);settingsPanel=findViewById(R.id.settingsPanel);tree=findViewById(R.id.scanTree);pointSizeButton=findViewById(R.id.pointSize);budgetButton=findViewById(R.id.pointBudget);updateButton=findViewById(R.id.updateApp);
    }

    private void bindCamera(){
        OrientationCubeView cube=findViewById(R.id.orientation);cube.setListener(new OrientationCubeView.Listener(){public void onPreset(float yaw,float pitch){cloud.setPreset(yaw,pitch);}public void onRotate(float dy,float dp){cloud.rotateCamera(dy,dp);}});
        cloud.setOrientationListener((yaw,pitch)->{cube.setOrientation(yaw,pitch);project.cameraYaw=yaw;project.cameraPitch=pitch;project.touch(System.currentTimeMillis());});
    }

    private void bindTools(){
        findViewById(R.id.compactOpen).setOnClickListener(v->selectTzf());
        findViewById(R.id.compactFit).setOnClickListener(v->{if(readyScans().isEmpty())status.setText("Нет видимых готовых облаков");else cloud.fitView();});
        findViewById(R.id.scans).setOnClickListener(v->togglePanel(scanPanel,settingsPanel));findViewById(R.id.closeScans).setOnClickListener(v->scanPanel.setVisibility(View.GONE));
        findViewById(R.id.moreTools).setOnClickListener(v->togglePanel(settingsPanel,scanPanel));findViewById(R.id.closeSettings).setOnClickListener(v->settingsPanel.setVisibility(View.GONE));
        findViewById(R.id.compactProjection).setOnClickListener(v->{project.orthographic=!project.orthographic;cloud.toggleProjection();changed();});
        findViewById(R.id.grid).setOnClickListener(v->{project.gridVisible=!project.gridVisible;cloud.setGridVisible(project.gridVisible);changed();});
        for(int i=0;i<POINT_BUDGETS.length;i++)if(POINT_BUDGETS[i]==project.pointBudget)budgetIndex=i;for(int i=0;i<POINT_SIZES.length;i++)if(POINT_SIZES[i]==project.pointSize)pointSizeIndex=i;
        pointSizeButton.setText("•• "+project.pointSize);budgetButton.setText(budgetLabel(project.pointBudget));
        pointSizeButton.setOnClickListener(v->{pointSizeIndex=(pointSizeIndex+1)%POINT_SIZES.length;project.pointSize=POINT_SIZES[pointSizeIndex];cloud.setPointSize(project.pointSize);pointSizeButton.setText("•• "+project.pointSize);changed();});
        budgetButton.setOnClickListener(v->{budgetIndex=(budgetIndex+1)%POINT_BUDGETS.length;project.pointBudget=POINT_BUDGETS[budgetIndex];budgetButton.setText(budgetLabel(project.pointBudget));reloadAll();changed();});
        Button measure=findViewById(R.id.measure);measure.setOnClickListener(v->{measure.setSelected(!measure.isSelected());cloud.setMeasureMode(measure.isSelected(),(length,dz)->status.setText(String.format(Locale.US,"Расстояние %.3f · ΔZ %.3f",length,dz)));});
        registerButton.setOnClickListener(v->startRegistration());cancelButton.setOnClickListener(v->cancelRegistration());
        findViewById(R.id.resetTransform).setOnClickListener(v->resetMoving());findViewById(R.id.saveTransform).setOnClickListener(v->saveMovingSnapshot());findViewById(R.id.restoreTransform).setOnClickListener(v->restoreMovingSnapshot());
        setRegistrationRunning(false);
    }

    private void bindTree(){tree.bind(project,new ScanTreePanel.Listener(){public void changed(){MainActivity.this.changed();syncScene();updateRoleUi();}public void visibilityChanged(ProjectModel.Node node){syncScene();}public void rolesChanged(){updateRoleUi();syncScene();}});}
    private void bindUpdater(){updater=new AppUpdater(this,new AppUpdater.Listener(){public void status(String text){updateButton.setEnabled(true);updateButton.setText("Обновить");MainActivity.this.status.setText(text);}public void available(UpdateInfo info){updateButton.setEnabled(true);updateButton.setText("Скачать "+info.versionName);new AlertDialog.Builder(MainActivity.this).setTitle("Доступно обновление "+info.versionName).setMessage("APK будет загружен с GitHub и проверен по SHA-256.").setNegativeButton("Позже",null).setPositiveButton("Скачать",(d,w)->{updateButton.setEnabled(false);updater.download(info);}).show();}public void progress(int percent){updateButton.setText(percent<0?"Загрузка…":percent+"%");}public void ready(File apk,UpdateInfo info){pendingUpdate=apk;updateButton.setEnabled(true);updateButton.setText("Установить "+info.versionName);status.setText("Обновление проверено и готово к установке");installPendingUpdate();}public void error(String message){updateButton.setEnabled(true);updateButton.setText("Повторить");status.setText("Ошибка обновления: "+message);}});updateButton.setOnClickListener(v->{if(pendingUpdate!=null&&pendingUpdate.isFile())installPendingUpdate();else{updateButton.setEnabled(false);updater.check();}});}
    private void installPendingUpdate(){if(pendingUpdate==null||!pendingUpdate.isFile())return;if(!AppUpdater.install(this,pendingUpdate)){status.setText("Разрешите установку из TZF Viewer, вернитесь и нажмите «Установить»");updateButton.setText("Установить");}}
    private void sizeOverlays(){findViewById(R.id.viewportContainer).post(()->{int max=Math.round(findViewById(R.id.viewportContainer).getHeight()*.35f),cap=dp(220),height=Math.min(cap,max);scanPanel.getLayoutParams().height=height;settingsPanel.getLayoutParams().height=height;scanPanel.requestLayout();settingsPanel.requestLayout();});}
    private void togglePanel(View show,View hide){hide.setVisibility(View.GONE);show.setVisibility(show.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);}

    private void selectTzf(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,PICK_TZF);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK_TZF||result!=RESULT_OK||data==null)return;List<Uri> uris=new ArrayList<>();ClipData clip=data.getClipData();if(clip!=null)for(int i=0;i<clip.getItemCount();i++)uris.add(clip.getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());int added=0;for(Uri uri:uris){String name=displayName(uri);if(!name.toLowerCase(Locale.ROOT).endsWith(".tzf")){status.setText("Пропущен не-TZF файл: "+name);continue;}try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}ProjectModel.Scan scan=rememberScan(uri,name);if(scan.loadState!=ProjectModel.Scan.READY){decodeScene(uri,scan);added++;}}changed();tree.refresh();status.setText("Добавлено в очередь: "+added);}

    private ProjectModel.Scan rememberScan(Uri uri,String name){for(ProjectModel.Scan scan:allScans())if(uri.toString().equals(scan.uri))return scan;ProjectModel.Scan scan=new ProjectModel.Scan(UUID.randomUUID().toString(),name);scan.uri=uri.toString();int[] colors={0xff38c9e8,0xffffb44a,0xff8ee06f,0xffd58cff,0xffff718a};scan.color=colors[project.scanCount()%colors.length];project.root.add(scan);return scan;}
    private String displayName(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}String name=uri.getLastPathSegment();return name==null?"Scan.tzf":name.substring(Math.max(name.lastIndexOf('/')+1,name.lastIndexOf(':')+1));}

    private void decodeScene(Uri uri,ProjectModel.Scan scan){AtomicInteger counter=sceneGenerations.computeIfAbsent(scan.id,k->new AtomicInteger());int generation=counter.incrementAndGet();scan.loadState=ProjectModel.Scan.LOADING;scan.loadError="";tree.refresh();worker.execute(()->{try{File local=copyToCache(uri,"scene-"+scan.id+".tzf");if(project.findNode(scan.id)==null)return;localFiles.put(scan.id,local);NativePreviewSession old=sessions.remove(scan.id);if(old!=null)old.close();NativePreviewSession session=new NativePreviewSession(local.getAbsolutePath());sessions.put(scan.id,session);int quota=Math.max(20_000,effectiveBudget()/Math.max(1,project.scanCount()));session.prepare(quota);boolean first=true;int published=0;while(counter.get()==generation&&project.findNode(scan.id)!=null){float[] chunk=session.nextChunk(100_000);if(chunk.length==0)break;boolean reset=first;first=false;published+=chunk.length/3;float[] world=scan.worldTransform();runOnUiThread(()->{if(counter.get()==generation&&project.findNode(scan.id)!=null)cloud.appendSceneCloud(scan.id,chunk,world,scan.color,isEffectivelyVisible(scan),reset);});}if(project.findNode(scan.id)==null)return;int total=published;scan.sourcePointCount=Math.max(scan.sourcePointCount,total);scan.loadState=ProjectModel.Scan.READY;runOnUiThread(()->{if(counter.get()!=generation||project.findNode(scan.id)==null)return;tree.refresh();syncScene();status.setText(scan.name+": "+total+" точек");});}catch(Exception error){scan.loadState=ProjectModel.Scan.ERROR;scan.loadError=error.getMessage();runOnUiThread(()->{if(counter.get()==generation&&project.findNode(scan.id)!=null){tree.refresh();status.setText(scan.name+" недоступен: "+error.getMessage());}});}});}
    private void restoreProjectScans(){for(ProjectModel.Scan scan:allScans())if(scan.uri!=null&&!scan.uri.isEmpty())decodeScene(Uri.parse(scan.uri),scan);}
    private void reloadAll(){for(ProjectModel.Scan scan:allScans())if(scan.uri!=null&&!scan.uri.isEmpty())decodeScene(Uri.parse(scan.uri),scan);}
    private File copyToCache(Uri uri,String name)throws IOException{File target=new File(getCacheDir(),name);try(InputStream input=getContentResolver().openInputStream(uri);FileOutputStream output=new FileOutputStream(target,false)){if(input==null)throw new IOException("нет доступа к выбранному файлу");byte[] buffer=new byte[1024*1024];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);}return target;}

    private void syncScene(){List<ProjectModel.Scan> scans=allScans();String[] live=new String[scans.size()];java.util.HashSet<String> keep=new java.util.HashSet<>();for(int i=0;i<scans.size();i++){ProjectModel.Scan scan=scans.get(i);live[i]=scan.id;keep.add(scan.id);cloud.setSceneCloudVisible(scan.id,isEffectivelyVisible(scan));cloud.setSceneCloudTransform(scan.id,scan.worldTransform());}for(String id:new ArrayList<>(sceneGenerations.keySet()))if(!keep.contains(id)){AtomicInteger generation=sceneGenerations.remove(id);if(generation!=null)generation.incrementAndGet();NativePreviewSession session=sessions.remove(id);if(session!=null)session.close();localFiles.remove(id);}cloud.retainSceneClouds(live);ProjectModel.Node moving=project.findNode(project.movingNodeId);cloud.setActiveSceneTarget(scanIds(moving),moving==null?null:moving.worldTransform());}
    private boolean isEffectivelyVisible(ProjectModel.Node node){for(ProjectModel.Node n=node;n!=null;n=n.parent())if(!n.visible)return false;return true;}
    private List<ProjectModel.Scan> allScans(){List<ProjectModel.Scan> out=new ArrayList<>();collectScans(project.root,out,false);return out;}
    private List<ProjectModel.Scan> readyScans(){List<ProjectModel.Scan> out=new ArrayList<>();collectScans(project.root,out,true);return out;}
    private void collectScans(ProjectModel.Group group,List<ProjectModel.Scan> out,boolean readyOnly){for(ProjectModel.Node node:group.children())if(node instanceof ProjectModel.Scan){ProjectModel.Scan scan=(ProjectModel.Scan)node;if(!readyOnly||(scan.loadState==ProjectModel.Scan.READY&&isEffectivelyVisible(scan)))out.add(scan);}else collectScans((ProjectModel.Group)node,out,readyOnly);}
    private String[] scanIds(ProjectModel.Node node){if(node==null)return new String[0];List<ProjectModel.Scan> scans=scansUnder(node,false);String[] ids=new String[scans.size()];for(int i=0;i<ids.length;i++)ids[i]=scans.get(i).id;return ids;}
    private List<ProjectModel.Scan> scansUnder(ProjectModel.Node node,boolean readyVisible){List<ProjectModel.Scan> out=new ArrayList<>();if(node instanceof ProjectModel.Scan){ProjectModel.Scan scan=(ProjectModel.Scan)node;if(!readyVisible||(scan.loadState==ProjectModel.Scan.READY&&isEffectivelyVisible(scan)))out.add(scan);}else if(node instanceof ProjectModel.Group)collectScans((ProjectModel.Group)node,out,readyVisible);return out;}

    private void updateRoleUi(){ProjectModel.Node reference=project.findNode(project.referenceNodeId),moving=project.findNode(project.movingNodeId);referenceLabel.setText("R  "+nodeLabel(reference));movingLabel.setText("M  "+nodeLabel(moving));registerButton.setEnabled(project.canRegister()&&nodesReady(reference,moving));if(moving==null)transformSummary.setText("M не выбран");else{float[] t=moving.worldTransform();transformSummary.setText(String.format(Locale.US,"X %.3f  Y %.3f  Z %.3f · RZ %.2f°",t[0],t[1],t[2],t[3]));}cloud.setActiveSceneTarget(scanIds(moving),moving==null?null:moving.worldTransform());}
    private String nodeLabel(ProjectModel.Node node){return node==null?"не выбран":node.name+(node instanceof ProjectModel.Group?" · связка "+node.scanCount():"");}
    private boolean nodesReady(ProjectModel.Node...nodes){for(ProjectModel.Node node:nodes)if(node==null||scansUnder(node,true).isEmpty())return false;return true;}

    private void applyMovingWorldTransform(float[] world){ProjectModel.Node moving=project.findNode(project.movingNodeId);if(moving==null)return;float[] local=moving.parent()==null?world:ProjectModel.relative(moving.parent().worldTransform(),world);System.arraycopy(local,0,moving.transform,0,4);changed();syncScene();updateRoleUi();}
    private void resetMoving(){ProjectModel.Node moving=project.findNode(project.movingNodeId);if(moving==null){status.setText("Сначала выберите M");return;}Arrays.fill(moving.transform,0);changed();syncScene();updateRoleUi();}
    private String snapshotKey(){return "transform-"+project.id+"-"+project.movingNodeId;}
    private void saveMovingSnapshot(){ProjectModel.Node moving=project.findNode(project.movingNodeId);if(moving==null){status.setText("Сначала выберите M");return;}getPreferences(MODE_PRIVATE).edit().putString(snapshotKey(),join(moving.transform)).apply();status.setText("Трансформация M сохранена");}
    private void restoreMovingSnapshot(){ProjectModel.Node moving=project.findNode(project.movingNodeId);if(moving==null){status.setText("Сначала выберите M");return;}String[] p=getPreferences(MODE_PRIVATE).getString(snapshotKey(),"").split(",");if(p.length!=4){status.setText("Сохранённой трансформации нет");return;}try{for(int i=0;i<4;i++)moving.transform[i]=Float.parseFloat(p[i]);changed();syncScene();updateRoleUi();status.setText("Трансформация восстановлена");}catch(NumberFormatException e){status.setText("Сохранённая трансформация повреждена");}}
    private String join(float[] values){return values[0]+","+values[1]+","+values[2]+","+values[3];}

    private void startRegistration(){ProjectModel.Node reference=project.findNode(project.referenceNodeId),moving=project.findNode(project.movingNodeId);if(!project.canRegister()||!nodesReady(reference,moving)){status.setText("Выберите готовые R и M");return;}double rms=readPositive(rmsLimit),p95=readPositive(p95Limit);if(!Double.isFinite(rms)||!Double.isFinite(p95))return;int generation=registrationGeneration.incrementAndGet();float[] movingWorld=moving.worldTransform();setRegistrationRunning(true);status.setText("Точная сшивка: подготовка связок…");worker.execute(()->{try{float[] ref=buildRegistrationCloud(reference,null);float[] mov=buildRegistrationCloud(moving,movingWorld);float[] pivot=centerOf(mov);float[] solverInitial=RegistrationTransform.toPivot(movingWorld,pivot);RegistrationResult result=TzfNative.registerPointClouds(ref,mov,solverInitial,rms,p95);runOnUiThread(()->finishRegistration(generation,result,pivot));}catch(Exception error){runOnUiThread(()->{if(registrationGeneration.get()==generation){setRegistrationRunning(false);status.setText("Ошибка точной сшивки: "+error.getMessage());}});}});}
    private float[] buildRegistrationCloud(ProjectModel.Node node,float[] relativeRoot)throws IOException{List<ProjectModel.Scan> scans=scansUnder(node,true);if(scans.isEmpty())throw new IOException("в узле нет готовых видимых сканов");int quota=Math.max(20_000,400_000/scans.size());List<float[]> parts=new ArrayList<>();int total=0;for(ProjectModel.Scan scan:scans){File file=localFiles.get(scan.id);if(file==null)throw new IOException(scan.name+" не загружен");float[] xyz=TzfNative.decodePreview(file.getAbsolutePath(),quota,1);float[] pose=relativeRoot==null?scan.worldTransform():ProjectModel.relative(relativeRoot,scan.worldTransform());applyPose(xyz,pose);parts.add(xyz);total+=xyz.length;}float[] result=new float[total];int offset=0;for(float[] part:parts){System.arraycopy(part,0,result,offset,part.length);offset+=part.length;}return result;}
    private static void applyPose(float[] xyz,float[] pose){double r=Math.toRadians(pose[3]),c=Math.cos(r),s=Math.sin(r);for(int i=0;i<xyz.length;i+=3){float x=xyz[i],y=xyz[i+1];xyz[i]=(float)(c*x-s*y)+pose[0];xyz[i+1]=(float)(s*x+c*y)+pose[1];xyz[i+2]+=pose[2];}}
    private static float[] centerOf(float[] xyz){float[] b=SceneBounds.of(xyz);return new float[]{(b[0]+b[3])*.5f,(b[1]+b[4])*.5f,(b[2]+b[5])*.5f};}
    private void finishRegistration(int generation,RegistrationResult result,float[] pivot){if(registrationGeneration.get()!=generation)return;setRegistrationRunning(false);if(!result.accepted){status.setText("Сшивка отклонена: "+registrationReason(result.reason));return;}ProjectModel.Node moving=project.findNode(project.movingNodeId);if(moving==null)return;float[] world=RegistrationTransform.fromPivot(result.transform,pivot);float[] local=ProjectModel.relative(moving.parent().worldTransform(),world);System.arraycopy(local,0,moving.transform,0,4);changed();syncScene();updateRoleUi();status.setText(String.format(Locale.US,"Принято · RMS %.3f · P95 %.3f · %.0f%% · %d ит.",result.rms,result.p95,result.overlap*100,result.iterations));}
    private void cancelRegistration(){registrationGeneration.incrementAndGet();setRegistrationRunning(false);status.setText("Сшивка отменена");}
    private void setRegistrationRunning(boolean running){registerButton.setEnabled(!running&&project!=null&&project.canRegister());cancelButton.setVisibility(running?View.VISIBLE:View.GONE);registrationProgress.setVisibility(running?View.VISIBLE:View.GONE);}
    private double readPositive(EditText field){try{double v=Double.parseDouble(field.getText().toString());if(v>0){field.setError(null);return v;}}catch(NumberFormatException ignored){}field.setError("Введите положительное число");return Double.NaN;}
    private String registrationReason(String reason){if(reason.contains("overlap"))return "недостаточное перекрытие";if("degenerate geometry".equals(reason))return "неоднозначная геометрия";if(reason.contains("RMS"))return "RMS выше допуска";if(reason.contains("P95"))return "P95 выше допуска";if(reason.contains("points")||reason.contains("coverage"))return "недостаточно точек";return reason;}

    private int effectiveBudget(){return project.pointBudget<0?1_200_000:project.pointBudget;}
    private String budgetLabel(int budget){if(budget<0)return "AUTO";if(budget>=1_000_000)return String.format(Locale.US,"%.1fM",budget/1_000_000f);return budget/1000+"k";}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void changed(){project.touch(System.currentTimeMillis());try{store.save(project);}catch(IOException error){status.setText("Не удалось сохранить проект: "+error.getMessage());}}
    @Override protected void onPause(){changed();cloud.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(cloud!=null)cloud.onResume();}
    @Override protected void onDestroy(){registrationGeneration.incrementAndGet();for(AtomicInteger generation:sceneGenerations.values())generation.incrementAndGet();for(NativePreviewSession session:sessions.values())session.close();sessions.clear();worker.shutdownNow();if(updater!=null)updater.close();super.onDestroy();}
}
