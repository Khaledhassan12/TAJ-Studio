package pro.sketchware.ai.agent.tools;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import pro.sketchware.R;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.utility.ThemeUtils;

/** Project-scoped TAJ Backups manager. */
public final class BackupManagerActivity extends BaseAppCompatActivity {
    public static final String EXTRA_PROJECT_ID = "sc_id";
    private String projectId;
    private File projectRoot;
    private LinearLayout list;
    private TextView summary;
    private final List<File> archives = new ArrayList<>();
    private final java.util.Set<String> selected = new java.util.LinkedHashSet<>();
    private ActivityResultLauncher<String[]> importLauncher;

    @Override protected void onCreate(Bundle state) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(state);
        projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        if (projectId == null || !projectId.matches("[A-Za-z0-9_-]+")) {
            Toast.makeText(this, "No valid active Sketchware project.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        projectRoot = new File(ProjectPaths.dataRoot(projectId)).getAbsoluteFile();
        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importArchive);
        buildUi();
        refresh();
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size);
        t.setTextColor(ThemeUtils.getColor(this, bold ? R.attr.colorOnSurface : R.attr.colorOnSurfaceVariant));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private MaterialButton button(String label, int icon) {
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label); b.setTextSize(12); b.setAllCaps(false); b.setIconResource(icon);
        b.setIconSize(dp(18)); b.setCornerRadius(dp(16));
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));
        MaterialToolbar bar = new MaterialToolbar(this);
        bar.setTitle("TAJ Backups"); bar.setSubtitle("Project " + projectId);
        bar.setNavigationIcon(R.drawable.ic_mtrl_back); bar.setNavigationOnClickListener(v -> finish());
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(64)));

        MaterialCardView hero = new MaterialCardView(this);
        hero.setRadius(dp(24)); hero.setCardElevation(0); hero.setStrokeWidth(dp(1));
        hero.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        hero.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainerLow));
        LinearLayout hc = new LinearLayout(this); hc.setOrientation(LinearLayout.VERTICAL);
        hc.setPadding(dp(18), dp(16), dp(18), dp(16));
        hc.addView(text("Your project, protected", 20, true));
        TextView description = text("Verified JSON snapshots for layouts, resources and source files. Restores stay inside this project.", 13, false);
        description.setPadding(0, dp(6), 0, dp(12)); hc.addView(description);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton imp = button("Import JSON", R.drawable.ic_mtrl_file_present);
        imp.setOnClickListener(v -> importLauncher.launch(new String[]{"application/json", "text/json", "application/octet-stream"}));
        MaterialButton all = button("Select all", R.drawable.ic_mtrl_check_circle);
        all.setOnClickListener(v -> { selected.clear(); for (File f : archives) selected.add(f.getAbsolutePath()); render(); });
        actions.addView(imp, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(0, dp(48), 1); allLp.setMarginStart(dp(8));
        actions.addView(all, allLp); hc.addView(actions); hero.addView(hc);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.setMargins(dp(16), dp(10), dp(16), dp(10)); root.addView(hero, heroLp);

        summary = text("Loading backups…", 13, false); summary.setPadding(dp(20), dp(4), dp(20), dp(8)); root.addView(summary);
        ScrollView scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(4), dp(16), dp(24)); scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this); bottom.setPadding(dp(12), dp(8), dp(12), dp(12));
        bottom.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainerLow));
        MaterialButton share = button("Share", R.drawable.ic_mtrl_share); share.setOnClickListener(v -> shareSelected());
        MaterialButton delete = button("Delete", R.drawable.ic_mtrl_delete); delete.setOnClickListener(v -> deleteSelected());
        MaterialButton legacy = button("Check .bak", R.drawable.ic_mtrl_history); legacy.setOnClickListener(v -> inspectLegacy());
        bottom.addView(share, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(48), 1); dlp.setMarginStart(dp(6)); bottom.addView(delete, dlp);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, dp(48), 1); llp.setMarginStart(dp(6)); bottom.addView(legacy, llp);
        root.addView(bottom); setContentView(root);
    }

    private void refresh() {
        archives.clear();
        collect(new File(ProjectPaths.dataRoot(projectId), "TAJ Agent/Backups"));
        Collections.sort(archives, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        render();
    }

    private void collect(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] children = dir.listFiles(); if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) collect(f);
            else if (f.isFile() && f.getName().endsWith(".json")) {
                try { AgentBackupStore.readAndValidate(f); archives.add(f); } catch (Exception ignored) {}
            }
        }
    }

    private void render() {
        list.removeAllViews();
        summary.setText(archives.size() + " verified backups • " + selected.size() + " selected");
        if (archives.isEmpty()) {
            TextView empty = text("No verified backups yet. TAJ Agent will create snapshots before supported edits.", 14, false);
            empty.setPadding(dp(16), dp(30), dp(16), dp(30)); list.addView(empty); return;
        }
        for (File archive : archives) {
            try {
                JSONObject r = new JSONObject(new String(Files.readAllBytes(archive.toPath()), StandardCharsets.UTF_8));
                MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(20)); card.setCardElevation(0);
                card.setStrokeWidth(dp(1)); card.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
                card.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainerLow));
                LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10), dp(10), dp(10), dp(10));
                android.widget.CheckBox cb = new android.widget.CheckBox(this); cb.setChecked(selected.contains(archive.getAbsolutePath()));
                cb.setOnCheckedChangeListener((v, checked) -> { if (checked) selected.add(archive.getAbsolutePath()); else selected.remove(archive.getAbsolutePath()); summary.setText(archives.size()+" verified backups • "+selected.size()+" selected"); });
                row.addView(cb, new LinearLayout.LayoutParams(dp(38), dp(48)));
                LinearLayout details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL);
                details.addView(text(r.optString("originalName", archive.getName()), 15, true));
                details.addView(text(r.optString("category", "other") + " • " + android.text.format.Formatter.formatShortFileSize(this, r.optLong("sizeBytes", 0)) + " • " + android.text.format.DateFormat.format("dd MMM yyyy, HH:mm", r.optLong("createdAt", archive.lastModified())), 11, false));
                row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
                MaterialButton restore = button("Restore", R.drawable.ic_mtrl_history); restore.setTextSize(11); restore.setOnClickListener(v -> confirmRestore(archive)); row.addView(restore);
                card.addView(row); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(10); list.addView(card, lp);
            } catch (Exception ignored) {}
        }
    }
