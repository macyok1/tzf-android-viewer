package ru.tzfviewer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

public final class ProjectsActivity extends Activity {
    private ProjectStore store;
    private LinearLayout list;

    @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_projects);store=new ProjectStore(getFilesDir());list=findViewById(R.id.projectList);findViewById(R.id.newProject).setOnClickListener(v->promptNew());findViewById(R.id.appSettings).setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));}
    @Override protected void onResume(){super.onResume();refresh();}

    private void refresh(){
        list.removeAllViews();
        for(ProjectModel project:store.list())list.addView(card(project));
        findViewById(R.id.emptyProjects).setVisibility(list.getChildCount()==0?View.VISIBLE:View.GONE);
    }

    private View card(ProjectModel project){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(16),dp(12),dp(10),dp(12));card.setBackgroundResource(R.drawable.bg_project_card);card.setMinimumHeight(dp(86));
        LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.setMargins(0,0,0,dp(10));card.setLayoutParams(cardParams);
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);TextView title=new TextView(this);title.setText(project.name);title.setTextColor(getColor(R.color.text_primary));title.setTextSize(18);title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);words.addView(title);TextView meta=new TextView(this);meta.setText(project.scanCount()+" сканов · "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(project.modifiedAt)));meta.setTextColor(getColor(R.color.text_secondary));meta.setTextSize(12);meta.setPadding(0,dp(5),0,0);words.addView(meta);card.addView(words,new LinearLayout.LayoutParams(0,-2,1));Button more=new Button(this);more.setText("⋯");more.setTextColor(getColor(R.color.text_primary));more.setTextSize(20);more.setPadding(0,0,0,0);more.setBackgroundResource(R.drawable.bg_compact_tool);more.setContentDescription("Действия проекта "+project.name);card.addView(more,new LinearLayout.LayoutParams(dp(46),dp(46)));more.setOnClickListener(v->showProjectMenu(v,project));card.setOnClickListener(v->open(project));return card;
    }

    private void showProjectMenu(View anchor,ProjectModel project){java.util.List<InstrumentMenu.Action> actions=new java.util.ArrayList<>();actions.add(InstrumentMenu.Action.item("⧉","Создать копию","Новый проект с теми же сканами",()->promptCopy(project)));actions.add(InstrumentMenu.Action.item("✎","Переименовать","Изменить название проекта",()->promptRename(project)));actions.add(InstrumentMenu.Action.item("×","Удалить проект","Исходные облака сохранятся",()->confirmDelete(project)).danger().divided());InstrumentMenu.show(anchor,"Проект",actions);}
    private void open(ProjectModel project){Intent intent=new Intent(this,MainActivity.class);intent.putExtra(MainActivity.EXTRA_PROJECT_ID,project.id);startActivity(intent);}
    private void promptNew(){prompt("Новый проект","Название проекта","Проект "+(store.list().size()+1),name->{try{ProjectModel p=ProjectModel.create(name,System.currentTimeMillis());store.save(p);open(p);}catch(IOException e){error(e);}});}
    private void promptCopy(ProjectModel source){prompt("Сохранить копию","Название копии",source.name+" — копия",name->{try{store.copy(source,name,System.currentTimeMillis());refresh();}catch(IOException e){error(e);}});}
    private void promptRename(ProjectModel project){prompt("Переименовать проект","Новое название",project.name,name->{try{project.name=name;project.touch(System.currentTimeMillis());store.save(project);refresh();}catch(IOException e){error(e);}});}
    private void confirmDelete(ProjectModel project){InstrumentDialog.confirm(this,"Проект","Удалить проект?",project.name+" будет удалён. Исходные TZF и ASC останутся на устройстве.","Удалить",true,()->{try{store.delete(project.id);refresh();}catch(IOException e){error(e);}});}
    private interface NameAction{void run(String name);}
    private void prompt(String title,String hint,String value,NameAction action){String primary=title.startsWith("Новый")?"Создать":title.contains("коп")?"Сохранить":"Готово";InstrumentDialog.input(this,"Рабочая сцена",title,"Название будет видно в списке проектов и при экспорте облака точек.","Название проекта",value,hint,primary,action::run);}
    private void error(Exception error){Toast.makeText(this,"Ошибка проекта: "+error.getMessage(),Toast.LENGTH_LONG).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
