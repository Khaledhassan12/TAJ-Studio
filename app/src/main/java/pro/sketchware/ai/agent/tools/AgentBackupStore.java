package pro.sketchware.ai.agent.tools;

import android.util.Base64;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import pro.sketchware.ai.agent.ProjectPaths;

/**
 * Durable, project-scoped archive for TAJ Agent file snapshots.
 * Every snapshot is a portable JSON document containing the original bytes and a SHA-256 digest.
 */
public final class AgentBackupStore {
    private static final String ROOT_NAME = "TAJ Agent";
    private static final String BACKUPS_NAME = "Backups";
    private static final String SCHEMA = "taj-studio-backup/v1";

    private AgentBackupStore() {}

    public static File backupRoot(String projectId) throws IOException {
        File projectRoot = new File(ProjectPaths.dataRoot(projectId)).getCanonicalFile();
        File root = new File(projectRoot, ROOT_NAME + File.separator + BACKUPS_NAME).getCanonicalFile();
        if (!root.getPath().startsWith(projectRoot.getPath() + File.separator)) {
            throw new IOException("Backup directory escaped the active project.");
        }
        ensureDirectory(root);
        return root;
    }

    public static File createSnapshot(String projectId, File source) throws IOException {
        if (projectId == null || !projectId.matches("[A-Za-z0-9_-]+")) {
            throw new IOException("Invalid active project.");
        }
        if (source == null || !source.isFile() || Files.isSymbolicLink(source.toPath())) {
            throw new IOException("Only regular files can be backed up.");
        }
        byte[] original = Files.readAllBytes(source.toPath());
        String digest = DomainToolHelper.sha256(source);
        String category = categoryFor(source);
        File categoryDir = new File(backupRoot(projectId), category).getCanonicalFile();
        File root = backupRoot(projectId).getCanonicalFile();
        if (!categoryDir.getPath().startsWith(root.getPath() + File.separator)) {
            throw new IOException("Invalid backup category.");
        }
        ensureDirectory(categoryDir);

        String stamp = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(new Date());
        String safeName = sanitize(source.getName());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        File destination = new File(categoryDir, safeName + " - " + stamp + " | TAJ STUDIO - " + unique + ".json");
        JSONObject record = new JSONObject();
        try {
            record.put("format", SCHEMA);
            record.put("createdAt", System.currentTimeMillis());
            record.put("projectId", projectId);
            record.put("category", category);
            record.put("originalName", source.getName());
            record.put("originalPath", source.getCanonicalPath());
            record.put("contentType", "application/octet-stream");
            record.put("encoding", "base64");
            record.put("sizeBytes", original.length);
            record.put("sha256", digest);
            record.put("content", Base64.encodeToString(original, Base64.NO_WRAP));
        } catch (org.json.JSONException e) {
            throw new IOException("Could not build backup manifest.", e);
        }

        writeVerifiedJson(destination, record.toString(2), record.getString("sha256"));
        return destination;
    }

    /** Validates a portable backup before restoring its bytes to a caller-approved destination. */
    public static byte[] readAndValidate(File archive) throws IOException {
        if (archive == null || !archive.isFile() || Files.isSymbolicLink(archive.toPath())) {
            throw new IOException("Backup file is not a regular file.");
        }
        if (archive.length() > 32L * 1024L * 1024L) throw new IOException("Backup archive exceeds 32 MiB.");
        try {
            JSONObject record = new JSONObject(new String(Files.readAllBytes(archive.toPath()), StandardCharsets.UTF_8));
            if (!SCHEMA.equals(record.optString("format"))) throw new IOException("Unsupported TAJ Studio backup format.");
            String encoded = record.getString("content");
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            if (bytes.length != record.getLong("sizeBytes")) throw new IOException("Backup size validation failed.");
            String actual = sha256(bytes);
            if (!actual.equals(record.getString("sha256"))) throw new IOException("Backup integrity check failed.");
            return bytes;
        } catch (org.json.JSONException | IllegalArgumentException e) {
            throw new IOException("Backup JSON is invalid or incomplete.", e);
        }
    }

    private static void writeVerifiedJson(File destination, String json, String expectedHash) throws IOException {
        File temp = File.createTempFile(".taj-backup-", ".tmp", destination.getParentFile());
        boolean moved = false;
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(bytes);
                out.flush();
                out.getFD().sync();
            }
            JSONObject check = new JSONObject(new String(Files.readAllBytes(temp.toPath()), StandardCharsets.UTF_8));
            byte[] payload = Base64.decode(check.getString("content"), Base64.DEFAULT);
            if (!expectedHash.equals(sha256(payload))) throw new IOException("Backup integrity verification failed.");
            if (destination.exists()) throw new IOException("Backup name collision; original data was not replaced.");
            try {
                Files.move(temp.toPath(), destination.toPath());
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), destination.toPath());
            }
            moved = true;
        } catch (org.json.JSONException e) {
            throw new IOException("Backup manifest verification failed.", e);
        } finally {
            if (!moved) Files.deleteIfExists(temp.toPath());
        }
    }

    private static String categoryFor(File source) {
        String path = source.getAbsolutePath().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (path.contains("/values-night/")) return "values-night";
        if (path.contains("/layout/")) return "layout";
        if (path.contains("/values/")) return "values";
        if (path.contains("/drawable/")) return "drawable";
        if (path.contains("/mipmap")) return "mipmap";
        if (path.contains("/menu/")) return "menu";
        if (path.contains("/anim/")) return "anim";
        if (path.contains("/assets/")) return "assets";
        if (path.endsWith(".java") || path.endsWith(".kt")) return "source";
        if (path.endsWith("androidmanifest.xml") || path.contains("/injection/")) return "manifest";
        return "other";
    }

    private static String sanitize(String name) {
        String safe = name == null ? "file" : name.replaceAll("[\\\\/:*?\\\"<>|\\\\p{Cntrl}]", "_").trim();
        if (safe.isEmpty() || ".".equals(safe) || "..".equals(safe)) return "file";
        return safe.length() > 100 ? safe.substring(safe.length() - 100) : safe;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create TAJ Agent backup directory.");
        }
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable.", e);
        }
    }
}