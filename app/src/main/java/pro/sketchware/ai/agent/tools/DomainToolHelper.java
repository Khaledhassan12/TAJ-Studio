package pro.sketchware.ai.agent.tools;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.ProjectPaths;
import pro.sketchware.ai.live.UiPoster;
import pro.sketchware.utility.FileUtil;
import com.besome.sketch.design.DesignActivity;

import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.managers.inject.InjectRootLayoutManager;
import com.besome.sketch.beans.ViewBean;
import a.a.a.jC;
import android.util.Pair;
import java.util.ArrayList;
import java.util.Map;

public final class DomainToolHelper {

    // Backups are unique and hash-verified; a user's existing ".bak" is never replaced.
    private static final ConcurrentHashMap<String, String> BACKUPS = new ConcurrentHashMap<>();

    public static void backup(String path, String projectId) {
        File source = new File(path);
        if (!source.isFile()) return;
        try {
            File archive = AgentBackupStore.createSnapshot(projectId, source);
            BACKUPS.put(path, archive.getAbsolutePath());
        } catch (IOException e) {
            String detail = e.getMessage();
            if (detail == null || detail.trim().isEmpty()) detail = e.getClass().getSimpleName();
            if (detail.length() > 240) detail = detail.substring(0, 240);
            throw new IllegalStateException("Could not preserve the original file in TAJ Agent Backups before modifying it: " + detail, e);
        }
    }

    public static boolean restore(String path) {
        return restoreInternal(path, null, false);
    }

    /** Roll back only if the target still contains the bytes written by this operation. */
    public static boolean restoreIfUnchanged(String path, String expectedCurrentHash) {
        return restoreInternal(path, expectedCurrentHash, true);
    }

