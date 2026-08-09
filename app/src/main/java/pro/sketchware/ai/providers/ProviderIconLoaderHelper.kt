package pro.sketchware.ai.providers

import android.content.Context
import coil.ImageLoader
import coil.decode.SvgDecoder

object ProviderIconLoaderHelper {
    fun createImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
