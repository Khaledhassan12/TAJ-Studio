package pro.sketchware.ai.prompts;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.ai.data.AiStorage;

/**
 * [WHAT] Catalog and resolver for template variables.
 */
public class PromptVariables {

    public static class Variable {
        public final String key;
        public final String displayName;
        public final int iconRes;

        public Variable(String key, String displayName, int iconRes) {
            this.key = key;
            this.displayName = displayName;
            this.iconRes = iconRes;
        }
    }

    public static class ResolveCtx {
        public long timestamp;
        public String modelId;
        public boolean isPreview;

        public ResolveCtx(long timestamp, String modelId) {
            this.timestamp = timestamp;
            this.modelId = modelId;
        }
    }

    public static List<Variable> getCatalog() {
        List<Variable> list = new ArrayList<>();
        list.add(new Variable("time", "Time", R.drawable.ic_mtrl_clock));
        list.add(new Variable("date", "Date", R.drawable.ic_mtrl_calendar));
        list.add(new Variable("sent_time", "Sent Time", R.drawable.ic_mtrl_history));
        list.add(new Variable("sent_date", "Sent Date", R.drawable.ic_mtrl_calendar));
        list.add(new Variable("active_memory", "Active Memory", R.drawable.ic_mtrl_chip_close_circle)); // ic_mtrl_chip_close_circle is closest to 'chip'
        list.add(new Variable("model_id", "Model ID", R.drawable.ic_mtrl_info));
        return list;
    }

    public static String resolve(String key, ResolveCtx ctx, AiStorage storage) {
        switch (key) {
            case "time":
            case "sent_time":
                return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(ctx.timestamp));
            case "date":
            case "sent_date":
                return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(ctx.timestamp));
            case "model_id":
                return ctx.modelId != null ? ctx.modelId : "(none)";
            case "active_memory":
                String mem = storage.kvGet("active_memory");
                if (mem == null || mem.isEmpty()) {
                    return ctx.isPreview ? "(no active memory)" : "";
                }
                return mem;
        }
        return "{" + key + "}";
    }

    public static String resolveList(List<PromptTemplate.Item> items, ResolveCtx ctx, AiStorage storage) {
        StringBuilder sb = new StringBuilder();
        for (PromptTemplate.Item item : items) {
            if (item.type == PromptTemplate.ItemType.TEXT) {
                sb.append(item.text);
            } else {
                sb.append(resolve(item.varKey, ctx, storage));
            }
        }
        return sb.toString();
    }
}
