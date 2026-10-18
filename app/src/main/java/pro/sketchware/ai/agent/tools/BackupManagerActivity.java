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
        bar.setNavigationIcon(R.drawable.ic_back_ios_24); bar.setNavigationOnClickListener(v -> finish());
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
        MaterialButton all = button("Select all", R.drawable.ic_check_circle);
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
        MaterialButton share = button("Share", R.drawable.ic_msg_share); share.setOnClickListener(v -> shareSelected());
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

    private void importArchive(Uri uri) {
        if (uri == null) return;
        File temp = null;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("Could not open selected file.");
            File tempDir = new File(getCacheDir(), "taj-backup-import");
            if (!tempDir.isDirectory() && !tempDir.mkdirs()) throw new java.io.IOException("Could not prepare import.");
            temp = File.createTempFile("import-", ".json", tempDir);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192]; long total = 0; int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > 32L * 1024L * 1024L) throw new java.io.IOException("Backup exceeds 32 MiB.");
                    out.write(buffer, 0, n);
                }
                out.getFD().sync();
            }
            AgentBackupStore.readAndValidate(temp);
            JSONObject record = new JSONObject(new String(Files.readAllBytes(temp.toPath()), StandardCharsets.UTF_8));
            String category = record.optString("category", "other");
            if (!category.matches("[A-Za-z0-9_-]{1,40}")) category = "other";
            File folder = new File(new File(ProjectPaths.dataRoot(projectId), "TAJ Agent/Backups"), category);
            if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Could not create backup category.");
            File dest = new File(folder, "Imported-" + System.currentTimeMillis() + ".json");
            Files.copy(temp.toPath(), dest.toPath());
            AgentBackupStore.readAndValidate(dest);
            selected.clear(); selected.add(dest.getAbsolutePath());
            Toast.makeText(this, "JSON verified and imported. Check its restore destination before continuing.", Toast.LENGTH_LONG).show();
            refresh();
        } catch (Exception e) {
            new MaterialAlertDialogBuilder(this).setTitle("Import rejected")
                    .setMessage("The file failed validation and was not imported. " + safeMessage(e))
                    .setPositiveButton("OK", null).show();
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp.toPath()); } catch (Exception ignored) {}
        }
    }

    private File resolveTarget(JSONObject record) throws Exception {
        String rootPath = projectRoot.getCanonicalPath();
        String relative = record.optString("relativePath", "");
        File target;
        if (!relative.isEmpty()) {
            if (relative.startsWith("/") || relative.contains("..") || relative.indexOf('\\\\') >= 0)
                throw new java.io.IOException("Unsafe relative path in backup.");
            target = new File(projectRoot, relative).getCanonicalFile();
        } else {
            String oldPath = record.optString("originalPath", "");
            if (oldPath.isEmpty()) throw new java.io.IOException("This older archive has no restorable path.");
            target = new File(oldPath).getCanonicalFile();
        }
        if (!target.getPath().startsWith(rootPath + File.separator))
            throw new java.io.IOException("Restore is restricted to the active Sketchware project.");
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs()))
            throw new java.io.IOException("Restore folder is unavailable.");
        return target;
    }

    private void confirmRestore(File archive) {
        try {
            byte[] bytes = AgentBackupStore.readAndValidate(archive);
            JSONObject record = new JSONObject(new String(Files.readAllBytes(archive.toPath()), StandardCharsets.UTF_8));
            File target = resolveTarget(record);
            new MaterialAlertDialogBuilder(this).setTitle("Restore verified backup")
                    .setMessage("Restore " + record.optString("originalName", target.getName()) + " to:\\n" + target.getPath()
                            + "\\n\\nThe existing file will be replaced only after integrity checks.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore", (dialog, which) -> {
                        try {
                            writeAtomically(target, bytes);
                            Toast.makeText(this, "Backup restored and verified.", Toast.LENGTH_LONG).show();
                            refresh();
                        } catch (Exception e) {
                            new MaterialAlertDialogBuilder(this).setTitle("Restore failed").setMessage(safeMessage(e)).setPositiveButton("OK", null).show();
                        }
                    }).show();
        } catch (Exception e) {
            new MaterialAlertDialogBuilder(this).setTitle("Cannot restore this backup").setMessage(safeMessage(e)).setPositiveButton("OK", null).show();
        }
    }

    private void writeAtomically(File target, byte[] bytes) throws Exception {
        if (bytes.length > 16L * 1024L * 1024L) throw new java.io.IOException("Restore payload exceeds 16 MiB.");
        File temp = File.createTempFile(".taj-restore-", ".tmp", target.getParentFile());
        boolean moved = false;
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) { out.write(bytes); out.flush(); out.getFD().sync(); }
            if (!java.util.Arrays.equals(bytes, Files.readAllBytes(temp.toPath()))) throw new java.io.IOException("Temporary restore verification failed.");
            try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
            moved = true;
            if (!java.util.Arrays.equals(bytes, Files.readAllBytes(target.toPath()))) throw new java.io.IOException("Restored file verification failed.");
        } finally { if (!moved) Files.deleteIfExists(temp.toPath()); }
    }

    private List<File> selectedFiles() {
        List<File> out = new ArrayList<>();
        for (File f : archives) if (selected.contains(f.getAbsolutePath())) out.add(f);
        return out;
    }

    private void shareSelected() {
        List<File> chosen = selectedFiles();
        if (chosen.isEmpty()) { Toast.makeText(this, "Select at least one backup.", Toast.LENGTH_SHORT).show(); return; }
        try {
            ArrayList<Uri> uris = new ArrayList<>();
            for (File f : chosen) uris.add(FileProvider.getUriForFile(this, getPackageName() + ".provider", f));
            Intent send = new Intent(chosen.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
            send.setType("application/json");
            if (chosen.size() == 1) send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share TAJ backup"));
        } catch (Exception e) { Toast.makeText(this, "Share failed: " + safeMessage(e), Toast.LENGTH_LONG).show(); }
    }

    private void deleteSelected() {
        List<File> chosen = selectedFiles();
        if (chosen.isEmpty()) { Toast.makeText(this, "Select backups to delete.", Toast.LENGTH_SHORT).show(); return; }
        new MaterialAlertDialogBuilder(this).setTitle("Delete selected backups?")
                .setMessage("Delete " + chosen.size() + " verified archive(s)? Original project files will not be changed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    int deleted = 0;
                    for (File f : chosen) {
                        try {
                            AgentBackupStore.readAndValidate(f);
                            String root = new File(ProjectPaths.dataRoot(projectId), "TAJ Agent/Backups").getCanonicalPath() + File.separator;
                            if (f.getCanonicalPath().startsWith(root) && Files.deleteIfExists(f.toPath())) deleted++;
                        } catch (Exception ignored) {}
                    }
                    selected.clear(); Toast.makeText(this, deleted + " backup(s) deleted.", Toast.LENGTH_LONG).show(); refresh();
                }).show();
    }

    private void inspectLegacy() {
        List<File> found = new ArrayList<>();
        scanLegacy(projectRoot, 0, found);
        if (found.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle("Legacy backup scan")
                    .setMessage("No recognized legacy .bak files were found. No files were changed.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        new MaterialAlertDialogBuilder(this).setTitle("Safely migrate old backups?")
                .setMessage("Found " + found.size() + " legacy TAJ Agent .bak file(s). Each candidate must map to an existing file inside this project and pass content checks. A verified JSON archive is written and re-read before the old .bak is removed. Anything uncertain is retained.")
                .setNegativeButton("Keep all", null)
                .setPositiveButton("Verify & migrate", (dialog, which) -> migrateLegacyCandidates(found))
                .show();
    }

    private void migrateLegacyCandidates(List<File> candidates) {
        int migrated = 0;
        List<String> retained = new ArrayList<>();
        for (File legacy : candidates) {
            try {
                String path = legacy.getCanonicalPath();
                String root = projectRoot.getCanonicalPath() + File.separator;
                if (!path.startsWith(root) || !legacy.getName().matches(".*\\\\.agent-backup-[A-Za-z0-9-]+\\\\.bak"))
                    throw new java.io.IOException("Not a recognized in-project backup name.");
                int marker = path.lastIndexOf(".agent-backup-");
                if (marker <= root.length()) throw new java.io.IOException("Original file path could not be derived.");
                File original = new File(path.substring(0, marker)).getCanonicalFile();
                if (!original.isFile() || java.nio.file.Files.isSymbolicLink(original.toPath()))
                    throw new java.io.IOException("Original target is missing or not a regular file.");
                byte[] bytes = java.nio.file.Files.readAllBytes(legacy.toPath());
                validateLegacyPayload(original, bytes);
                File archive = AgentBackupStore.migrateLegacy(projectId, original, legacy);
                byte[] verified = AgentBackupStore.readAndValidate(archive);
                if (!java.util.Arrays.equals(bytes, verified)) throw new java.io.IOException("Archive payload mismatch.");
                if (!java.nio.file.Files.deleteIfExists(legacy.toPath()))
                    throw new java.io.IOException("Verified archive created, but old file could not be removed.");
                migrated++;
            } catch (Exception e) {
                retained.add(legacy.getName() + ": " + safeMessage(e));
            }
        }
        refresh();
        StringBuilder report = new StringBuilder("Migrated and verified: ").append(migrated)
                .append("\\nRetained safely: ").append(retained.size());
        for (String reason : retained) report.append("\\n\\n• ").append(reason);
        new MaterialAlertDialogBuilder(this).setTitle("Legacy cleanup result")
                .setMessage(report.toString()).setPositiveButton("OK", null).show();
    }

    private void validateLegacyPayload(File target, byte[] bytes) throws Exception {
        if (bytes.length == 0 || bytes.length > 16L * 1024L * 1024L)
            throw new java.io.IOException("Payload is empty or exceeds 16 MiB.");
        String name = target.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".xml")) {
            org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
            parser.setInput(new java.io.ByteArrayInputStream(bytes), "UTF-8");
            boolean rootSeen = false;
            int event;
            while ((event = parser.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG) rootSeen = true;
            }
            if (!rootSeen) throw new java.io.IOException("XML has no root element.");
        } else if (name.endsWith(".java") || name.endsWith(".kt")) {
            String source = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            if (source.trim().isEmpty()) throw new java.io.IOException("Source file is empty.");
        }
    }

    private void scanLegacy(File dir, int depth, List<File> out) {
        if (dir == null || depth > 12 || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                if (!"TAJ Agent".equals(f.getName())) scanLegacy(f, depth + 1, out);
            } else if (f.isFile() && f.getName().matches(".*\\\\.agent-backup-[A-Za-z0-9-]+\\\\.bak")) {
                try {
                    if (f.getCanonicalPath().startsWith(projectRoot.getCanonicalPath() + File.separator))
                        out.add(f);
                } catch (Exception ignored) {}
            }
        }
    }

    private String safeMessage(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
