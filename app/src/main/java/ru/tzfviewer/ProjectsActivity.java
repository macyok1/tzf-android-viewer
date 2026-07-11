package ru.tzfviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

public final class ProjectsActivity extends Activity {
    private ProjectStore store;
    private LinearLayout list;

    @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_projects);store=new ProjectStore(getFilesDir());list=findViewById(R.id.projectList);findViewById(R.id.newProject).setOnClickListener(v->promptNew());}
    @Override protected void onResume(){super.onResume();refresh();}

    private void refresh(){
        list.removeAllViews();
        for(ProjectModel project:store.list())list.addView(card(project));
        findViewById(R.id.emptyProjects).setVisibility(list.getChildCount()==0?View.VISIBLE:View.GONE);
    }

    private View card(ProjectModel project){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(12),dp(12),dp(10));card.setBackgroundColor(getColor(R.color.panel));
        LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.setMargins(0,0,0,dp(10));card.setLayoutParams(cardParams);
        TextView title=new TextView(this);title.setText(project.name);title.setTextColor(getColor(R.color.text_primary));title.setTextSize(18);card.addView(title);
        TextView meta=new TextView(this);meta.setText(project.scanCount()+" сканов · "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(project.modifiedAt)));meta.setTextColor(getColor(R.color.text_secondary));meta.setTextSize(12);card.addView(meta);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.END);String[] labels={"Открыть","Копия","Имя","Удалить"};for(String label:labels){Button b=new Button(this);b.setText(label);b.setTextSize(11);b.setMinWidth(0);b.setMinimumWidth(0);b.setOnClickListener(v->action(label,project));actions.addView(b);}card.addView(actions);card.setOnClickListener(v->open(project));return card;
    }

    private void action(String action,ProjectModel project){if("Открыть".equals(action))open(project);else if("Копия".equals(action))promptCopy(project);else if("Имя".equals(action))promptRename(project);else confirmDelete(project);}
    private void open(ProjectModel project){Intent intent=new Intent(this,MainActivity.class);intent.putExtra(MainActivity.EXTRA_PROJECT_ID,project.id);startActivity(intent);}
    private void promptNew(){prompt("Новый проект","Название проекта","Проект "+(store.list().size()+1),name->{try{ProjectModel p=ProjectModel.create(name,System.currentTimeMillis());store.save(p);open(p);}catch(IOException e){error(e);}});}
    private void promptCopy(ProjectModel source){prompt("Сохранить копию","Название копии",source.name+" — копия",name->{try{store.copy(source,name,System.currentTimeMillis());refresh();}catch(IOException e){error(e);}});}
    private void promptRename(ProjectModel project){prompt("Переименовать проект","Новое название",project.name,name->{try{project.name=name;project.touch(System.currentTimeMillis());store.save(project);refresh();}catch(IOException e){error(e);}});}
    private void confirmDelete(ProjectModel project){new AlertDialog.Builder(this).setTitle("Удалить проект?").setMessage(project.name+" будет удалён. Исходные TZF останутся на устройстве.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{try{store.delete(project.id);refresh();}catch(IOException e){error(e);}}).show();}
    private interface NameAction{void run(String name);}
    private void prompt(String title,String hint,String value,NameAction action){EditText input=new EditText(this);input.setHint(hint);input.setText(value);input.setSelectAllOnFocus(true);int pad=dp(20);LinearLayout box=new LinearLayout(this);box.setPadding(pad,0,pad,0);box.addView(input,new LinearLayout.LayoutParams(-1,-2));AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("Отмена",null).setPositiveButton("Готово",null).create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String name=input.getText().toString().trim();if(name.isEmpty()){input.setError("Введите название");return;}dialog.dismiss();action.run(name);}));dialog.show();}
    private void error(Exception error){Toast.makeText(this,"Ошибка проекта: "+error.getMessage(),Toast.LENGTH_LONG).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
