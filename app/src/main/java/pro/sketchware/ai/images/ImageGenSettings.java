package pro.sketchware.ai.images;

import android.content.Context;

import pro.sketchware.ai.data.AiStorage;

/**
 * [WHAT] SSOT for Image Generation settings.
 * [WHY] P2-IG: Wraps AiStorage kv for typed access (R5/R16); single writer per field.
 * [HOW] Singleton; strictly derived from persisted storage; sizes clamped to 64..2048.
 *
 * [العربية]
 * المصدر الوحيد لإعدادات توليد الصور.
 * يغلف مفاتيح AiStorage بوصول مُنمّط مع كاتب واحد لكل حقل وضبط الأبعاد بين 64 و2048.
 */
public class ImageGenSettings {

    public static final int MIN_SIZE = 64;
    public static final int MAX_SIZE = 2048;
    public static final int DEFAULT_SIZE = 1024;

    /**
     * Keywords that mark a model as an image-generation model (lowercase match
     * against modelId or alias).
     */
    private static final String[] IMAGE_MODEL_KEYWORDS = {
            "dall-e", "dalle", "gpt-image", "imagen", "stable", "sdxl", "sd3",
            "flux", "midjourney", "niji", "seedream", "wanx", "qwen-image",
            "cogview", "kolors", "ideogram", "recraft"
    };

    private static ImageGenSettings instance;
    private final AiStorage storage;

    private ImageGenSettings(Context context) {
        this.storage = AiStorage.get(context);
    }

    public static synchronized ImageGenSettings get(Context context) {
        if (instance == null) {
            instance = new ImageGenSettings(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isEnabled() {
        return storage.isImageGenEnabled();
    }

    public void setEnabled(boolean enabled) {
        storage.setImageGenEnabled(enabled);
    }

    /**
     * @return "providerId:modelId" or null when nothing is selected.
     */
    public String getModel() {
        return storage.getImageGenModel();
    }

    public void setModel(String modelId) {
        storage.setImageGenModel(modelId);
    }

    public int getSizeW() {
        return clamp(storage.getImageGenSizeW());
    }

    public void setSizeW(int width) {
        storage.setImageGenSizeW(clamp(width));
    }

    public int getSizeH() {
        return clamp(storage.getImageGenSizeH());
    }

    public void setSizeH(int height) {
        storage.setImageGenSizeH(clamp(height));
    }

    /**
     * [WHAT] True when the given text (model id or alias) matches any known
     * image-generation keyword.
     */
    public static boolean matchesImageKeywords(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        for (String keyword : IMAGE_MODEL_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    private static int clamp(int value) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, value));
    }
}
