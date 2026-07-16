package ru.tzfviewer;
import android.app.Activity;
import android.os.Bundle;
public final class HelpActivity extends Activity { @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_help);findViewById(R.id.backFromHelp).setOnClickListener(v->finish());} }
