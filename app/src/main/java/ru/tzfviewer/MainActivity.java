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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final int PICK_PRIMARY = 101, PICK_SECONDARY = 102;
    private static final int PREVIEW_LIMIT = 150_000;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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
        ((OrientationCubeView) findViewById(R.id.orientation)).setListener(cloud::setPreset);

        findViewById(R.id.modeViewer).setOnClickListener(v -> setMode(false));
        findViewById(R.id.modeStitching).setOnClickListener(v -> setMode(true));
        findViewById(R.id.openPrimary).setOnClickListener(v -> selectTzf(PICK_PRIMARY));
        findViewById(R.id.openReference).setOnClickListener(v -> selectTzf(PICK_PRIMARY));
        findViewById(R.id.openSecondary).setOnClickListener(v -> selectTzf(PICK_SECONDARY));
        findViewById(R.id.projection).setOnClickListener(v -> cloud.toggleProjection());
        findViewById(R.id.fit).setOnClickListener(v -> cloud.fitView());
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
        viewerTools.setVisibility(View.VISIBLE);
        stitchingTools.setVisibility(stitching ? View.VISIBLE : View.GONE);
        findViewById(R.id.modeViewer).setSelected(!stitching);
        findViewById(R.id.modeStitching).setSelected(stitching);
    }

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
        if ((request != PICK_PRIMARY && request != PICK_SECONDARY) || result != RESULT_OK ||
                data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri,
                data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) {}
        decode(uri, request == PICK_SECONDARY);
    }

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
                    publishStage(local, 60_000, 1, true, counter, generation, "Перемещаемый скан");
                } else {
                    publishStage(local, 20_000, 4, false, counter, generation, "Быстрый просмотр");
                    publishStage(local, 65_000, 2, false, counter, generation, "Уточнение");
                    publishStage(local, PREVIEW_LIMIT, 1, false, counter, generation, "Опорный скан");
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
    @Override protected void onPause() { if (cloud != null) cloud.onPause(); super.onPause(); }
    @Override protected void onDestroy() { primaryGeneration.incrementAndGet(); secondaryGeneration.incrementAndGet(); registrationGeneration.incrementAndGet(); worker.shutdownNow(); super.onDestroy(); }
}
