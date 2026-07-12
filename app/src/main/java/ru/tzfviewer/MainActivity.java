package ru.tzfviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.text.InputType;
import android.widget.PopupMenu;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
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
    private static final int[] SCAN_COLORS={0xff38c9e8,0xffffb44a,0xff8ee06f,0xffd58cff,0xffff718a,0xffffed61,0xff5f8cff,0xff63e6be};
    private static final double DEFAULT_RMS_LIMIT=3.0,DEFAULT_P95_LIMIT=8.0;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger registrationGeneration=new AtomicInteger();
    private final ConcurrentHashMap<String,AtomicInteger> sceneGenerations=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,NativePreviewSession> sessions=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,File> localFiles=new ConcurrentHashMap<>();
    private final Random random=new Random();
    private ProjectStore store;
    private ProjectModel project;
    private PointCloudView cloud;
    private ScanTreePanel tree;
    private TextView status,referenceLabel,movingLabel,transformSummary;
    private Button cancelButton,applyCandidateButton,rejectCandidateButton,pointSizeButton,budgetButton,stitchMenuButton,scanX7Button;
    private ProgressBar registrationProgress;
    private View scanPanel,settingsPanel;
    private int budgetIndex,pointSizeIndex;
    private boolean stitchingActionsEnabled=true;
    private float[] candidateWorld,candidateOriginalWorld;
    private List<ProjectModel.Scan> candidateGraphScans;
    private float[] candidateGraphWorld;
    private boolean x7Running;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        store=new ProjectStore(getFilesDir());String id=getIntent().getStringExtra(EXTRA_PROJECT_ID);if(id==null){finish();return;}
        try{project=store.load(id);}catch(Exception error){finish();return;}
        setContentView(R.layout.activity_main);
        cloud=new PointCloudView(this);
        ((FrameLayout)findViewById(R.id.viewportContainer)).addView(cloud,0,new FrameLayout.LayoutParams(-1,-1));
        bindViews();bindCamera();bindTools();bindTree();
        cloud.setTransformListener(this::applyMovingWorldTransform);
        cloud.setPointSize(project.pointSize);cloud.setGridVisible(project.gridVisible);cloud.restoreOrientation(project.cameraYaw,project.cameraPitch);if(project.orthographic)cloud.toggleProjection();
        updateRoleUi();syncScene();restoreProjectScans();sizeOverlays();
    }

    private void bindViews(){
        ((TextView)findViewById(R.id.projectTitle)).setText(project.name);
        status=findViewById(R.id.status);referenceLabel=findViewById(R.id.referenceLabel);movingLabel=findViewById(R.id.movingLabel);transformSummary=findViewById(R.id.transformSummary);
        cancelButton=findViewById(R.id.cancelRegistration);applyCandidateButton=findViewById(R.id.applyCandidate);rejectCandidateButton=findViewById(R.id.rejectCandidate);registrationProgress=findViewById(R.id.registrationProgress);stitchMenuButton=findViewById(R.id.moreTools);
        scanPanel=findViewById(R.id.scanPanel);settingsPanel=findViewById(R.id.settingsPanel);tree=findViewById(R.id.scanTree);pointSizeButton=findViewById(R.id.pointSize);budgetButton=findViewById(R.id.pointBudget);scanX7Button=findViewById(R.id.scanX7);
    }

    private void bindCamera(){
        OrientationCubeView cube=findViewById(R.id.orientation);cube.setListener(new OrientationCubeView.Listener(){public void onPreset(float yaw,float pitch){cloud.setPreset(yaw,pitch);}public void onRotate(float dy,float dp){cloud.rotateCamera(dy,dp);}});
        cloud.setOrientationListener((yaw,pitch)->{cube.setOrientation(yaw,pitch);project.cameraYaw=yaw;project.cameraPitch=pitch;project.touch(System.currentTimeMillis());});
    }

    private void bindTools(){
        findViewById(R.id.compactOpen).setOnClickListener(v->selectTzf());
        scanX7Button.setOnClickListener(v->promptX7Connection());
        findViewById(R.id.backToProjects).setOnClickListener(v->navigateToProjects());
        findViewById(R.id.compactFit).setOnClickListener(v->{if(readyScans().isEmpty())status.setText("Нет видимых готовых облаков");else cloud.fitView();});
        findViewById(R.id.scans).setOnClickListener(v->togglePanel(scanPanel,settingsPanel));findViewById(R.id.closeScans).setOnClickListener(v->scanPanel.setVisibility(View.GONE));
        stitchMenuButton.setOnClickListener(this::showStitchingMenu);findViewById(R.id.closeSettings).setOnClickListener(v->settingsPanel.setVisibility(View.GONE));
        findViewById(R.id.pairDetails).setOnClickListener(v->togglePanel(settingsPanel,scanPanel));
        findViewById(R.id.compactProjection).setOnClickListener(v->{project.orthographic=!project.orthographic;cloud.toggleProjection();changed();});
        findViewById(R.id.grid).setOnClickListener(v->{project.gridVisible=!project.gridVisible;cloud.setGridVisible(project.gridVisible);changed();});
        for(int i=0;i<POINT_BUDGETS.length;i++)if(POINT_BUDGETS[i]==project.pointBudget)budgetIndex=i;for(int i=0;i<POINT_SIZES.length;i++)if(POINT_SIZES[i]==project.pointSize)pointSizeIndex=i;
        pointSizeButton.setText("•• "+project.pointSize);budgetButton.setText(budgetLabel(project.pointBudget));
        pointSizeButton.setOnClickListener(v->{pointSizeIndex=(pointSizeIndex+1)%POINT_SIZES.length;project.pointSize=POINT_SIZES[pointSizeIndex];cloud.setPointSize(project.pointSize);pointSizeButton.setText("•• "+project.pointSize);changed();});
        budgetButton.setOnClickListener(v->{budgetIndex=(budgetIndex+1)%POINT_BUDGETS.length;project.pointBudget=POINT_BUDGETS[budgetIndex];budgetButton.setText(budgetLabel(project.pointBudget));reloadAll();changed();});
        Button measure=findViewById(R.id.measure);measure.setOnClickListener(v->{measure.setSelected(!measure.isSelected());cloud.setMeasureMode(measure.isSelected(),(length,dz)->status.setText(String.format(Locale.US,"Расстояние %.3f · ΔZ %.3f",length,dz)));});
        cancelButton.setOnClickListener(v->cancelRegistration());applyCandidateButton.setOnClickListener(v->applyRegistrationCandidate());rejectCandidateButton.setOnClickListener(v->rejectRegistrationCandidate());
        findViewById(R.id.resetTransform).setOnClickListener(v->resetMoving());findViewById(R.id.saveTransform).setOnClickListener(v->saveMovingSnapshot());findViewById(R.id.restoreTransform).setOnClickListener(v->restoreMovingSnapshot());
        setRegistrationRunning(false);
    }

    private void bindTree(){tree.bind(project,new ScanTreePanel.Listener(){public void changed(){rejectRegistrationCandidate();MainActivity.this.changed();syncScene();updateRoleUi();}public void visibilityChanged(ProjectModel.Node node){rejectRegistrationCandidate();syncScene();}public void rolesChanged(){rejectRegistrationCandidate();updateRoleUi();syncScene();}});}
    private void sizeOverlays(){findViewById(R.id.viewportContainer).post(()->{int max=Math.round(findViewById(R.id.viewportContainer).getHeight()*.35f),cap=dp(220),height=Math.min(cap,max);scanPanel.getLayoutParams().height=height;settingsPanel.getLayoutParams().height=height;scanPanel.requestLayout();settingsPanel.requestLayout();});}
    private void togglePanel(View show,View hide){hide.setVisibility(View.GONE);show.setVisibility(show.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);}
    private void showStitchingMenu(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,1,0,"Авто — поиск совпадения");
        menu.getMenu().add(0,2,1,"Уточнение — довести текущую позу");
        ProjectModel.Node moving=project.findNode(project.movingNodeId);if(moving instanceof ProjectModel.Group&&scansUnder(moving,true).size()>1)menu.getMenu().add(0,3,2,"Группа — сшить все станции");
        menu.setOnMenuItemClickListener(item->{if(item.getItemId()==1)startDeepRegistration();else if(item.getItemId()==2)startRegistration();else if(item.getItemId()==3)startGroupRegistration();return true;});
        menu.show();
    }

    private void promptX7Connection(){
        android.content.SharedPreferences preferences=getSharedPreferences("x7",MODE_PRIVATE);
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);int pad=dp(18);form.setPadding(pad,0,pad,0);
        EditText host=new EditText(this);host.setHint("IP X7");host.setText(preferences.getString("host","192.168.43.1"));form.addView(host);
        EditText user=new EditText(this);user.setHint("FTP логин X7");user.setText(preferences.getString("user",""));form.addView(user);
        EditText password=new EditText(this);password.setHint("FTP пароль X7");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setText(preferences.getString("password",""));form.addView(password);
        new AlertDialog.Builder(this).setTitle("Подключить Trimble X7").setMessage("Телефон должен быть подключён к Wi‑Fi X7.").setView(form).setNegativeButton("Отмена",null).setPositiveButton("Подключить",(d,w)->{
            String h=host.getText().toString().trim(),u=user.getText().toString().trim(),p=password.getText().toString();
            if(h.isEmpty()||u.isEmpty()||p.isEmpty()){status.setText("Укажите IP и FTP доступ X7");return;}
            preferences.edit().putString("host",h).putString("user",u).putString("password",p).apply();chooseX7Project(new TrimbleX7Client(h,u,p));
        }).show();
    }

    private void chooseX7Project(TrimbleX7Client client){
        setX7Running(true);status.setText("X7: получаем проекты…");worker.execute(()->{try{List<TrimbleX7Client.Project> projects=client.projects();if(projects.isEmpty())throw new IOException("на X7 нет проектов");String[] labels=new String[projects.size()];for(int i=0;i<labels.length;i++)labels[i]=projects.get(i).name;runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Проект на Trimble X7").setItems(labels,(d,which)->runX7Scan(client,projects.get(which))).setOnCancelListener(d->setX7Running(false)).show());}catch(Exception error){finishX7Error(error);}});
    }

    private void runX7Scan(TrimbleX7Client client,TrimbleX7Client.Project scannerProject){
        worker.execute(()->{try{
            runOnUiThread(()->status.setText("X7: запускаем скан…"));
            org.json.JSONObject configuration=client.configuration(scannerProject);TrimbleX7Client.Task task=client.start(configuration);
            if(task.id<0)throw new IOException("X7 не вернул ID задачи");
            long deadline=System.currentTimeMillis()+12*60_000L;
            while(true){if(System.currentTimeMillis()>deadline)throw new IOException("превышено время ожидания скана");TrimbleX7Client.Task current=client.task(task.id);if(current.failed())throw new IOException("X7 сообщил ошибку сканирования");final int percent=Math.max(0,Math.min(100,current.percent));runOnUiThread(()->status.setText("X7: сканирование "+percent+"%"));if(current.complete()){task=current;break;}Thread.sleep(1_000);}
            TrimbleX7Client.Scan scan=client.scan(scannerProject,task.scanId);client.markForDownload(scan);
            File directory=new File(new File(new File(getFilesDir(),"projects"),project.id),"scans");if(!directory.exists()&&!directory.mkdirs())throw new IOException("не удалось создать папку проекта");
            String filename=new File(scan.file).getName();if(!filename.toLowerCase(Locale.ROOT).endsWith(".tzf"))throw new IOException("X7 вернул недопустимое имя TZF");File destination=new File(directory,"x7-"+scan.id+"-"+filename);
            client.download(scan,destination,(phase,percent)->runOnUiThread(()->status.setText("X7: "+phase+" "+percent+"%")));
            runOnUiThread(()->{ProjectModel.Scan local=rememberScan(Uri.fromFile(destination),destination.getName());changed();tree.refresh();decodeScene(Uri.fromFile(destination),local);status.setText("X7: файл сохранён, открываем…");setX7Running(false);});
        }catch(Exception error){finishX7Error(error);}});
    }

    private void finishX7Error(Exception error){runOnUiThread(()->{setX7Running(false);String message=error.getMessage();status.setText("X7: "+(message==null?"ошибка подключения":message));});}
    private void setX7Running(boolean running){x7Running=running;if(scanX7Button!=null)scanX7Button.setEnabled(!running);}

    private void selectTzf(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,PICK_TZF);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK_TZF||result!=RESULT_OK||data==null)return;List<Uri> uris=new ArrayList<>();ClipData clip=data.getClipData();if(clip!=null)for(int i=0;i<clip.getItemCount();i++)uris.add(clip.getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());int added=0;for(Uri uri:uris){String name=displayName(uri);if(!name.toLowerCase(Locale.ROOT).endsWith(".tzf")){status.setText("Пропущен не-TZF файл: "+name);continue;}try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}ProjectModel.Scan scan=rememberScan(uri,name);if(scan.loadState!=ProjectModel.Scan.READY){decodeScene(uri,scan);added++;}}changed();tree.refresh();status.setText("Добавлено в очередь: "+added);}

    private ProjectModel.Scan rememberScan(Uri uri,String name){for(ProjectModel.Scan scan:allScans())if(uri.toString().equals(scan.uri))return scan;ProjectModel.Scan scan=new ProjectModel.Scan(UUID.randomUUID().toString(),name);scan.uri=uri.toString();scan.color=nextScanColor();project.root.add(scan);return scan;}
    private int nextScanColor(){HashSet<Integer> used=new HashSet<>();for(ProjectModel.Scan scan:allScans())used.add(scan.color);int[] choices=new int[SCAN_COLORS.length];int count=0;for(int color:SCAN_COLORS)if(!used.contains(color))choices[count++]=color;if(count==0){System.arraycopy(SCAN_COLORS,0,choices,0,SCAN_COLORS.length);count=choices.length;}return choices[random.nextInt(count)];}
    private String displayName(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}String name=uri.getLastPathSegment();return name==null?"Scan.tzf":name.substring(Math.max(name.lastIndexOf('/')+1,name.lastIndexOf(':')+1));}

    private void decodeScene(Uri uri,ProjectModel.Scan scan){AtomicInteger counter=sceneGenerations.computeIfAbsent(scan.id,k->new AtomicInteger());int generation=counter.incrementAndGet();scan.loadState=ProjectModel.Scan.LOADING;scan.loadError="";tree.refresh();worker.execute(()->{try{File local=copyToCache(uri,"scene-"+scan.id+".tzf");if(project.findNode(scan.id)==null)return;localFiles.put(scan.id,local);NativePreviewSession old=sessions.remove(scan.id);if(old!=null)old.close();NativePreviewSession session=new NativePreviewSession(local.getAbsolutePath());sessions.put(scan.id,session);int quota=Math.max(20_000,effectiveBudget()/Math.max(1,project.scanCount()));session.prepare(quota);boolean first=true;int published=0;while(counter.get()==generation&&project.findNode(scan.id)!=null){float[] chunk=session.nextChunk(100_000);if(chunk.length==0)break;boolean reset=first;first=false;published+=chunk.length/3;float[] world=scan.worldTransform();runOnUiThread(()->{if(counter.get()==generation&&project.findNode(scan.id)!=null)cloud.appendSceneCloud(scan.id,chunk,world,scan.color,isEffectivelyVisible(scan),reset);});}if(project.findNode(scan.id)==null)return;int total=published;scan.sourcePointCount=Math.max(scan.sourcePointCount,total);scan.loadState=ProjectModel.Scan.READY;runOnUiThread(()->{if(counter.get()!=generation||project.findNode(scan.id)==null)return;tree.refresh();syncScene();status.setText(scan.name+": "+total+" точек");});}catch(Exception error){scan.loadState=ProjectModel.Scan.ERROR;scan.loadError=error.getMessage();runOnUiThread(()->{if(counter.get()==generation&&project.findNode(scan.id)!=null){tree.refresh();status.setText(scan.name+" недоступен: "+error.getMessage());}});}});}
    private void restoreProjectScans(){for(ProjectModel.Scan scan:allScans())if(scan.uri!=null&&!scan.uri.isEmpty())decodeScene(Uri.parse(scan.uri),scan);}
    private void reloadAll(){for(ProjectModel.Scan scan:allScans())if(scan.uri!=null&&!scan.uri.isEmpty())decodeScene(Uri.parse(scan.uri),scan);}
    private File copyToCache(Uri uri,String name)throws IOException{File target=new File(getCacheDir(),name);InputStream source="file".equals(uri.getScheme())?new java.io.FileInputStream(new File(uri.getPath())):getContentResolver().openInputStream(uri);try(InputStream input=source;FileOutputStream output=new FileOutputStream(target,false)){if(input==null)throw new IOException("нет доступа к выбранному файлу");byte[] buffer=new byte[1024*1024];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);}return target;}

    private void syncScene(){List<ProjectModel.Scan> scans=allScans();String[] live=new String[scans.size()];java.util.HashSet<String> keep=new java.util.HashSet<>();for(int i=0;i<scans.size();i++){ProjectModel.Scan scan=scans.get(i);live[i]=scan.id;keep.add(scan.id);cloud.setSceneCloudVisible(scan.id,isEffectivelyVisible(scan));cloud.setSceneCloudTransform(scan.id,scan.worldTransform());}for(String id:new ArrayList<>(sceneGenerations.keySet()))if(!keep.contains(id)){AtomicInteger generation=sceneGenerations.remove(id);if(generation!=null)generation.incrementAndGet();NativePreviewSession session=sessions.remove(id);if(session!=null)session.close();localFiles.remove(id);}cloud.retainSceneClouds(live);ProjectModel.Node moving=project.findNode(project.movingNodeId);cloud.setActiveSceneTarget(moving==null?null:moving.id,scanIds(moving),moving==null?null:moving.worldTransform());}
    private boolean isEffectivelyVisible(ProjectModel.Node node){for(ProjectModel.Node n=node;n!=null;n=n.parent())if(!n.visible)return false;return true;}
    private List<ProjectModel.Scan> allScans(){List<ProjectModel.Scan> out=new ArrayList<>();collectScans(project.root,out,false);return out;}
    private List<ProjectModel.Scan> readyScans(){List<ProjectModel.Scan> out=new ArrayList<>();collectScans(project.root,out,true);return out;}
    private void collectScans(ProjectModel.Group group,List<ProjectModel.Scan> out,boolean readyOnly){for(ProjectModel.Node node:group.children())if(node instanceof ProjectModel.Scan){ProjectModel.Scan scan=(ProjectModel.Scan)node;if(!readyOnly||(scan.loadState==ProjectModel.Scan.READY&&isEffectivelyVisible(scan)))out.add(scan);}else collectScans((ProjectModel.Group)node,out,readyOnly);}
    private String[] scanIds(ProjectModel.Node node){if(node==null)return new String[0];List<ProjectModel.Scan> scans=scansUnder(node,false);String[] ids=new String[scans.size()];for(int i=0;i<ids.length;i++)ids[i]=scans.get(i).id;return ids;}
    private List<ProjectModel.Scan> scansUnder(ProjectModel.Node node,boolean readyVisible){List<ProjectModel.Scan> out=new ArrayList<>();if(node instanceof ProjectModel.Scan){ProjectModel.Scan scan=(ProjectModel.Scan)node;if(!readyVisible||(scan.loadState==ProjectModel.Scan.READY&&isEffectivelyVisible(scan)))out.add(scan);}else if(node instanceof ProjectModel.Group)collectScans((ProjectModel.Group)node,out,readyVisible);return out;}

    private void updateRoleUi(){ProjectModel.Node reference=project.findNode(project.referenceNodeId),moving=project.findNode(project.movingNodeId);referenceLabel.setText("R  "+nodeLabel(reference));movingLabel.setText("M  "+nodeLabel(moving));stitchMenuButton.setEnabled(stitchingActionsEnabled&&project.canRegister()&&nodesReady(reference,moving));if(moving==null)transformSummary.setText("M не выбран");else{float[] t=moving.worldTransform();transformSummary.setText(String.format(Locale.US,"X %.3f  Y %.3f  Z %.3f · RZ %.2f°",t[0],t[1],t[2],t[3]));}cloud.setActiveSceneTarget(moving==null?null:moving.id,scanIds(moving),moving==null?null:moving.worldTransform());}
    private String nodeLabel(ProjectModel.Node node){return node==null?"не выбран":node.name+(node instanceof ProjectModel.Group?" · связка "+node.scanCount():"");}
    private boolean nodesReady(ProjectModel.Node...nodes){for(ProjectModel.Node node:nodes)if(node==null||scansUnder(node,true).isEmpty())return false;return true;}

    private void applyMovingWorldTransform(String targetNodeId,float[] world){if(targetNodeId==null||!targetNodeId.equals(project.movingNodeId))return;ProjectModel.Node moving=project.findNode(targetNodeId);if(moving==null||moving.id.equals(project.referenceNodeId))return;float[] local=moving.parent()==null?world:ProjectModel.relative(moving.parent().worldTransform(),world);System.arraycopy(local,0,moving.transform,0,4);changed();syncScene();updateRoleUi();}
    private void resetMoving(){ProjectModel.Node moving=project.findNode(project.movingNodeId);if(moving==null){status.setText("Сначала выберите M");return;}Arrays.fill(moving.transform,0);changed();syncScene();updateRoleUi();}
    private String snapshotKey(){return "transform-"+project.id+"-"+project.movingNodeId;}
    private void saveMovingSnapshot(){ProjectModel.Node moving=project.findNode(project.movingNodeId);if(moving==null){status.setText("Сначала выберите M");return;}getPreferences(MODE_PRIVATE).edit().putString(snapshotKey(),join(moving.transform)).apply();status.setText("Трансформация M сохранена");}
    private void restoreMovingSnapshot(){ProjectModel.Node moving=project.findNode(project.movingNodeId);if(moving==null){status.setText("Сначала выберите M");return;}String[] p=getPreferences(MODE_PRIVATE).getString(snapshotKey(),"").split(",");if(p.length!=4){status.setText("Сохранённой трансформации нет");return;}try{for(int i=0;i<4;i++)moving.transform[i]=Float.parseFloat(p[i]);changed();syncScene();updateRoleUi();status.setText("Трансформация восстановлена");}catch(NumberFormatException e){status.setText("Сохранённая трансформация повреждена");}}
    private String join(float[] values){return values[0]+","+values[1]+","+values[2]+","+values[3];}

    private void startRegistration(){ProjectModel.Node reference=project.findNode(project.referenceNodeId),moving=project.findNode(project.movingNodeId);if(!project.canRegister()||!nodesReady(reference,moving)){status.setText("Выберите готовые R и M");return;}int generation=registrationGeneration.incrementAndGet();float[] movingWorld=moving.worldTransform();renderRegistrationState(RegistrationUiState.running(RegistrationUiState.Stage.PREPARING,5,"Подготовка"));status.setText("Уточнение: подготовка выборок…");worker.execute(()->{try{float[] ref=buildRegistrationCloud(reference,null,generation,5,18);float[] mov=buildRegistrationCloud(moving,movingWorld,generation,18,35);updateRegistrationProgress(generation,RegistrationUiState.Stage.REFINING,35,"Уточнение: точная доводка…");float[] pivot=centerOf(mov);float[] solverInitial=RegistrationTransform.toPivot(movingWorld,pivot);RegistrationResult result=TzfNative.registerPointClouds(ref,mov,solverInitial,DEFAULT_RMS_LIMIT,DEFAULT_P95_LIMIT);runOnUiThread(()->finishRegistration(generation,result,pivot));}catch(Exception error){runOnUiThread(()->{if(registrationGeneration.get()==generation){setRegistrationRunning(false);status.setText("Ошибка уточнения: "+error.getMessage());}});}});}
    private void startDeepRegistration(){ProjectModel.Node reference=project.findNode(project.referenceNodeId),moving=project.findNode(project.movingNodeId);if(!project.canRegister()||!nodesReady(reference,moving)){status.setText("Выберите готовые R и M");return;}rejectRegistrationCandidate();int generation=registrationGeneration.incrementAndGet();float[] movingWorld=moving.worldTransform();renderRegistrationState(RegistrationUiState.running(RegistrationUiState.Stage.PREPARING,5,"Подготовка"));status.setText("Авто: подготовка выборок…");worker.execute(()->{try{float[] ref=buildRegistrationCloud(reference,null,generation,5,15);float[] mov=buildRegistrationCloud(moving,movingWorld,generation,15,25);updateRegistrationProgress(generation,RegistrationUiState.Stage.SEARCHING,25,"Авто: признаки, гипотезы, проверка и уточнение…");float[] pivot=centerOf(mov);RegistrationResult result=TzfNative.registerPointCloudsGlobal(ref,mov,DEFAULT_RMS_LIMIT,DEFAULT_P95_LIMIT);runOnUiThread(()->finishDeepRegistration(generation,result,pivot,movingWorld));}catch(Exception error){runOnUiThread(()->{if(registrationGeneration.get()==generation){renderRegistrationState(RegistrationUiState.error(error.getMessage()));status.setText("Ошибка авто-сшивки: "+error.getMessage());}});}});}
    private void startGroupRegistration(){ProjectModel.Node node=project.findNode(project.movingNodeId);if(!(node instanceof ProjectModel.Group)){status.setText("Выберите группу M");return;}List<ProjectModel.Scan> scans=scansUnder(node,true);if(scans.size()<2){status.setText("В группе нужно минимум два готовых скана");return;}rejectRegistrationCandidate();int generation=registrationGeneration.incrementAndGet();renderRegistrationState(RegistrationUiState.running(RegistrationUiState.Stage.PREPARING,2,"Групповая сшивка"));worker.execute(()->{try{List<float[]> clouds=new ArrayList<>();float[] initial=new float[scans.size()*4];for(int i=0;i<scans.size();++i){ProjectModel.Scan scan=scans.get(i);clouds.add(buildRegistrationCloud(scan,scan.worldTransform(),generation,2,20));System.arraycopy(scan.worldTransform(),0,initial,i*4,4);}List<Float> encoded=new ArrayList<>();int pairs=scans.size()*(scans.size()-1)/2,done=0;for(int i=0;i<scans.size();++i)for(int j=i+1;j<scans.size();++j){if(registrationGeneration.get()!=generation)throw new IOException("отменено");updateRegistrationProgress(generation,RegistrationUiState.Stage.SEARCHING,20+Math.round(60f*done++/Math.max(1,pairs)),"Группа: "+scans.get(i).name+" ↔ "+scans.get(j).name);float[] movingCloud=clouds.get(j),pivot=centerOf(movingCloud);RegistrationResult pair=TzfNative.registerPointCloudsGlobal(clouds.get(i),movingCloud,DEFAULT_RMS_LIMIT,DEFAULT_P95_LIMIT);if(!pair.accepted)continue;float[] relative=RegistrationTransform.fromPivot(pair.transform,pivot);encoded.add((float)i);encoded.add((float)j);for(float value:relative)encoded.add(value);encoded.add((float)Math.max(.05,pair.overlap/(1+pair.rms)));}float[] edges=new float[encoded.size()];for(int i=0;i<edges.length;++i)edges[i]=encoded.get(i);updateRegistrationProgress(generation,RegistrationUiState.Stage.VALIDATING,85,"Группа: оптимизация графа");float[] optimized=TzfNative.optimizePoseGraph(initial,edges,0);runOnUiThread(()->finishGroupRegistration(generation,scans,optimized));}catch(Exception error){runOnUiThread(()->{if(registrationGeneration.get()==generation){renderRegistrationState(RegistrationUiState.error(error.getMessage()));status.setText("Групповая сшивка: "+registrationReason(error.getMessage()));}});}});}
    private void finishGroupRegistration(int generation,List<ProjectModel.Scan> scans,float[] optimized){if(registrationGeneration.get()!=generation)return;candidateGraphScans=new ArrayList<>(scans);candidateGraphWorld=optimized;for(int i=0;i<scans.size();++i)cloud.setSceneCloudTransform(scans.get(i).id,Arrays.copyOfRange(optimized,i*4,i*4+4));renderRegistrationState(RegistrationUiState.preview("Группа"));status.setText("Групповой кандидат готов · "+scans.size()+" станций · применить?");}
    private void updateRegistrationProgress(int generation,RegistrationUiState.Stage stage,int progress,String message){runOnUiThread(()->{if(registrationGeneration.get()==generation){renderRegistrationState(RegistrationUiState.running(stage,progress,message));status.setText(message);}});}
    private float[] buildRegistrationCloud(ProjectModel.Node node,float[] relativeRoot,int generation,int progressStart,int progressEnd)throws IOException{List<ProjectModel.Scan> scans=scansUnder(node,true);if(scans.isEmpty())throw new IOException("в узле нет готовых видимых сканов");int quota=Math.max(20_000,400_000/scans.size());List<float[]> parts=new ArrayList<>();int total=0;for(int scanIndex=0;scanIndex<scans.size();scanIndex++){if(registrationGeneration.get()!=generation)throw new IOException("отменено");ProjectModel.Scan scan=scans.get(scanIndex);File file=localFiles.get(scan.id);if(file==null)throw new IOException(scan.name+" не загружен");float[] pose=relativeRoot==null?scan.worldTransform():ProjectModel.relative(relativeRoot,scan.worldTransform());try(NativePreviewSession session=new NativePreviewSession(file.getAbsolutePath())){session.prepare(quota);int prepared=0;while(registrationGeneration.get()==generation){float[] chunk=session.nextChunk(50_000);if(chunk.length==0)break;prepared+=chunk.length/3;applyPose(chunk,pose);parts.add(chunk);total+=chunk.length;float fraction=(scanIndex+Math.min(1f,prepared/(float)quota))/scans.size();int progress=progressStart+Math.round((progressEnd-progressStart)*fraction);updateRegistrationProgress(generation,RegistrationUiState.Stage.PREPARING,progress,"Подготовка: "+scan.name+" · "+prepared+" точек");}}}if(registrationGeneration.get()!=generation)throw new IOException("отменено");float[] result=new float[total];int offset=0;for(float[] part:parts){System.arraycopy(part,0,result,offset,part.length);offset+=part.length;}return result;}
    private static void applyPose(float[] xyz,float[] pose){double r=Math.toRadians(pose[3]),c=Math.cos(r),s=Math.sin(r);for(int i=0;i<xyz.length;i+=3){float x=xyz[i],y=xyz[i+1];xyz[i]=(float)(c*x-s*y)+pose[0];xyz[i+1]=(float)(s*x+c*y)+pose[1];xyz[i+2]+=pose[2];}}
    private static float[] centerOf(float[] xyz){float[] b=SceneBounds.of(xyz);return new float[]{(b[0]+b[3])*.5f,(b[1]+b[4])*.5f,(b[2]+b[5])*.5f};}
    private void finishRegistration(int generation,RegistrationResult result,float[] pivot){if(registrationGeneration.get()!=generation)return;ProjectModel.Node moving=project.findNode(project.movingNodeId);if(!result.accepted||moving==null){setRegistrationRunning(false);status.setText("Уточнение: "+registrationReason(result.reason));return;}showRegistrationPreview(result,pivot,moving.worldTransform(),"Уточнение");}
    private void finishDeepRegistration(int generation,RegistrationResult result,float[] pivot,float[] originalWorld){if(registrationGeneration.get()!=generation)return;if(!result.accepted){renderRegistrationState(RegistrationUiState.error(result.reason));status.setText("Авто: "+registrationReason(result.reason));return;}showRegistrationPreview(result,pivot,originalWorld,"Авто");}
    private void showRegistrationPreview(RegistrationResult result,float[] pivot,float[] originalWorld,String mode){candidateOriginalWorld=originalWorld.clone();candidateWorld=RegistrationTransform.fromPivot(result.transform,pivot);showRegistrationCandidate();renderRegistrationState(RegistrationUiState.preview("Кандидат"));boolean warning=result.reason.startsWith("quality warning");String quality=warning?"предупреждение: качество ниже допуска":"кандидат";status.setTextColor(getColor(warning?R.color.amber:R.color.text_secondary));status.setText(String.format(Locale.US,"%s · %s · RMS %.3f · P95 %.3f · %.0f%% · применить?",mode,quality,result.rms,result.p95,result.overlap*100));}
    private void showRegistrationCandidate(){ProjectModel.Node moving=project.findNode(project.movingNodeId);if(moving==null||candidateWorld==null||candidateOriginalWorld==null)return;for(ProjectModel.Scan scan:scansUnder(moving,false)){float[] relative=ProjectModel.relative(candidateOriginalWorld,scan.worldTransform());cloud.setSceneCloudTransform(scan.id,ProjectModel.compose(candidateWorld,relative));}cloud.setActiveSceneTarget(moving.id,scanIds(moving),candidateWorld);}
    private void applyRegistrationCandidate(){if(candidateGraphScans!=null&&candidateGraphWorld!=null){for(int i=0;i<candidateGraphScans.size();++i){ProjectModel.Scan scan=candidateGraphScans.get(i);float[] world=Arrays.copyOfRange(candidateGraphWorld,i*4,i*4+4),local=ProjectModel.relative(scan.parent().worldTransform(),world);System.arraycopy(local,0,scan.transform,0,4);}candidateGraphScans=null;candidateGraphWorld=null;changed();syncScene();updateRoleUi();renderRegistrationState(RegistrationUiState.idle());status.setText("Групповая сшивка применена");return;}ProjectModel.Node moving=project.findNode(project.movingNodeId);if(moving==null||candidateWorld==null)return;float[] local=ProjectModel.relative(moving.parent().worldTransform(),candidateWorld);System.arraycopy(local,0,moving.transform,0,4);candidateWorld=candidateOriginalWorld=null;changed();syncScene();updateRoleUi();renderRegistrationState(RegistrationUiState.idle());status.setTextColor(getColor(R.color.text_secondary));status.setText("Кандидат сшивки применён");}
    private void rejectRegistrationCandidate(){if(candidateWorld==null&&candidateOriginalWorld==null&&candidateGraphScans==null)return;candidateWorld=candidateOriginalWorld=null;candidateGraphScans=null;candidateGraphWorld=null;syncScene();updateRoleUi();renderRegistrationState(RegistrationUiState.idle());status.setTextColor(getColor(R.color.text_secondary));status.setText("Кандидат отменён");}
    private void cancelRegistration(){registrationGeneration.incrementAndGet();TzfNative.cancelRegistration();setRegistrationRunning(false);status.setText("Сшивка отменена");}
    private void setRegistrationRunning(boolean running){renderRegistrationState(running?RegistrationUiState.running(RegistrationUiState.Stage.REFINING,50,"Точная сшивка"):RegistrationUiState.idle());}
    private void renderRegistrationState(RegistrationUiState state){stitchingActionsEnabled=state.actionsEnabled();ProjectModel.Node reference=project==null?null:project.findNode(project.referenceNodeId),moving=project==null?null:project.findNode(project.movingNodeId);stitchMenuButton.setEnabled(stitchingActionsEnabled&&project!=null&&project.canRegister()&&nodesReady(reference,moving));cancelButton.setVisibility(state.showCancel()?View.VISIBLE:View.GONE);applyCandidateButton.setVisibility(state.showDecision()?View.VISIBLE:View.GONE);rejectCandidateButton.setVisibility(state.showDecision()?View.VISIBLE:View.GONE);registrationProgress.setVisibility(state.showProgress()?View.VISIBLE:View.GONE);registrationProgress.setProgress(state.progress);boolean hideBase=state.running()||state.showDecision();referenceLabel.setVisibility(hideBase?View.GONE:View.VISIBLE);movingLabel.setVisibility(hideBase?View.GONE:View.VISIBLE);}
    private String registrationReason(String reason){if(reason.contains("ambiguous"))return "найдено несколько неоднозначных совпадений";if(reason.contains("global hypothesis"))return "надёжная гипотеза не найдена";if(reason.contains("moved too far")||reason.contains("rotated too far"))return "уточнение ушло слишком далеко от ручной позиции";if(reason.contains("overlap"))return "недостаточное перекрытие";if("degenerate geometry".equals(reason))return "неоднозначная геометрия";if(reason.contains("RMS"))return "RMS выше допуска";if(reason.contains("P95"))return "P95 выше допуска";if(reason.contains("points")||reason.contains("coverage"))return "недостаточно точек";return reason;}

    private int effectiveBudget(){return project.pointBudget<0?1_200_000:project.pointBudget;}
    private String budgetLabel(int budget){if(budget<0)return "AUTO";if(budget>=1_000_000)return String.format(Locale.US,"%.1fM",budget/1_000_000f);return budget/1000+"k";}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void changed(){project.touch(System.currentTimeMillis());try{store.save(project);}catch(IOException error){status.setText("Не удалось сохранить проект: "+error.getMessage());}}
    private void navigateToProjects(){changed();finish();}
    @Override public void onBackPressed(){if(candidateWorld!=null||candidateGraphScans!=null){rejectRegistrationCandidate();return;}if(scanPanel.getVisibility()==View.VISIBLE||settingsPanel.getVisibility()==View.VISIBLE){scanPanel.setVisibility(View.GONE);settingsPanel.setVisibility(View.GONE);return;}navigateToProjects();}
    @Override protected void onPause(){changed();cloud.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(cloud!=null)cloud.onResume();}
    @Override protected void onDestroy(){registrationGeneration.incrementAndGet();TzfNative.cancelRegistration();for(AtomicInteger generation:sceneGenerations.values())generation.incrementAndGet();for(NativePreviewSession session:sessions.values())session.close();sessions.clear();worker.shutdownNow();super.onDestroy();}
}
