package ru.tzfviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final int PICK_PRIMARY = 101, PICK_SECONDARY = 102;
    private static final int PREVIEW_LIMIT = 150_000;
    private static final String STATE_TRANSFORM = "transform";
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final AtomicInteger primaryGeneration = new AtomicInteger();
    private final AtomicInteger secondaryGeneration = new AtomicInteger();
    private final float[] transform = new float[6];
    private final EditText[] fields = new EditText[6];
    private PointCloudView cloud;
    private TextView status, transformSummary;
    private EditText translationStep, rotationStep;
    private View viewerTools, stitchingTools;
    private boolean stitching, updatingFields;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        cloud = new PointCloudView(this);
        ((FrameLayout)findViewById(R.id.viewportContainer)).addView(cloud, 0,
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        status = findViewById(R.id.status);
        transformSummary = findViewById(R.id.transformSummary);
        translationStep = findViewById(R.id.translationStep);
        rotationStep = findViewById(R.id.rotationStep);
        viewerTools = findViewById(R.id.viewerTools);
        stitchingTools = findViewById(R.id.stitchingTools);
        ((OrientationCubeView)findViewById(R.id.orientation)).setListener(cloud::setPreset);

        findViewById(R.id.modeViewer).setOnClickListener(v -> setMode(false));
        findViewById(R.id.modeStitching).setOnClickListener(v -> setMode(true));
        findViewById(R.id.openPrimary).setOnClickListener(v -> selectTzf(PICK_PRIMARY));
        findViewById(R.id.openReference).setOnClickListener(v -> selectTzf(PICK_PRIMARY));
        findViewById(R.id.openSecondary).setOnClickListener(v -> selectTzf(PICK_SECONDARY));
        findViewById(R.id.projection).setOnClickListener(v -> cloud.toggleProjection());
        findViewById(R.id.fit).setOnClickListener(v -> cloud.fitView());
        Button measure = findViewById(R.id.measure);
        measure.setOnClickListener(v -> {
            measure.setSelected(!measure.isSelected());
            cloud.setMeasureMode(measure.isSelected(), (length, dz) ->
                    status.setText(String.format(Locale.US,"Расстояние %.3f · ΔZ %.3f",length,dz)));
        });
        ((CheckBox)findViewById(R.id.showPrimary)).setOnCheckedChangeListener((b, checked) -> cloud.setPrimaryVisible(checked));
        ((CheckBox)findViewById(R.id.showSecondary)).setOnCheckedChangeListener((b, checked) -> cloud.setSecondaryVisible(checked));
        findViewById(R.id.resetTransform).setOnClickListener(v -> { java.util.Arrays.fill(transform,0f); syncTransform(); });
        findViewById(R.id.saveTransform).setOnClickListener(v -> saveTransform());
        findViewById(R.id.restoreTransform).setOnClickListener(v -> restoreTransform());
        createAxisEditors(findViewById(R.id.translationFields), 0, translationStep);
        createAxisEditors(findViewById(R.id.rotationFields), 3, rotationStep);
        if (state != null && state.getFloatArray(STATE_TRANSFORM) != null)
            System.arraycopy(state.getFloatArray(STATE_TRANSFORM),0,transform,0,6);
        setMode(false);
        syncTransform();
    }

    private void setMode(boolean stitching) {
        this.stitching = stitching;
        viewerTools.setVisibility(View.VISIBLE);
        stitchingTools.setVisibility(stitching ? View.VISIBLE : View.GONE);
        findViewById(R.id.modeViewer).setSelected(!stitching);
        findViewById(R.id.modeStitching).setSelected(stitching);
    }

    private void createAxisEditors(LinearLayout parent, int offset, EditText stepField) {
        String[] axes={"X","Y","Z"};
        for(int axis=0;axis<3;axis++) {
            LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(4,0,4,4);
            TextView label=new TextView(this); label.setText(axes[axis]); label.setTextColor(getColor(R.color.text_secondary)); label.setGravity(android.view.Gravity.CENTER);
            EditText input=new EditText(this); input.setSingleLine(true); input.setGravity(android.view.Gravity.CENTER); input.setTextColor(getColor(R.color.text_primary));
            int index=offset+axis; fields[index]=input;
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
            input.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){} public void afterTextChanged(Editable e){ if(updatingFields)return; try{transform[index]=Float.parseFloat(e.toString()); input.setError(null); applyTransform();}catch(NumberFormatException ex){input.setError("Введите число");}}});
            LinearLayout controls=new LinearLayout(this); Button minus=smallButton("−"), plus=smallButton("+");
            minus.setOnClickListener(v->{transform[index]-=readStep(stepField);syncTransform();}); plus.setOnClickListener(v->{transform[index]+=readStep(stepField);syncTransform();});
            controls.addView(minus,new LinearLayout.LayoutParams(0,48,1)); controls.addView(plus,new LinearLayout.LayoutParams(0,48,1));
            box.addView(label); box.addView(input,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,52)); box.addView(controls);
            parent.addView(box,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        }
    }

    private Button smallButton(String text){ Button b=new Button(this); b.setText(text); b.setTextSize(20); b.setTextColor(getColor(R.color.text_primary)); return b; }
    private float readStep(EditText field){try{float value=Math.abs(Float.parseFloat(field.getText().toString()));field.setError(null);return value>0?value:0f;}catch(NumberFormatException e){field.setError("Введите положительный шаг");return 0f;}}
    private void syncTransform(){ updatingFields=true; for(int i=0;i<6;i++) if(fields[i]!=null) fields[i].setText(String.format(Locale.US,i<3?"%.3f":"%.1f",transform[i])); updatingFields=false; applyTransform(); }
    private void applyTransform(){ cloud.setSecondaryTransform(transform); transformSummary.setText(String.format(Locale.US,"X %.3f  Y %.3f  Z %.3f   ·   RX %.1f°  RY %.1f°  RZ %.1f°",transform[0],transform[1],transform[2],transform[3],transform[4],transform[5])); }

    private void saveTransform(){ getPreferences(MODE_PRIVATE).edit().putString("transform",joinTransform()).apply(); status.setText("Трансформация сохранена"); }
    private String joinTransform(){ StringBuilder out=new StringBuilder(); for(float v:transform){if(out.length()>0)out.append(',');out.append(v);} return out.toString(); }
    private void restoreTransform(){ String raw=getPreferences(MODE_PRIVATE).getString("transform",""); String[] parts=raw.split(","); if(parts.length!=6){status.setText("Сохранённой трансформации нет");return;} try{for(int i=0;i<6;i++)transform[i]=Float.parseFloat(parts[i]);syncTransform();status.setText("Трансформация восстановлена");}catch(NumberFormatException e){status.setText("Сохранённая трансформация повреждена");} }

    private void selectTzf(int request) { Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("application/octet-stream"); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(intent,request); }

    @Override protected void onActivityResult(int request,int result,Intent data){ super.onActivityResult(request,result,data); if((request!=PICK_PRIMARY&&request!=PICK_SECONDARY)||result!=RESULT_OK||data==null||data.getData()==null)return; Uri uri=data.getData(); try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){} decode(uri,request==PICK_SECONDARY); }

    private void decode(Uri uri, boolean secondary){ AtomicInteger counter=secondary?secondaryGeneration:primaryGeneration; int generation=counter.incrementAndGet(); status.setText(secondary?"Загружаем перемещаемый скан…":"Загружаем опорный скан…"); decoder.execute(()->{ try{File local=copyToCache(uri,secondary?"secondary-preview.tzf":"primary-preview.tzf"); if(secondary){float[] xyz=TzfNative.decodePreview(local.getAbsolutePath(),60_000,1); runOnUiThread(()->{if(counter.get()!=generation)return;cloud.setSecondaryCloud(xyz);status.setText("Перемещаемый скан: "+xyz.length/3+" точек");});}else{float[] coarse=TzfNative.decodePreview(local.getAbsolutePath(),20_000,3);runOnUiThread(()->{if(counter.get()!=generation)return;cloud.setCloud(coarse);status.setText("Быстрый просмотр: "+coarse.length/3+" точек");});float[] xyz=TzfNative.decodePreview(local.getAbsolutePath(),PREVIEW_LIMIT,1);runOnUiThread(()->{if(counter.get()!=generation)return;cloud.setCloud(xyz);status.setText("Опорный скан: "+xyz.length/3+" точек");});}}catch(Exception error){runOnUiThread(()->{if(counter.get()==generation)status.setText((secondary?"Перемещаемый":"Опорный")+" скан не загружен: "+error.getMessage());});}}); }

    private File copyToCache(Uri uri,String name)throws IOException{File target=new File(getCacheDir(),name);try(InputStream input=getContentResolver().openInputStream(uri);FileOutputStream output=new FileOutputStream(target,false)){if(input==null)throw new IOException("нет доступа к выбранному файлу");byte[] buffer=new byte[1024*1024];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);}return target;}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putFloatArray(STATE_TRANSFORM,transform.clone());}
    @Override protected void onResume(){super.onResume();if(cloud!=null)cloud.onResume();}
    @Override protected void onPause(){if(cloud!=null)cloud.onPause();super.onPause();}
    @Override protected void onDestroy(){primaryGeneration.incrementAndGet();secondaryGeneration.incrementAndGet();decoder.shutdownNow();super.onDestroy();}
}
