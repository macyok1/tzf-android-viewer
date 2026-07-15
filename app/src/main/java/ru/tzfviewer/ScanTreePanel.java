package ru.tzfviewer;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ScanTreePanel extends LinearLayout {
    interface Listener {
        void changed();
        void visibilityChanged(ProjectModel.Node node);
        void rolesChanged();
        default void acceptRegistration(String stationId){}
        default void rejectRegistration(String stationId){}
        default void retryRegistration(String stationId){}
        default void manualRegistration(String stationId){}
        default void detachRegistration(String stationId){}
    }

    private ProjectModel project;
    private Listener listener;
    private final Set<ProjectModel.Node> selected = new LinkedHashSet<>();
    private boolean selectionMode;
    private boolean registrationView;

    public ScanTreePanel(Context context) { this(context, null); }
    public ScanTreePanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
    }

    void bind(ProjectModel project, Listener listener) {
        this.project = project;
        this.listener = listener;
        refresh();
    }

    void refresh() {
        removeAllViews();
        if (project == null) return;
        addViewModeBar();
        if(registrationView){addRegistrationRows();return;}
        if (selectionMode) addSelectionBar();
        for (ProjectModel.Node node : project.root.children()) addNode(node, 0);
        if (project.root.children().isEmpty()) {
            TextView empty = text("Сканы ещё не добавлены");
            empty.setPadding(dp(12), dp(16), 0, dp(16));
            addView(empty);
        }
    }

    private void addViewModeBar(){
        LinearLayout bar=row();Button folders=action("Папки",v->{registrationView=false;selectionMode=false;selected.clear();refresh();});Button sets=action("Наборы сшивки",v->{registrationView=true;selectionMode=false;selected.clear();refresh();});folders.setTextColor(registrationView?Color.rgb(159,180,194):Color.rgb(56,201,232));sets.setTextColor(registrationView?Color.rgb(56,201,232):Color.rgb(159,180,194));bar.addView(folders,new LayoutParams(0,dp(42),1));bar.addView(sets,new LayoutParams(0,dp(42),1));addView(bar);
    }

    private void addRegistrationRows(){
        List<RegistrationTreeModel.Row> rows=RegistrationTreeModel.rows(project);if(rows.isEmpty()){TextView empty=text("Наборов сшивки пока нет");empty.setPadding(dp(12),dp(16),0,dp(16));addView(empty);return;}
        for(RegistrationTreeModel.Row item:rows){LinearLayout line=row();if(item.kind==RegistrationTreeModel.Kind.SET){line.setPadding(dp(10),dp(3),dp(6),0);TextView title=text("▼  "+item.title+" · "+item.status);title.setTextColor(Color.rgb(159,180,194));line.addView(title,new LayoutParams(0,dp(38),1));}else{line.setPadding(dp(18),0,dp(2),0);TextView title=text("●  "+item.title+" · "+item.status);title.setTextColor(toneColor(item.tone));line.addView(title,new LayoutParams(0,dp(40),1));line.addView(role("R",item.stationId.equals(project.referenceNodeId),v->toggleRegistrationRole(item.stationId,true)),new LayoutParams(dp(34),dp(36)));line.addView(role("M",item.stationId.equals(project.movingNodeId),v->toggleRegistrationRole(item.stationId,false)),new LayoutParams(dp(34),dp(36)));if(item.actions.contains(RegistrationTreeModel.Action.ACCEPT))line.addView(action("✓",v->{if(listener!=null)listener.acceptRegistration(item.stationId);}),new LayoutParams(dp(34),dp(36)));if(item.actions.contains(RegistrationTreeModel.Action.REJECT))line.addView(action("×",v->{if(listener!=null)listener.rejectRegistration(item.stationId);}),new LayoutParams(dp(34),dp(36)));if(item.actions.contains(RegistrationTreeModel.Action.DETACH))line.addView(action("⋮",v->showRegistrationMenu(v,item.stationId)),new LayoutParams(dp(36),dp(36)));}addView(line);}
    }

    private void toggleRegistrationRole(String stationId,boolean reference){ProjectModel.Node node=project.findNode(stationId);if(!(node instanceof ProjectModel.Scan))return;RegistrationGraph graph=new RegistrationGraph(project);ProjectModel.Node opposite=project.findNode(reference?project.movingNodeId:project.referenceNodeId);if(opposite!=null&&graph.setForScan(opposite.id)==graph.setForScan(stationId)){if(reference)project.setMoving(null);else project.setReference(null);}if(reference)project.setReference(stationId.equals(project.referenceNodeId)?null:node);else project.setMoving(stationId.equals(project.movingNodeId)?null:node);if(listener!=null)listener.rolesChanged();refresh();}
    private void showRegistrationMenu(View anchor,String stationId){PopupMenu menu=new PopupMenu(getContext(),anchor);menu.getMenu().add("Убрать из набора");menu.setOnMenuItemClickListener(item->{if(listener!=null)listener.detachRegistration(stationId);return true;});menu.show();}

    private void addSelectionBar() {
        LinearLayout bar = row();
        TextView count = text("Выбрано: " + selected.size());
        bar.addView(count, new LayoutParams(0, dp(40), 1));
        bar.addView(action("Связать", v -> groupSelected()), new LayoutParams(dp(88), dp(36)));
        bar.addView(action("Переместить", v -> moveSelected()), new LayoutParams(dp(104), dp(36)));
        bar.addView(action("×", v -> leaveSelection()), new LayoutParams(dp(40), dp(36)));
        addView(bar);
    }

    private void addNode(ProjectModel.Node node, int depth) {
        LinearLayout row = row();
        row.setPadding(dp(4 + depth * 14), 0, dp(2), 0);
        if (selectionMode) {
            CheckBox pick = new CheckBox(getContext());
            pick.setChecked(selected.contains(node));
            pick.setOnCheckedChangeListener((button, checked) -> { if (checked) selected.add(node); else selected.remove(node); refresh(); });
            row.addView(pick, new LayoutParams(dp(36), dp(36)));
        } else {
            CheckBox visible = new CheckBox(getContext());
            visible.setChecked(node.visible);
            visible.setOnCheckedChangeListener((button, checked) -> { node.visible = checked; changed(); if (listener != null) listener.visibilityChanged(node); });
            row.addView(visible, new LayoutParams(dp(36), dp(36)));
        }

        String prefix = node instanceof ProjectModel.Group ? (((ProjectModel.Group) node).expanded ? "▼ " : "▶ ") : "● ";
        TextView name = text(prefix + node.name + stateSuffix(node));
        name.setOnClickListener(v -> { if (node instanceof ProjectModel.Group) { node.expanded = !node.expanded; changed(); refresh(); } });
        name.setOnLongClickListener(v -> { selectionMode = true; selected.add(node); refresh(); return true; });
        row.addView(name, new LayoutParams(0, dp(36), 1));

        if (!selectionMode) {
            Button more = action("⋮", v -> showMenu(v, node));
            row.addView(more, new LayoutParams(dp(36), dp(36)));
        }
        addView(row);
        if (node instanceof ProjectModel.Group && node.expanded)
            for (ProjectModel.Node child : ((ProjectModel.Group) node).children()) addNode(child, depth + 1);
    }

    private String stateSuffix(ProjectModel.Node node) {
        if (node instanceof ProjectModel.Group) return " · " + node.scanCount();
        ProjectModel.Scan scan = (ProjectModel.Scan) node;
        if (scan.loadState == ProjectModel.Scan.LOADING) return " · загрузка";
        if (scan.loadState == ProjectModel.Scan.ERROR) return " · ошибка";
        if (scan.loadState == ProjectModel.Scan.WAITING) return " · ожидает";
        return "";
    }

    private void showMenu(View anchor, ProjectModel.Node node) {
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        menu.getMenu().add("Переименовать");
        if (node.parent() != project.root) menu.getMenu().add("Отсоединить в сцену");
        if (node instanceof ProjectModel.Group) menu.getMenu().add("Расформировать");
        menu.getMenu().add("Удалить из проекта");
        menu.setOnMenuItemClickListener(item -> { String title = item.getTitle().toString();
            if (title.startsWith("Переименовать")) rename(node);
            else if (title.startsWith("Отсоединить")) { project.root.reparentPreservingWorld(node); changedAndRefresh(); }
            else if (title.startsWith("Расформировать")) { ((ProjectModel.Group) node).dissolveIntoParent(); project.clearRoleFor(node); changedAndRefresh(); }
            else confirmDelete(node);
            return true;
        });
        menu.show();
    }

    private void rename(ProjectModel.Node node) {
        EditText input = new EditText(getContext()); input.setText(node.name); input.setSelectAllOnFocus(true);
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("Переименовать").setView(input).setNegativeButton("Отмена", null).setPositiveButton("Готово", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { String value=input.getText().toString().trim();if(value.isEmpty()){input.setError("Введите название");return;}node.name=value;dialog.dismiss();changedAndRefresh(); }));
        dialog.show();
    }

    private void confirmDelete(ProjectModel.Node node) {
        new AlertDialog.Builder(getContext()).setTitle("Удалить из проекта?").setMessage("Исходные TZF останутся на устройстве.").setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d,w) -> { new RegistrationGraph(project).removeNode(node); changedAndRefresh(); if(listener!=null)listener.rolesChanged(); }).show();
    }

    private void groupSelected() {
        List<ProjectModel.Node> roots = selectedRoots();
        if (roots.size() < 2) return;
        ProjectModel.Group group = new ProjectModel.Group(UUID.randomUUID().toString(), "Связка " + (countGroups(project.root) + 1));
        project.root.add(group);
        for (ProjectModel.Node node : roots) group.reparentPreservingWorld(node);
        leaveSelection(); changedAndRefresh();
    }

    private void moveSelected() {
        List<ProjectModel.Group> groups = new ArrayList<>(); collectGroups(project.root, groups);
        if (groups.isEmpty() || selected.isEmpty()) return;
        String[] names = new String[groups.size() + 1]; names[0] = "Сцена"; for (int i=0;i<groups.size();i++) names[i+1]=groups.get(i).name;
        new AlertDialog.Builder(getContext()).setTitle("Переместить в…").setItems(names, (d,which) -> { ProjectModel.Group destination=which==0?project.root:groups.get(which-1);for(ProjectModel.Node node:selectedRoots())if(node!=destination&&!ProjectModel.isAncestor(node,destination))destination.reparentPreservingWorld(node);leaveSelection();changedAndRefresh(); }).show();
    }

    private List<ProjectModel.Node> selectedRoots() { List<ProjectModel.Node> roots=new ArrayList<>();for(ProjectModel.Node node:selected){boolean covered=false;for(ProjectModel.Node other:selected)if(other!=node&&ProjectModel.isAncestor(other,node)){covered=true;break;}if(!covered)roots.add(node);}return roots; }
    private void collectGroups(ProjectModel.Group parent,List<ProjectModel.Group> out){for(ProjectModel.Node node:parent.children())if(node instanceof ProjectModel.Group){out.add((ProjectModel.Group)node);collectGroups((ProjectModel.Group)node,out);}}
    private int countGroups(ProjectModel.Group parent){int n=0;for(ProjectModel.Node node:parent.children())if(node instanceof ProjectModel.Group)n+=1+countGroups((ProjectModel.Group)node);return n;}
    private void leaveSelection(){selectionMode=false;selected.clear();refresh();}
    private void changedAndRefresh(){changed();refresh();}
    private void changed(){if(listener!=null)listener.changed();}
    private LinearLayout row(){LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);return row;}
    private TextView text(String value){TextView view=new TextView(getContext());view.setText(value);view.setTextColor(Color.WHITE);view.setGravity(Gravity.CENTER_VERTICAL);view.setSingleLine(true);return view;}
    private Button action(String value,OnClickListener listener){Button button=new Button(getContext());button.setText(value);button.setTextColor(Color.WHITE);button.setTextSize(11);button.setPadding(0,0,0,0);button.setBackgroundColor(Color.TRANSPARENT);button.setOnClickListener(listener);return button;}
    private Button role(String value,boolean active,OnClickListener listener){Button button=action(value,listener);button.setTextSize(12);button.setTextColor(active?("R".equals(value)?Color.rgb(56,201,232):Color.rgb(255,180,74)):Color.rgb(120,145,158));return button;}
    private int toneColor(RegistrationTreeModel.Tone tone){if(tone==RegistrationTreeModel.Tone.GREEN)return Color.rgb(142,224,111);if(tone==RegistrationTreeModel.Tone.ORANGE)return Color.rgb(255,180,74);if(tone==RegistrationTreeModel.Tone.RED)return Color.rgb(255,107,107);return Color.rgb(159,180,194);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
