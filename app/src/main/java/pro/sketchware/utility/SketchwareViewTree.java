package pro.sketchware.utility;

import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a stable, parent-aware sequence of Sketchware view beans.
 *
 * The project stores a layout as a flat list; the editor and generators need the
 * same list arranged so every container precedes its descendants and each sibling
 * group follows the index stored for that parent.
 */
public final class SketchwareViewTree {

    private SketchwareViewTree() {
    }

    /**
     * Returns reachable widgets in layout-tree order. Orphaned widgets are excluded
     * because no visual parent can receive them. The source list and its beans are
     * not modified.
     */
    public static ArrayList<ViewBean> ordered(List<ViewBean> views) {
        ArrayList<ViewBean> result = new ArrayList<>();
        if (views == null || views.isEmpty()) return result;

        Map<String, List<ViewBean>> children = indexChildren(views);
        List<ViewBean> roots = children.get("root");
        if (roots == null || roots.isEmpty()) return result;

        Set<String> visited = new HashSet<>();
        visited.add("root");

        // All top-level widgets must be available before widgets nested under them.
        for (ViewBean root : roots) {
            if (root != null) {
                result.add(root);
                if (root.id != null && !root.id.isEmpty()) visited.add(root.id);
            }
        }
        for (ViewBean root : roots) {
            appendDescendants(root, children, visited, result);
        }
        return result;
    }

    /**
     * Returns a widget and its reachable subtree in parent-before-child order.
     * This is used when an operation must copy or remove a container as one unit.
     */
    public static ArrayList<ViewBean> withDescendants(List<ViewBean> views, ViewBean root) {
        ArrayList<ViewBean> result = new ArrayList<>();
        if (root == null) return result;

        result.add(root);
        Map<String, List<ViewBean>> children = indexChildren(views);
        Set<String> visited = new HashSet<>();
        if (root.id != null && !root.id.isEmpty()) visited.add(root.id);
        appendDescendants(root, children, visited, result);
        return result;
    }

    private static Map<String, List<ViewBean>> indexChildren(List<ViewBean> views) {
        Map<String, List<ViewBean>> children = new HashMap<>();
        if (views == null) return children;

        for (ViewBean bean : views) {
            if (bean == null || bean.parent == null || bean.parent.isEmpty()) continue;
            children.computeIfAbsent(bean.parent, ignored -> new ArrayList<>()).add(bean);
        }

        // List.sort is stable, so equal indexes preserve the project's stored order.
        Comparator<ViewBean> byPosition = Comparator.comparingInt(bean -> bean.index);
        for (List<ViewBean> siblings : children.values()) {
            siblings.sort(byPosition);
        }
        return children;
    }

    private static void appendDescendants(
            ViewBean parent,
            Map<String, List<ViewBean>> children,
            Set<String> visited,
            List<ViewBean> result) {
        if (parent == null || parent.id == null || parent.id.isEmpty()) return;

        List<ViewBean> direct = children.get(parent.id);
        if (direct == null || direct.isEmpty()) return;

        List<ViewBean> added = new ArrayList<>();
        for (ViewBean child : direct) {
            if (child == null || child.id == null || child.id.isEmpty()) continue;
            if (visited.add(child.id)) {
                result.add(child);
                added.add(child);
            }
        }

        for (ViewBean child : added) {
            appendDescendants(child, children, visited, result);
        }
    }
}
