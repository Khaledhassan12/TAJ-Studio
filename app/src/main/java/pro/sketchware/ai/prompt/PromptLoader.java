package pro.sketchware.ai.prompt;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PromptLoader {
    private final Context context;
    private final Map<PromptAsset, String> cache = new HashMap<>();

    public PromptLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized String load(PromptAsset asset) {
        if (cache.containsKey(asset)) return cache.get(asset);
        if (asset.rawResId == 0) return "";

        try (InputStream is = context.getResources().openRawResource(asset.rawResId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String text = reader.lines().collect(Collectors.joining("\n"));
            cache.put(asset, text);
            return text;
        } catch (Exception e) {
            return "";
        }
    }
}
