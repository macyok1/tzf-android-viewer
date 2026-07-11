package ru.tzfviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_TZF = 101;
    private static final int PREVIEW_LIMIT = 150_000;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button open;
    private PointCloudView cloud;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(24, 24, 24, 24);
        layout.setOrientation(LinearLayout.VERTICAL);
        open = new Button(this);
        open.setText("Открыть TZF");
        status = new TextView(this);
        status.setText("Выберите скан .tzf для предпросмотра");
        cloud = new PointCloudView(this);
        cloud.setVisibility(View.GONE);
        layout.addView(open);
        layout.addView(status);
        layout.addView(cloud, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(layout);
        open.setOnClickListener(v -> selectTzf());
    }

    private void selectTzf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/octet-stream");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_TZF);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_TZF || result != RESULT_OK || data == null ||
                data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Некоторые файловые провайдеры не поддерживают сохраняемое разрешение.
        }
        decode(uri);
    }

    private void decode(Uri uri) {
        open.setEnabled(false);
        cloud.setVisibility(View.GONE);
        status.setText("Копируем TZF и готовим предпросмотр…");
        decoder.execute(() -> {
            try {
                File local = copyToCache(uri);
                float[] xyz = TzfNative.decodePreview(local.getAbsolutePath(), PREVIEW_LIMIT);
                runOnUiThread(() -> {
                    cloud.setCloud(xyz);
                    cloud.setVisibility(View.VISIBLE);
                    status.setText("Точек в предпросмотре: " + (xyz.length / 3) +
                            "\nПоворот — один палец, масштаб — два пальца.");
                    open.setEnabled(true);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    status.setText("Не удалось показать TZF: " + error.getMessage());
                    open.setEnabled(true);
                });
            }
        });
    }

    private File copyToCache(Uri uri) throws IOException {
        File target = new File(getCacheDir(), "preview.tzf");
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target, false)) {
            if (input == null) throw new IOException("нет доступа к выбранному файлу");
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return target;
    }

    @Override protected void onDestroy() {
        decoder.shutdownNow();
        super.onDestroy();
    }
}
