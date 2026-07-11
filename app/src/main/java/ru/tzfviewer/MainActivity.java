package ru.tzfviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int PICK_TZF = 101;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(40, 40, 40, 40);
        layout.setOrientation(LinearLayout.VERTICAL);
        Button open = new Button(this);
        open.setText("Открыть TZF");
        status = new TextView(this);
        status.setText("Выберите скан .tzf");
        layout.addView(open);
        layout.addView(status);
        setContentView(layout);
        open.setOnClickListener(v -> selectTzf());
    }

    private void selectTzf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/octet-stream");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_TZF);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_TZF || result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, data.getFlags() &
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            TzfHeader header = TzfReader.readHeader(getContentResolver().openInputStream(uri));
            status.setText(header.toDisplayString());
        } catch (Exception error) {
            status.setText("Не удалось открыть TZF: " + error.getMessage());
        }
    }
}
