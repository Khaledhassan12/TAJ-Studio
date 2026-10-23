package pro.sketchware.utility;

import androidx.annotation.NonNull;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import java.util.HashSet;
import java.util.Set;
import mod.agus.jcoderz.beans.ViewBeans;
import a.a.a.jC;

/** Helpers for resolving a layout referenced by an Android include tag. */
public final class IncludedViews {
    private IncludedViews() {}

    @NonNull
    public static String includedLayout(@NonNull ViewBean bean) {
        String inject = bean.inject;
        if (inject == null) return "";
        int key = -1;
        int searchFrom = 0;
        while (searchFrom < inject.length()) {
            int candidate = inject.indexOf("layout", searchFrom);
            if (candidate < 0) return "";
            int after = candidate + 6;
            if (after < inject.length() && inject.charAt(after) == '_') {
                searchFrom = after;
                continue;
            }
            int equals = after;
            while (equals < inject.length() && Character.isWhitespace(inject.charAt(equals))) equals++;
            if (equals < inject.length() && inject.charAt(equals) == '=') {
                key = equals;
                break;
            }
            searchFrom = after;
        }
        if (key < 0) return "";
        int start = key + 1;
        while (start < inject.length() && Character.isWhitespace(inject.charAt(start))) start++;
        if (start >= inject.length()) return "";
        char quote = inject.charAt(start);
        if (quote == '"' || quote == '\'') start++;
        int end = start;
        while (end < inject.length() && inject.charAt(end) != '"' && inject.charAt(end) != '\'' && inject.charAt(end) != '\n') end++;
        String layout = inject.substring(start, end).trim();
        return layout.startsWith("@layout/") ? layout.substring(8) : layout;
    }

    @NonNull
    public static Set<String> layouts(@NonNull String scId) {
        Set<String> result = new HashSet<>();
        for (ProjectFileBean file : jC.b(scId).b()) result.add(file.getXmlName());
        for (ProjectFileBean file : jC.b(scId).c()) result.add(file.getXmlName());
        return result;
    }

    public static boolean isInclude(ViewBean bean) {
        return bean != null && bean.type == ViewBeans.VIEW_TYPE_LAYOUT_INCLUDE;
    }
}