    private static boolean restoreInternal(String path, String expectedCurrentHash, boolean guardCurrent) {
        String backupPath = BACKUPS.get(path);
        if (backupPath == null) return false;
        File backup = new File(backupPath);
        File target = new File(path);
        File temp = null;
        try {
            if (!backup.isFile()) return false;
            if (guardCurrent && (!target.isFile()
                    || !expectedCurrentHash.equals(sha256(target)))) return false;
            byte[] original = AgentBackupStore.readAndValidate(backup);
            String expectedBackupHash = sha256Bytes(original);
            File parent = target.getAbsoluteFile().getParentFile();
            if (parent == null || !parent.isDirectory()) return false;
            temp = File.createTempFile(".taj-restore-", ".tmp", parent);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(original);
                out.flush();
                out.getFD().sync();
            }
            if (!expectedBackupHash.equals(sha256(temp))) return false;
            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
            boolean restored = target.isFile() && expectedBackupHash.equals(sha256(target));
            if (restored) BACKUPS.remove(path, backupPath);
            return restored;
        } catch (IOException e) {
            return false;
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp.toPath()); } catch (IOException ignored) {}
        }
    }

    private static String sha256Bytes(byte[] bytes) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return toHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable.", e);
        }
    }

    /** Compare-and-swap text write: a file changed since read is never silently overwritten. */
    public static void atomicWrite(File target, String content, String expectedCurrentHash) throws IOException {
        if (target == null || content == null) throw new IOException("Target and content are required.");
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent == null) throw new IOException("Target has no parent directory.");
        if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create the target directory.");
        }
        assertUnchanged(target, expectedCurrentHash);

        File temp = File.createTempFile(".taj-agent-", ".tmp", parent);
        boolean moved = false;
        try {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(bytes);
                out.flush();
                out.getFD().sync();
            }
            if (!sha256(temp).equals(contentSha256(content))) {
                throw new IOException("Temporary write verification failed.");
            }

            // Recheck immediately before the replace to catch edits made during preparation.
            assertUnchanged(target, expectedCurrentHash);
            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            if (!target.isFile() || !contentSha256(content).equals(sha256(target))) {
                throw new IOException("Written file did not match the requested content.");
            }
        } finally {
            if (!moved) {
                try { Files.deleteIfExists(temp.toPath()); } catch (IOException ignored) {}
            }
        }
    }

    private static void assertUnchanged(File target, String expectedCurrentHash) throws IOException {
        if (expectedCurrentHash == null) {
            if (target.exists()) throw new IOException("Write conflict: destination appeared after inspection.");
        } else if (!target.isFile() || !expectedCurrentHash.equals(sha256(target))) {
            throw new IOException("Write conflict: the file changed after it was read; no overwrite was applied.");
        }
    }

    public static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable.", e);
        }
        try (java.io.InputStream in = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return toHex(digest.digest());
    }

    /** UTF-8 read for API 26+ with a hard byte cap to avoid unbounded agent reads. */
    public static String readUtf8(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Target is not a regular file.");
        if (file.length() > 16L * 1024L * 1024L) {
            throw new IOException("File exceeds the 16 MiB read safety limit.");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(file.length(), 65536L));
        try (java.io.InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if ((long) output.size() + count > 16L * 1024L * 1024L) {
                    throw new IOException("File grew beyond the 16 MiB read safety limit.");
                }
                output.write(buffer, 0, count);
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    public static String contentSha256(String text) throws IOException {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable.", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    /** Stable, bounded tree fingerprint; recursive operations never follow symlinks. */
    public static String fingerprintTree(File root) throws IOException {
        if (root == null || !root.exists()) throw new IOException("Source does not exist.");
        StringBuilder canonical = new StringBuilder();
        TreeStats stats = new TreeStats();
        appendFingerprint(root, "", canonical, stats, 0);
        return contentSha256(canonical.toString());
    }

    private static final class TreeStats {
        int entries;
        long bytes;
    }

    private static void appendFingerprint(File file, String relative, StringBuilder out,
                                          TreeStats stats, int depth) throws IOException {
        if (depth > 64) throw new IOException("Directory nesting exceeds the safety limit.");
        if (Files.isSymbolicLink(file.toPath())) {
            throw new IOException("Symbolic links are not supported for recursive project operations.");
        }
        if (++stats.entries > 10000) throw new IOException("Directory contains too many entries.");
        if (file.isDirectory()) {
            out.append("D:").append(relative).append('\n');
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Could not enumerate a directory.");
            java.util.Arrays.sort(children, java.util.Comparator.comparing(File::getName));
            for (File child : children) {
                String childRelative = relative.isEmpty() ? child.getName() : relative + "/" + child.getName();
                appendFingerprint(child, childRelative, out, stats, depth + 1);
            }
        } else if (file.isFile()) {
            stats.bytes += file.length();
            if (stats.bytes > 512L * 1024L * 1024L) {
                throw new IOException("Recursive operation exceeds the 512 MiB safety limit.");
            }
            out.append("F:").append(relative).append(':').append(sha256(file)).append('\n');
        } else {
            throw new IOException("Unsupported filesystem entry.");
        }
    }

    /** Copy a file or directory without following links; refuses existing destinations. */
    public static void copyTree(File source, File destination) throws IOException {
        String before = fingerprintTree(source);
        if (destination.exists()) throw new IOException("Destination already exists.");
        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null || !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create destination directory.");
        }
        try {
            copyNode(source, destination, 0);
            if (!before.equals(fingerprintTree(destination))
                    || !before.equals(fingerprintTree(source))) {
                throw new IOException("Copy verification failed: source or destination changed during the operation.");
            }
        } catch (IOException e) {
            // Never recursively erase a partial destination after a failed copy: a user or
            // editor may already have created/modified entries there. Preserve it for inspection.
            throw new IOException("Copy did not complete. The source was preserved and the destination may contain partial output; it was deliberately left untouched to avoid deleting concurrent user changes. Destination: "
                    + destination.getAbsolutePath() + ". Cause: " + e.getMessage(), e);
        }
    }

    private static void copyNode(File source, File destination, int depth) throws IOException {
        if (depth > 64 || Files.isSymbolicLink(source.toPath())) {
            throw new IOException("Unsafe directory entry encountered during copy.");
        }
        if (source.isDirectory()) {
            // mkdir() must succeed; accepting an existing directory could merge into a
            // destination created concurrently by the user and write into their files.
            if (!destination.mkdir()) throw new IOException("Destination entry appeared during copy; refusing to merge.");
            File[] children = source.listFiles();
            if (children == null) throw new IOException("Could not read source directory.");
            for (File child : children) copyNode(child, new File(destination, child.getName()), depth + 1);
        } else if (source.isFile()) {
            Files.copy(source.toPath(), destination.toPath());
        } else {
            throw new IOException("Unsupported source entry.");
        }
    }

    /** Move without replacement and verify the resulting tree. A conflict never overwrites a destination. */
    public static void moveTree(File source, File destination) throws IOException {
        String expected = fingerprintTree(source);
        if (destination.exists()) throw new IOException("Destination already exists.");
        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null || !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create destination directory.");
        }
        // Do not request ATOMIC_MOVE: Java permits provider-specific replacement behavior
        // for that option. The default move semantics must fail if a destination appears.
        Files.move(source.toPath(), destination.toPath());
        if (source.exists() || !destination.exists() || !expected.equals(fingerprintTree(destination))) {
            throw new IOException("Move could not be fully verified; inspect the destination and source paths before retrying.");
        }
    }

    /** Removes only entries under the supplied path and never follows symlinks. */
    public static void deleteTree(File target) throws IOException {
        if (target == null || (!target.exists() && !Files.isSymbolicLink(target.toPath()))) return;
        if (Files.isSymbolicLink(target.toPath()) || target.isFile()) {
            Files.deleteIfExists(target.toPath());
            return;
        }
        File[] children = target.listFiles();
        if (children == null) throw new IOException("Could not enumerate directory during rollback.");
        for (File child : children) deleteTree(child);
        Files.deleteIfExists(target.toPath());
    }

    /** Move one regular file to project-specific recoverable trash and verify its bytes. */
    public static String moveFileToTrash(String sourcePath, String scId) throws IOException {
        if (scId == null || !scId.matches("[A-Za-z0-9_-]+")) throw new IOException("Invalid active project identifier.");
        File source = new File(sourcePath);
        if (!source.isFile() || Files.isSymbolicLink(source.toPath())) {
            throw new IOException("Only regular, non-symlink files can be moved to trash.");
        }
        String expectedHash = sha256(source);
        File trashDir = new File(new File(FileUtil.getExternalStorageDir(), ".sketchware/.agent_trash"), scId);
        if (!trashDir.isDirectory() && !trashDir.mkdirs() && !trashDir.isDirectory()) {
            throw new IOException("Could not prepare recoverable trash storage.");
        }
        File destination = new File(trashDir, UUID.randomUUID() + "-" + source.getName());
        // The generated name is unique, but default move semantics still refuse replacement.
        Files.move(source.toPath(), destination.toPath());
        if (source.exists() || !destination.isFile() || !expectedHash.equals(sha256(destination))) {
            if (!source.exists() && destination.exists()) {
                try { Files.move(destination.toPath(), source.toPath()); } catch (IOException ignored) {}
            }
            throw new IOException("Trash verification failed; deletion was not reported as successful.");
        }
        return destination.getAbsolutePath();
    }

    public static boolean verifyXml(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        try {
            org.xmlpull.v1.XmlPullParser parser =
                    org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(new java.io.StringReader(content));
            int event = parser.getEventType();
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) event = parser.next();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Marshals Sketchware's mutable designer model onto the main thread and waits
     * for completion. The legacy model and ViewEditor are UI-owned state; mutating
     * them from the agent executor can race drawing, selection, undo, and save.
     */
    private static <T> T callOnMainAndWait(java.util.concurrent.Callable<T> action) throws Exception {
        if (action == null) throw new IOException("Designer operation is required.");
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            return action.call();
        }

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicBoolean abandoned = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicReference<T> value = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();

        UiPoster.post(() -> {
            if (abandoned.get()) {
                latch.countDown();
                return;
            }
            try {
                value.set(action.call());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(15, java.util.concurrent.TimeUnit.SECONDS)) {
            abandoned.set(true);
            throw new IOException("Timed out waiting for the Sketchware designer thread; live synchronization is not verified.");
        }
        Throwable error = failure.get();
        if (error != null) {
            if (error instanceof Exception) throw (Exception) error;
            if (error instanceof Error) throw (Error) error;
            throw new IOException("Designer synchronization failed.", error);
        }
        return value.get();
    }

    public static void refreshEditor(Tool.ToolContext ctx, String xmlName) throws Exception {
        if (ctx == null) throw new IOException("Agent project context is missing.");
        DesignActivity activity = ctx.activity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            throw new IOException("The active Sketchware editor is no longer available; visual refresh was not verified.");
        }
        callOnMainAndWait(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                throw new IOException("The Sketchware editor closed before refresh completed.");
            }
            if (!activity.refreshAgentLayout(ctx.sc_id, xmlName)) {
                throw new IOException("The active editor could not apply the live layout refresh.");
            }
            return null;
        });
    }

    public static void syncLayoutToDesigner(String scId, String xmlName, String content) throws Exception {
        if (scId == null || scId.trim().isEmpty() || xmlName == null || xmlName.trim().isEmpty() || content == null) {
            throw new IOException("Project ID, layout filename, and XML content are required for live designer synchronization.");
        }

        ViewBeanParser parser = new ViewBeanParser(content);
        parser.setSkipRoot(true);
        ArrayList<ViewBean> parsedLayout = parser.parse();
        if (parsedLayout == null) throw new IOException("The layout could not be parsed into Sketchware ViewBeans.");
        Pair<String, Map<String, String>> root = parser.getRootAttributes();

        callOnMainAndWait(() -> {
            // Update the root metadata and widget model as one UI-thread transaction.
            new InjectRootLayoutManager(scId).set(xmlName, InjectRootLayoutManager.toRoot(root));
            jC.a(scId).c.put(xmlName, parsedLayout);
            jC.a(scId).i();
            return null;
        });
    }

    public static boolean verifyLiveModel(String scId, String xmlName, String expectedId) throws Exception {
        if (scId == null || xmlName == null || expectedId == null || expectedId.isEmpty()) return false;
        return callOnMainAndWait(() -> {
            ArrayList<ViewBean> liveBeans = jC.a(scId).c.get(xmlName);
            if (liveBeans != null) {
                for (ViewBean bean : liveBeans) {
                    if (bean != null && expectedId.equals(bean.id)) return true;
                }
            }
            return false;
        });
    }

    /** Verifies ordered widget identities and full bean properties against the UI-owned live model. */
    public static boolean verifyLiveLayout(String scId, String xmlName, String persistedXml) throws Exception {
        if (scId == null || xmlName == null || persistedXml == null) return false;
        ViewBeanParser parser = new ViewBeanParser(persistedXml);
        parser.setSkipRoot(true);
        ArrayList<ViewBean> expected = parser.parse();
        return callOnMainAndWait(() -> {
            ArrayList<ViewBean> actual = jC.a(scId).c.get(xmlName);
            if (expected == null || actual == null || expected.size() != actual.size()) return false;
            for (int i = 0; i < expected.size(); i++) {
                ViewBean expectedBean = expected.get(i);
                ViewBean actualBean = actual.get(i);
                if (expectedBean == null || actualBean == null || !expectedBean.isEqual(actualBean)) return false;
            }
            return true;
        });
    }

    private DomainToolHelper() {}
}
