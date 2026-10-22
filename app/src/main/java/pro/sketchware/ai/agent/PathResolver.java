package pro.sketchware.ai.agent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.utility.FilePathUtil;
import a.a.a.yq;
import pro.sketchware.SketchApplication;
import mod.hey.studios.activity.managers.assets.ManageAssetsActivity;
import pro.sketchware.utility.FileUtil;

public final class PathResolver {

    public static String dataRoot(String scId) {
        return ProjectPaths.dataRoot(scId);
    }

    public static String resourceRoot(String scId) {
        return ProjectPaths.resourceRoot(scId);
    }

    public static String assetsRoot(String scId) {
        return ProjectPaths.assetsRoot(scId);
    }

    public static String javaManagerRoot(String scId) {
        return ProjectPaths.javaManagerRoot(scId);
    }

    public static String projectJavaRoot(String scId) {
        return ProjectPaths.projectJavaRoot(scId);
    }

    public static File resolve(String scId, String userPath) {
        if (scId == null || scId.trim().isEmpty() || !scId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid project identifier.");
        }

        File candidate;
        if (userPath == null || userPath.trim().isEmpty()) {
            candidate = new File(dataRoot(scId));
        } else {
            String path = userPath.trim();
            File requested = new File(path);
            if (requested.isAbsolute()) {
                candidate = requested;
            } else {
                path = path.replace("./", "");
                while (path.startsWith("/")) path = path.substring(1);

                if (path.startsWith("res/") || path.startsWith("resource/")) {
                    String rest = path.substring(path.indexOf("/") + 1);
                    candidate = new File(resourceRoot(scId), rest);
                } else {
                    String[] knownFolders = {"layout/", "values/", "drawable/", "anim/", "menu/", "drawable-xhdpi/", "values-night/"};
                    File mapped = null;
                    for (String folder : knownFolders) {
                        if (path.startsWith(folder)) {
                            mapped = new File(resourceRoot(scId), path);
                            break;
                        }
                    }

                    if (mapped != null) {
                        candidate = mapped;
                    } else if (path.startsWith("assets/")) {
                        candidate = new File(assetsRoot(scId), path.substring(7));
                    } else if (path.startsWith("java/")) {
                        candidate = new File(projectJavaRoot(scId), path.substring(5));
                    } else {
                        String[] searchOrder = {"layout", "drawable", "values", "anim", "menu", "drawable-xhdpi", "values-night"};
                        File found = null;
                        for (String folder : searchOrder) {
                            String suffix = ".xml";
                            File possible = new File(resourceRoot(scId) + File.separator + folder,
                                    path + (path.contains(".") ? "" : suffix));
                            if (possible.exists()) {
                                found = possible;
                                break;
                            }
                        }

                        if (found == null) {
                            File assetCandidate = new File(assetsRoot(scId), path);
                            if (assetCandidate.exists()) {
                                found = assetCandidate;
                            }
                        }
                        if (found == null) {
                            File javaCandidate = new File(javaManagerRoot(scId), path + (path.contains(".") ? "" : ".java"));
                            if (javaCandidate.exists()) {
                                found = javaCandidate;
                            } else if (!path.contains(".")) {
                                File kotlinCandidate = new File(javaManagerRoot(scId), path + ".kt");
                                if (kotlinCandidate.exists()) found = kotlinCandidate;
                            }
                        }
                        candidate = found != null ? found : new File(dataRoot(scId), path);
                    }
                }
            }
        }

        ensureInsideProject(scId, candidate);
        return candidate;
    }

    public static File validateProjectPath(String scId, File candidate) {
        if (scId == null || scId.trim().isEmpty() || !scId.matches("[A-Za-z0-9_-]+") || candidate == null) {
            throw new IllegalArgumentException("Invalid project path.");
        }
        ensureInsideProject(scId, candidate);
        return candidate;
    }

    /**
     * Rejects traversal, symlink escapes and arbitrary absolute paths. Only the
     * active project's known storage roots are addressable by AI tools.
     */
    private static void ensureInsideProject(String scId, File candidate) {
        try {
            String target = candidate.getCanonicalPath();
            // Include the canonical Sketchware project-data directory as an anchor.
            // Some Sketchware versions expose a narrower/different wq.b(scId) root,
            // while manager APIs (FilePathUtil) store resources/assets below
            // <external>/.sketchware/data/<sc_id>/files. Restrict this fallback to
            // the bound project ID; never widen it to the whole .sketchware/data tree.
            String projectDataRoot = new File(
                    android.os.Environment.getExternalStorageDirectory(),
                    ".sketchware/data/" + scId).getPath();
            String[] roots = {
                    projectDataRoot, dataRoot(scId), resourceRoot(scId), assetsRoot(scId),
                    javaManagerRoot(scId), projectJavaRoot(scId)
            };
            for (String root : roots) {
                if (root == null || root.trim().isEmpty()) continue;
                String canonicalRoot = new File(root).getCanonicalPath();
                if (target.equals(canonicalRoot)
                        || target.startsWith(canonicalRoot + File.separator)) {
                    return;
                }
            }
        } catch (java.io.IOException ignored) {
            // Fail closed when canonicalization cannot establish a safe path.
        }
        throw new SecurityException("AI tools may only access files inside the current project.");
    }

    public static List<String> candidates(String scId, String filename) {
        List<String> list = new ArrayList<>();
        String[] searchOrder = {"layout", "drawable", "values", "anim", "menu", "drawable-xhdpi", "values-night"};
        for (String folder : searchOrder) {
            list.add("res/" + folder + "/" + filename);
        }
        list.add("assets/" + filename);
        list.add("java/" + filename);
        return list;
    }

    private PathResolver() {}
}
