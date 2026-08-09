package pro.sketchware.ai.providers;

import android.content.Context;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import coil.ImageLoader;
import coil.request.ErrorResult;
import coil.request.ImageRequest;
import coil.request.SuccessResult;
import pro.sketchware.R;
import androidx.core.content.ContextCompat;

/**
 * [WHAT] Singleton for loading provider icons (SVGs or Vectors) via Coil.
 * [WHY] Enables brand-colored SVG rendering literal to mockup (Step 2).
 * [HOW] Reuses the ImageLoader from ProviderIconLoaderHelper (SVG-enabled).
 */
public class AiIconLoader {

    private static AiIconLoader instance;
    private final ImageLoader imageLoader;

    public static synchronized AiIconLoader get(Context context) {
        if (instance == null) {
            instance = new AiIconLoader(context.getApplicationContext());
        }
        return instance;
    }

    private AiIconLoader(Context context) {
        // Reuses the helper which has SvgDecoder registered
        this.imageLoader = ProviderIconLoaderHelper.INSTANCE.createImageLoader(context);
    }

    /**
     * Loads a provider icon into an ImageView.
     * @param imageView Target view.
     * @param providerId ID used to locate asset file "Providers/<id>.svg".
     */
    public void loadIcon(ImageView imageView, String providerId) {
        // Clear tint/filter for SVGs (brand colored)
        imageView.setColorFilter(null);
        
        String assetPath = "file:///android_asset/Providers/" + providerId + ".svg";
        
        ImageRequest request = new ImageRequest.Builder(imageView.getContext())
                .data(assetPath)
                .target(imageView)
                .listener(new ImageRequest.Listener() {
                    @Override public void onStart(@NonNull ImageRequest request) {}
                    @Override public void onCancel(@NonNull ImageRequest request) {}
                    @Override public void onSuccess(@NonNull ImageRequest request, @NonNull SuccessResult result) {}
                    @Override
                    public void onError(@NonNull ImageRequest request, @NonNull ErrorResult result) {
                        // Fallback to monochrome vector with theme tint
                        imageView.setImageResource(getFallbackVector(providerId));
                        imageView.setColorFilter(ContextCompat.getColor(imageView.getContext(), R.color.taj_ai_primary));
                    }
                })
                .build();
        
        imageLoader.enqueue(request);
    }

    private int getFallbackVector(String id) {
        switch (id) {
            case "google": return R.drawable.ic_brand_google;
            case "openai": return R.drawable.ic_brand_openai;
            case "anthropic": return R.drawable.ic_brand_anthropic;
            case "deepseek": return R.drawable.ic_brand_deepseek;
            case "qwen": return R.drawable.ic_brand_qwen;
            case "groq": return R.drawable.ic_brand_groq;
            case "ollama": return R.drawable.ic_brand_ollama;
            case "openrouter": return R.drawable.ic_brand_openrouter;
            default: return R.drawable.ic_mtrl_web;
        }
    }
}
