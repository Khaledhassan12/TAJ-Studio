package pro.sketchware.ai.agent.tools;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import pro.sketchware.R;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.utility.ThemeUtils;

/** In-place TAJ Backups panel hosted inside the Assistant navigation container. */
public final class BackupManagerPanel extends LinearLayout {
    private final String projectId;
    private final LinearLayout list;
    private final TextView summary;
    private final List<File> archives = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();

    public BackupManagerPanel(@NonNull Context context, @NonNull String projectId) {
        super(context);
        this.projectId = projectId;
        setOrientation(VERTICAL);
        setPadding(dp(14), dp(12), dp(14), dp(12));
        setBackgroundColor(ThemeUtils.getColor(context, R.attr.colorSurface));

        MaterialCardView hero = new MaterialCardView(context);
        hero.setRadius(dp(22));
        hero.setCardElevation(0);
        hero.setStrokeWidth(dp(1));
        hero.setStrokeColor(ThemeUtils.getColor(context, R.attr.colorOutlineVariant));
        hero.setCardBackgroundColor(ThemeUtils.getColor(context, R.attr.colorSurfaceContainerLow));
        LinearLayout intro = new LinearLayout(context);
        intro.setOrientation(VERTICAL);
        intro.setPadding(dp(18), dp(16), dp(18), dp(16));
        TextView title = text("Your project, protected", 20, true);
        intro.addView(title);
        TextView subtitle = text("Verified TAJ Agent snapshots for project files. Review a backup before restoring it.", 13, false);
        subtitle.setPadding(0, dp(6), 0, dp(12));
        intro.addView(subtitle);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton refresh = button("Refresh", R.drawable.ic_mtrl_history);
        refresh.setOnClickListener(v -> refresh());
        MaterialButton delete = button("Delete selected", R.drawable.ic_mtrl_delete);
        delete.setOnClickListener(v -> deleteSelected());
        actions.addView(refresh, new LayoutParams(0, dp(44), 1));
        LayoutParams deleteLp = new LayoutParams(0, dp(44), 1);
        deleteLp.setMarginStart(dp(8));
        actions.addView(delete, deleteLp);
        intro.addView(actions);
        hero.addView(intro);
        addView(hero, new LayoutParams(-1, -2));

        summary = text("Loading backups…", 12, false);
        summary.setPadding(dp(4), dp(12), dp(4), dp(8));
        addView(summary, new LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        list = new LinearLayout(context);
        list.setOrientation(VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        LayoutParams scrollLp = new LayoutParams(-1, 0, 1);
        scrollLp.topMargin = dp(2);
        addView(scroll, scrollLp);
        refresh();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ThemeUtils.getColor(getContext(),
                bold ? R.attr.colorOnSurface : R.attr.colorOnSurfaceVariant));
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private MaterialButton button(String label, int icon) {
        MaterialButton view = new MaterialButton(getContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        view.setText(label);
        view.setTextSize(12);
        view.setAllCaps(false);
        view.setIconResource(icon);
        view.setIconSize(dp(18));
        view.setCornerRadius(dp(15));
        return view;
    }

    public void refresh() {
        archives.clear();
        selected.clear();
        File root = new File(ProjectPaths.dataRoot(projectId), "TAJ Agent/Backups");
        collect(root);
        Collections.sort(archives, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        render();
    }

    private void collect(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File file : children) {
            if (file.isDirectory()) collect(file);
            else if (file.isFile() && file.getName().endsWith(".json")) {
                try {
                    AgentBackupStore.readAndValidate(file);
                    archives.add(file);
                } catch (Exception ignored) { }
            }
        }
    }

    private void render() {
        list.removeAllViews();
        summary.setText(archives.size() + " verified backup" + (archives.size() == 1 ? "" : "s")
                + " • " + selected.size() + " selected");
        if (archives.isEmpty()) {
            TextView empty = text("No verified backups yet. TAJ Agent creates snapshots before supported file edits.", 14, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(34), dp(18), dp(34));
            list.addView(empty, new LayoutParams(-1, -2));
            return;
        }
        for (File archive : archives) {
            try {
                JSONObject record = new JSONObject(new String(Files.readAllBytes(archive.toPath()), StandardCharsets.UTF_8));
                MaterialCardView card = new MaterialCardView(getContext());
                card.setRadius(dp(18));
                card.setCardElevation(0);
                card.setStrokeWidth(dp(1));
                card.setStrokeColor(ThemeUtils.getColor(getContext(), R.attr.colorOutlineVariant));
                card.setCardBackgroundColor(ThemeUtils.getColor(getContext(), R.attr.colorSurfaceContainerLow));

                LinearLayout body = new LinearLayout(getContext());
                body.setOrientation(VERTICAL);
                body.setPadding(dp(14), dp(12), dp(14), dp(12));
                android.widget.CheckBox check = new android.widget.CheckBox(getContext());
                check.setText(record.optString("originalName", archive.getName()));
                check.setTextSize(14);
                check.setTextColor(ThemeUtils.getColor(getContext(), R.attr.colorOnSurface));
                check.setChecked(selected.contains(archive.getAbsolutePath()));
                check.setOnCheckedChangeListener((buttonView, checked) -> {
                    if (checked) selected.add(archive.getAbsolutePath());
                    else selected.remove(archive.getAbsolutePath());
                    summary.setText(archives.size() + " verified backup" + (archives.size() == 1 ? "" : "s")
                            + " • " + selected.size() + " selected");
                });
                body.addView(check);
                String meta = record.optString("category", "other") + " • "
                        + android.text.format.Formatter.formatShortFileSize(getContext(), record.optLong("sizeBytes", 0))
                        + " • " + android.text.format.DateFormat.format("dd MMM yyyy, HH:mm",
                        record.optLong("createdAt", archive.lastModified()));
                TextView details = text(meta, 11, false);
                details.setPadding(dp(2), 0, 0, dp(10));
                body.addView(details);
                MaterialButton restore = button("Review & restore", R.drawable.ic_mtrl_history);
                restore.setOnClickListener(v -> confirmRestore(archive));
                body.addView(restore, new LayoutParams(-1, dp(42)));
                card.addView(body);
                LayoutParams cardLp = new LayoutParams(-1, -2);
                cardLp.bottomMargin = dp(10);
                list.addView(card, cardLp);
            } catch (Exception ignored) { }
        }
    }

    private File resolveTarget(JSONObject record) throws Exception {
        File root = new File(ProjectPaths.dataRoot(projectId)).getCanonicalFile();
        String relative = record.optString("relativePath", "");
        File target;
        if (!relative.isEmpty()) {
            if (relative.startsWith("/") || relative.contains("..") || relative.indexOf('\\\\') >= 0)
                throw new java.io.IOException("Unsafe backup path.");
            target = new File(root, relative).getCanonicalFile();
        } else {
            String oldPath = record.optString("originalPath", "");
            if (oldPath.isEmpty()) throw new java.io.IOException("This older backup has no restorable path.");
            target = new File(oldPath).getCanonicalFile();
        }
        if (!target.getPath().startsWith(root.getPath() + File.separator))
            throw new java.io.IOException("Restore is restricted to the active project.");
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs()))
            throw new java.io.IOException("The target folder is unavailable.");
        return target;
    }

    private void confirmRestore(File archive) {
        try {
            byte[] payload = AgentBackupStore.readAndValidate(archive);
            JSONObject record = new JSONObject(new String(Files.readAllBytes(archive.toPath()), StandardCharsets.UTF_8));
            File target = resolveTarget(record);
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Restore verified backup")
                    .setMessage("Restore " + record.optString("originalName", target.getName())
                            + " to:\\n" + target.getPath()
                            + "\\n\\nThe existing file may be replaced after verification.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore", (dialog, which) -> {
                        try {
                            writeAtomically(target, payload);
                            Toast.makeText(getContext(), "Backup restored and verified.", Toast.LENGTH_LONG).show();
                            pro.sketchware.ai.live.LiveRegistry.notifyChanged("layout", target.getAbsolutePath());
                            refresh();
                        } catch (Exception e) {
                            showError("Restore failed", e);
                        }
                    }).show();
        } catch (Exception e) {
            showError("Cannot restore this backup", e);
        }
    }

    private void writeAtomically(File target, byte[] bytes) throws Exception {
        if (bytes.length > 16L * 1024L * 1024L) throw new java.io.IOException("Restore payload exceeds 16 MiB.");
        File temp = File.createTempFile(".taj-restore-", ".tmp", target.getParentFile());
        boolean moved = false;
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(bytes); out.flush(); out.getFD().sync();
            }
            if (!java.util.Arrays.equals(bytes, Files.readAllBytes(temp.toPath())))
                throw new java.io.IOException("Temporary restore verification failed.");
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            if (!java.util.Arrays.equals(bytes, Files.readAllBytes(target.toPath())))
                throw new java.io.IOException("Restored file verification failed.");
        } finally {
            if (!moved) Files.deleteIfExists(temp.toPath());
        }
    }

    private void deleteSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(getContext(), "Select at least one backup first.", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<File> chosen = new ArrayList<>();
        for (File file : archives) if (selected.contains(file.getAbsolutePath())) chosen.add(file);
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Delete selected backups?")
                .setMessage("Delete " + chosen.size() + " verified backup(s)? Original project files will not be changed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    int deleted = 0;
                    for (File file : chosen) {
                        try {
                            AgentBackupStore.readAndValidate(file);
                            String root = new File(ProjectPaths.dataRoot(projectId), "TAJ Agent/Backups")
                                    .getCanonicalPath() + File.separator;
                            if (file.getCanonicalPath().startsWith(root) && Files.deleteIfExists(file.toPath())) deleted++;
                        } catch (Exception ignored) { }
                    }
                    Toast.makeText(getContext(), deleted + " backup(s) deleted.", Toast.LENGTH_LONG).show();
                    refresh();
                }).show();
    }

    private void showError(String title, Exception error) {
        new MaterialAlertDialogBuilder(getContext()).setTitle(title)
                .setMessage(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())
                .setPositiveButton("OK", null).show();
    }
}
