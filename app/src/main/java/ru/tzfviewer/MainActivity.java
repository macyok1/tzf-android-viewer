package ru.tzfviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    static final String EXTRA_PROJECT_ID = "project_id";
    private static final int PICK_PRIMARY = 101, PICK_SECONDARY = 102, PICK_SCENE = 103;
    private static final int[] POINT_BUDGETS={150_000,300_000,600_000,1_200_000,2_500_000,5_000_000,10_000_000,-1};
    private static final String STATE_TRANSFORM = "transform";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger primaryGeneration = new AtomicInteger();
    private final AtomicInteger secondaryGeneration = new AtomicInteger();
    private final AtomicInteger registrationGeneration = new AtomicInteger();
    private final float[] transform = new float[4];
    private PointCloudView cloud;
    private TextView status, transformSummary;
    private EditText rmsLimit, p95Limit;
    private Button registerButton, cancelRegistration;
    private ProgressBar registrationProgress;
    private View viewerTools, stitchingTools;
    private volatile File primaryFile, secondaryFile;
    private ProjectStore projectStore;
    private ProjectModel project;
    private final int[] pointSizes={1,2,3,5};
    private int budgetIndex,pointSizeIndex;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        projectStore = new ProjectStore(getFilesDir());
        String projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        if (projectId == null) { finish(); return; }
        try { project = projectStore.load(projectId); }
        catch (IOException | IllegalArgumentException error) { finish(); return; }
        setContentView(R.layout.activity_main);
        cloud = new PointCloudView(this);
        cloud.setTransformListener(values -> {
            System.arraycopy(values, 0, transform, 0, 4);
            syncTransformSummary();
        });
        ((FrameLayout) findViewById(R.id.viewportContainer)).addView(cloud, 0,
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        status = findViewById(R.id.status);
        transformSummary = findViewById(R.id.transformSummary);
        rmsLimit = findViewById(R.id.rmsLimit);
        p95Limit = findViewById(R.id.p95Limit);
        registerButton = findViewById(R.id.registerScans);
        cancelRegistration = findViewById(R.id.cancelRegistration);
        registrationProgress = findViewById(R.id.registrationProgress);
        viewerTools = findViewById(R.id.viewerTools);
        stitchingTools = findViewById(R.id.stitchingTools);
        OrientationCubeView orientation = findViewById(R.id.orientation);
        orientation.setListener(new OrientationCubeView.Listener() {
            @Override public void onPreset(float yaw, float pitch) { cloud.setPreset(yaw, pitch); }
            @Override public void onRotate(float deltaYaw, float deltaPitch) { cloud.rotateCamera(deltaYaw, deltaPitch); }
        });
        cloud.setOrientationListener((yaw,pitch)->{orientation.setOrientation(yaw,pitch);project.cameraYaw=yaw;project.cameraPitch=pitch;});
        cloud.restoreOrientation(project.cameraYaw, project.cameraPitch);

        findViewById(R.id.modeViewer).setOnClickListener(v -> setMode(false));
        findViewById(R.id.modeStitching).setOnClickListener(v -> setMode(true));
        findViewById(R.id.openPrimary).setOnClickListener(v -> selectTzf(PICK_PRIMARY));
        findViewById(R.id.openReference).setOnClickListener(v -> selectTzf(PICK_PRIMARY));
        findViewById(R.id.openSecondary).setOnClickListener(v -> selectTzf(PICK_SECONDARY));
        findViewById(R.id.projection).setOnClickListener(v -> cloud.toggleProjection());
        findViewById(R.id.fit).setOnClickListener(v -> cloud.fitView());
        findViewById(R.id.compactOpen).setOnClickListener(v -> selectTzf(PICK_SCENE));
        findViewById(R.id.compactFit).setOnClickListener(v -> cloud.fitView());
        findViewById(R.id.compactProjection).setOnClickListener(v -> cloud.toggleProjection());
        Button pointSizeButton=findViewById(R.id.pointSize),budgetButton=findViewById(R.id.pointBudget),gridButton=findViewById(R.id.grid);
        for(int i=0;i<POINT_BUDGETS.length;i++)if(POINT_BUDGETS[i]==project.pointBudget)budgetIndex=i;
        for(int i=0;i<pointSizes.length;i++)if(pointSizes[i]==project.pointSize)pointSizeIndex=i;
        pointSizeButton.setOnClickListener(v->{pointSizeIndex=(pointSizeIndex+1)%pointSizes.length;project.pointSize=pointSizes[pointSizeIndex];cloud.setPointSize(project.pointSize);pointSizeButton.setText("•• "+project.pointSize);});
        budgetButton.setOnClickListener(v->{budgetIndex=(budgetIndex+1)%POINT_BUDGETS.length;project.pointBudget=POINT_BUDGETS[budgetIndex];budgetButton.setText(budgetLabel(project.pointBudget));reloadForBudget();});
        gridButton.setSelected(project.gridVisible);gridButton.setOnClickListener(v->{project.gridVisible=!project.gridVisible;gridButton.setSelected(project.gridVisible);cloud.setGridVisible(project.gridVisible);});
        ScanTreePanel tree=findViewById(R.id.scanTree);tree.bind(project,new ScanTreePanel.Listener(){public void changed(){project.touch(System.currentTimeMillis());tree.refresh();}public void visibility(ProjectModel.Node n,boolean v){cloud.setSceneCloudVisible(n.id,v);int i=project.root.children().indexOf(n);if(i==0)cloud.setPrimaryVisible(v);else if(i==1)cloud.setSecondaryVisible(v);}});View panel=findViewById(R.id.scanPanel);findViewById(R.id.scans).setOnClickListener(v->panel.setVisibility(panel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));findViewById(R.id.closeScans).setOnClickListener(v->panel.setVisibility(View.GONE));
        cloud.setPointSize(project.pointSize);cloud.setGridVisible(project.gridVisible);pointSizeButton.setText("•• "+project.pointSize);budgetButton.setText(budgetLabel(project.pointBudget));
        registerButton.setOnClickListener(v -> startRegistration());
        cancelRegistration.setOnClickListener(v -> cancelRegistration());
        Button measure = findViewById(R.id.measure);
        measure.setOnClickListener(v -> {
            measure.setSelected(!measure.isSelected());
            cloud.setMeasureMode(measure.isSelected(), (length, dz) -> status.setText(
                    String.format(Locale.US, "Расстояние %.3f · ΔZ %.3f", length, dz)));
        });
        ((CheckBox) findViewById(R.id.showPrimary)).setOnCheckedChangeListener(
                (button, checked) -> cloud.setPrimaryVisible(checked));
        ((CheckBox) findViewById(R.id.showSecondary)).setOnCheckedChangeListener(
                (button, checked) -> cloud.setSecondaryVisible(checked));
        findViewById(R.id.resetTransform).setOnClickListener(v -> {
            Arrays.fill(transform, 0f); syncTransform();
        });
        findViewById(R.id.saveTransform).setOnClickListener(v -> saveTransform());
        findViewById(R.id.restoreTransform).setOnClickListener(v -> restoreTransform());
        if (state != null && state.getFloatArray(STATE_TRANSFORM) != null) {
            float[] saved = state.getFloatArray(STATE_TRANSFORM);
            System.arraycopy(saved, 0, transform, 0, Math.min(4, saved.length));
        }
        setMode(false);
        setRegistrationRunning(false);
        syncTransform();
    }

    private void setMode(boolean stitching) {
        viewerTools.setVisibility(View.GONE);
        stitchingTools.setVisibility(stitching ? View.VISIBLE : View.GONE);
        findViewById(R.id.modeViewer).setSelected(!stitching);
        findViewById(R.id.modeStitching).setSelected(stitching);
    }

    private String budgetLabel(int budget){if(budget<0)return "AUTO";if(budget>=1_000_000)return String.format(Locale.US,"%.1fM",budget/1_000_000f);return (budget/1000)+"k";}
    private int effectiveBudget(){return project.pointBudget<0?1_200_000:project.pointBudget;}
    private void reloadForBudget(){int visible=(primaryFile!=null?1:0)+(secondaryFile!=null?1:0);if(visible==0)return;int each=Math.max(10_000,effectiveBudget()/visible);if(primaryFile!=null)reloadLocal(primaryFile,false,each);if(secondaryFile!=null)reloadLocal(secondaryFile,true,each);}
    private void reloadLocal(File file,boolean secondary,int limit){AtomicInteger counter=secondary?secondaryGeneration:primaryGeneration;int generation=counter.incrementAndGet();status.setText("Детализация: "+budgetLabel(project.pointBudget));worker.execute(()->{try{float[] xyz=TzfNative.decodePreview(file.getAbsolutePath(),limit,1);runOnUiThread(()->{if(counter.get()!=generation)return;if(secondary)cloud.setSecondaryCloud(xyz);else cloud.setCloud(xyz);status.setText("Показано "+xyz.length/3+" точек");});}catch(Exception error){runOnUiThread(()->{if(counter.get()==generation)status.setText("Не удалось изменить детализацию: "+error.getMessage());});}});}

    private void syncTransform() { cloud.setSecondaryTransform(transform); syncTransformSummary(); }
    private void syncTransformSummary() {
        transformSummary.setText(String.format(Locale.US,
                "X %.3f  Y %.3f  Z %.3f   ·   RZ %.2f°",
                transform[0], transform[1], transform[2], transform[3]));
    }

    private void startRegistration() {
        if (primaryFile == null || secondaryFile == null) {
            status.setText("Сначала загрузите опорный и перемещаемый TZF");
            return;
        }
        final double rms = readPositive(rmsLimit), p95 = readPositive(p95Limit);
        if (!Double.isFinite(rms) || !Double.isFinite(p95)) return;
        final int generation = registrationGeneration.incrementAndGet();
        final float[] initial = transform.clone();
        setRegistrationRunning(true);
        status.setText("Точная сшивка: подготовка облаков…");
        worker.execute(() -> {
            try {
                RegistrationResult result = TzfNative.registerScans(
                        primaryFile.getAbsolutePath(), secondaryFile.getAbsolutePath(),
                        initial, rms, p95);
                runOnUiThread(() -> finishRegistration(generation, result));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (registrationGeneration.get() != generation) return;
                    setRegistrationRunning(false);
                    status.setText("Ошибка точной сшивки: " + error.getMessage());
                });
            }
        });
    }

    private void finishRegistration(int generation, RegistrationResult result) {
        if (registrationGeneration.get() != generation) return;
        setRegistrationRunning(false);
        if (!result.accepted) {
            status.setText("Сшивка отклонена: " + registrationReason(result.reason));
            return;
        }
        System.arraycopy(result.transform, 0, transform, 0, 4);
        syncTransform();
        status.setText(String.format(Locale.US,
                "Принято · RMS %.3f · P95 %.3f · совпадение %.0f%% · %d ит.",
                result.rms, result.p95, result.overlap * 100, result.iterations));
    }

    private void cancelRegistration() {
        registrationGeneration.incrementAndGet();
        setRegistrationRunning(false);
        status.setText("Сшивка отменена");
    }

    private void setRegistrationRunning(boolean running) {
        registerButton.setEnabled(!running);
        cancelRegistration.setVisibility(running ? View.VISIBLE : View.GONE);
        registrationProgress.setVisibility(running ? View.VISIBLE : View.GONE);
    }

    private double readPositive(EditText field) {
        try {
            double value = Double.parseDouble(field.getText().toString());
            if (value > 0) { field.setError(null); return value; }
        } catch (NumberFormatException ignored) {}
        field.setError("Введите положительное число");
        return Double.NaN;
    }

    private String registrationReason(String reason) {
        if ("insufficient overlap".equals(reason) ||
                "insufficient validation overlap".equals(reason)) return "недостаточное перекрытие";
        if ("degenerate geometry".equals(reason)) return "неоднозначная геометрия";
        if ("RMS exceeds threshold".equals(reason)) return "RMS выше допуска";
        if ("P95 exceeds threshold".equals(reason)) return "P95 выше допуска";
        if ("not enough points".equals(reason) ||
                "not enough spatial coverage".equals(reason)) return "недостаточно точек";
        return reason;
    }

    private void saveTransform() {
        getPreferences(MODE_PRIVATE).edit().putString("transform", joinTransform()).apply();
        status.setText("Трансформация сохранена");
    }

    private String joinTransform() {
        StringBuilder out = new StringBuilder();
        for (float value : transform) { if (out.length() > 0) out.append(','); out.append(value); }
        return out.toString();
    }

    private void restoreTransform() {
        String[] parts = getPreferences(MODE_PRIVATE).getString("transform", "").split(",");
        if (parts.length != 4) { status.setText("Сохранённой трансформации нет"); return; }
        try {
            for (int i = 0; i < 4; i++) transform[i] = Float.parseFloat(parts[i]);
            syncTransform(); status.setText("Трансформация восстановлена");
        } catch (NumberFormatException error) { status.setText("Сохранённая трансформация повреждена"); }
    }

    private void selectTzf(int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/octet-stream");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, request);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if ((request != PICK_PRIMARY && request != PICK_SECONDARY && request != PICK_SCENE) || result != RESULT_OK ||
                data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri,
                data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) {}
        ProjectModel.Scan scan=rememberScan(uri);
        if(request==PICK_SCENE)decodeScene(uri,scan);else decode(uri, request == PICK_SECONDARY);
    }

    private ProjectModel.Scan rememberScan(Uri uri){for(ProjectModel.Node node:project.root.children())if(node instanceof ProjectModel.Scan&&uri.toString().equals(((ProjectModel.Scan)node).uri))return (ProjectModel.Scan)node;String name=uri.getLastPathSegment();if(name==null||name.isEmpty())name="Scan "+(project.scanCount()+1);int slash=Math.max(name.lastIndexOf('/'),name.lastIndexOf(':'));if(slash>=0&&slash+1<name.length())name=name.substring(slash+1);ProjectModel.Scan scan=new ProjectModel.Scan(UUID.randomUUID().toString(),name);scan.uri=uri.toString();int[] colors={0xff38c9e8,0xffffb44a,0xff8ee06f,0xffd58cff,0xffff718a};scan.color=colors[project.scanCount()%colors.length];project.root.add(scan);project.touch(System.currentTimeMillis());ScanTreePanel tree=findViewById(R.id.scanTree);if(tree!=null)tree.refresh();return scan;}
    private void decodeScene(Uri uri,ProjectModel.Scan scan){int generation=primaryGeneration.incrementAndGet();status.setText("Загружаем "+scan.name+"…");worker.execute(()->{try{File local=copyToCache(uri,"scene-"+scan.id+".tzf");int quota=Math.max(20_000,effectiveBudget()/Math.max(1,project.scanCount()));float[] xyz=TzfNative.decodePreview(local.getAbsolutePath(),quota,1);scan.sourcePointCount=Math.max(scan.sourcePointCount,xyz.length/3);float[] world=scan.worldTransform();runOnUiThread(()->{if(primaryGeneration.get()!=generation)return;cloud.setSceneCloud(scan.id,xyz,world,scan.color,scan.visible);status.setText(scan.name+": "+xyz.length/3+" точек");});}catch(Exception e){runOnUiThread(()->status.setText("Скан не загружен: "+e.getMessage()));}});}

    private void decode(Uri uri, boolean secondary) {
        AtomicInteger counter = secondary ? secondaryGeneration : primaryGeneration;
        int generation = counter.incrementAndGet();
        registrationGeneration.incrementAndGet();
        status.setText(secondary ? "Загружаем перемещаемый скан…" : "Загружаем опорный скан…");
        worker.execute(() -> {
            try {
                File local = copyToCache(uri, secondary ? "secondary-preview.tzf" : "primary-preview.tzf");
                if (secondary) secondaryFile = local; else primaryFile = local;
                if (secondary) {
                    publishStage(local, 30_000, 3, true, counter, generation, "Черновой перемещаемый скан");
                    publishStage(local, Math.max(60_000,effectiveBudget()/2), 1, true, counter, generation, "Перемещаемый скан");
                } else {
                    publishStage(local, 20_000, 4, false, counter, generation, "Быстрый просмотр");
                    publishStage(local, 65_000, 2, false, counter, generation, "Уточнение");
                    publishStage(local, effectiveBudget(), 1, false, counter, generation, "Опорный скан");
                }
            } catch (Exception error) {
                runOnUiThread(() -> { if (counter.get() == generation)
                    status.setText((secondary ? "Перемещаемый" : "Опорный") +
                            " скан не загружен: " + error.getMessage()); });
            }
        });
    }

    private void publishStage(File local, int limit, int tileStride, boolean secondary,
                              AtomicInteger counter, int generation, String label) throws IOException {
        if (counter.get() != generation) return;
        float[] xyz = TzfNative.decodePreview(local.getAbsolutePath(), limit, tileStride);
        runOnUiThread(() -> {
            if (counter.get() != generation) return;
            if (secondary) cloud.setSecondaryCloud(xyz); else cloud.setCloud(xyz);
            status.setText(label + ": " + xyz.length / 3 + " точек");
        });
    }

    private File copyToCache(Uri uri, String name) throws IOException {
        File target = new File(getCacheDir(), name);
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target, false)) {
            if (input == null) throw new IOException("нет доступа к выбранному файлу");
            byte[] buffer = new byte[1024 * 1024]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return target;
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putFloatArray(STATE_TRANSFORM, transform.clone()); }
    @Override protected void onResume() { super.onResume(); if (cloud != null) cloud.onResume(); }
    @Override protected void onPause() { if(project!=null){project.touch(System.currentTimeMillis());try{projectStore.save(project);}catch(IOException ignored){}}if (cloud != null) cloud.onPause(); super.onPause(); }
    @Override protected void onDestroy() { primaryGeneration.incrementAndGet(); secondaryGeneration.incrementAndGet(); registrationGeneration.incrementAndGet(); worker.shutdownNow(); super.onDestroy(); }
}
