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
    }

    private ProjectModel project;
    private Listener listener;
    private final Set<ProjectModel.Node> selected = new LinkedHashSet<>();
    private boolean selectionMode;

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
        if (selectionMode) addSelectionBar();
        for (ProjectModel.Node node : project.root.children()) addNode(node, 0);
        if (project.root.children().isEmpty()) {
            TextView empty = text("Сканы ещё не добавлены");
            empty.setPadding(dp(12), dp(16), 0, dp(16));
            addView(empty);
        }
    }

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
            row.addView(role("R", node.id.equals(project.referenceNodeId), v -> toggleReference(node)), new LayoutParams(dp(34), dp(32)));
            row.addView(role("M", node.id.equals(project.movingNodeId), v -> toggleMoving(node)), new LayoutParams(dp(34), dp(32)));
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

    private void toggleReference(ProjectModel.Node node) {
        project.setReference(node.id.equals(project.referenceNodeId) ? null : node);
        changed(); if (listener != null) listener.rolesChanged(); refresh();
    }

    private void toggleMoving(ProjectModel.Node node) {
        project.setMoving(node.id.equals(project.movingNodeId) ? null : node);
        changed(); if (listener != null) listener.rolesChanged(); refresh();
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
    private Button role(String value,boolean active,OnClickListener listener){Button button=action(value,listener);button.setTextColor(active?("R".equals(value)?Color.rgb(69,209,233):Color.rgb(255,180,74)):Color.rgb(120,145,158));return button;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
