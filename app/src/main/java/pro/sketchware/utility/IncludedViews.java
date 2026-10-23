package pro.sketchware.utility;

import androidx.annotation.NonNull;
import com.besome.sketch.beans.ViewBean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mod.agus.jcoderz.beans.ViewBeans;

/** Helpers for resolving a layout referenced by an Android include tag. */
public final class IncludedViews {
    private static final Pattern LAYOUT = Pattern.compile("(?:^|\\s)layout\\s*=\\s*[\\\"\']([^\\\"\']+)[\\\"\']");
    private IncludedViews() {}
    @NonNull public static String includedLayout(@NonNull ViewBean bean) {
        if (bean.inject == null || bean.inject.isEmpty()) return "";
        Matcher matcher = LAYOUT.matcher(bean.inject);
        if (!matcher.find()) return "";
        String layout = matcher.group(1).trim();
        return layout.startsWith("@layout/") ? layout.substring("@layout/".length()) : layout;
    }
    public static boolean isInclude(ViewBean bean) {
        return bean != null && bean.type == ViewBeans.VIEW_TYPE_LAYOUT_INCLUDE;
    }
}
