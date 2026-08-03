package pro.sketchware.marketplace.catalog;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.marketplace.models.MarketplaceLibrary;
import pro.sketchware.R;

/**
 * كتالوج المكتبات الحقيقي - إحداثيات Maven/JitPack موثقة 100% وبدون حلقات توليد وهمية.
 * Real library catalog - 100% verified Maven/JitPack coordinates, no fake generation loops.
 */
public class LibraryCatalog {

    // WHAT: Overload to support mostUsed flag.
    // HOW: Passing the mostUsedFlag to the constructor.
    private static void add(List<MarketplaceLibrary> list, String id, String name, String summary,
                            String coord, String cat, String github, int icon, boolean mostUsed) {
        String version = coord.contains(":") ? coord.substring(coord.lastIndexOf(':') + 1) : "1.0.0";
        
        // WHAT: Category-based icons fallback.
        // HOW: Using a human-readable categoryIconMap to replace generic icons.
        int finalIcon = icon;
        if (icon == R.drawable.ic_lib_github || icon == R.drawable.ic_lib_generic || icon == 0) {
            finalIcon = getCategoryIcon(cat);
        }

        list.add(new MarketplaceLibrary(id, name, summary, summary, version, coord,
                "https://search.maven.org/search?q=" + coord.replace(':', '+'),
                true, null, 0, null, null, mostUsed, null, finalIcon, cat, github,
                "Apache-2.0", 21, true, true, false, 0, true, "2024-01-01", null, null));
    }

    private static void add(List<MarketplaceLibrary> list, String id, String name, String summary,
                            String coord, String cat, String github, int icon) {
        add(list, id, name, summary, coord, cat, github, icon, false);
    }

    private static int getCategoryIcon(String category) {
        if (category == null) return R.drawable.ic_lib_github;
        switch (category) {
            case "Image Loading": return R.drawable.ic_cat_image;
            case "Networking": return R.drawable.ic_cat_network;
            case "Database": return R.drawable.ic_cat_database;
            case "Animation": return R.drawable.ic_cat_animation;
            case "UI": return R.drawable.ic_cat_ui;
            case "Navigation": return R.drawable.ic_cat_map;
            case "Camera": return R.drawable.ic_cat_camera;
            case "Security": return R.drawable.ic_cat_security;
            case "Media": return R.drawable.ic_cat_media;
            case "Architecture": return R.drawable.ic_cat_di;
            case "Background": return R.drawable.ic_cat_background;
            case "JSON": return R.drawable.ic_cat_json;
            case "QR & Barcode": return R.drawable.ic_cat_qr;
            case "Debug": return R.drawable.ic_cat_debug;
            default: return R.drawable.ic_lib_github;
        }
    }

    private static List<MarketplaceLibrary> cachedCuratedCatalog = null;

    /**
     * WHAT: Static caching of the library list to avoid repetitive allocations.
     * HOW: Build the list once and return the cached instance on subsequent calls.
     * WHY: The catalog is hardcoded and constant; re-creating 50+ objects on every call is wasteful.
     *
     * (عربي)
     * ماذا: تخزين قائمة المكتبات في ذاكرة مؤقتة ثابتة (Static Cache) لتجنب إعادة الإنشاء المتكررة.
     * كيف: بناء القائمة مرة واحدة فقط وإرجاع النسخة المحفوظة في الاستدعاءات اللاحقة.
     * لماذا: الكتالوج ثابت (Hardcoded)؛ إعادة إنشاء أكثر من 50 كائناً في كل مرة يستهلك الذاكرة والمعالج بلا داعٍ.
     */
    public static synchronized List<MarketplaceLibrary> getCuratedLibraries() {
        if (cachedCuratedCatalog != null) {
            return cachedCuratedCatalog;
        }

        List<MarketplaceLibrary> libraries = new ArrayList<>();
        int G = R.drawable.ic_lib_github; // fallback أنيق للمكتبات بلا أيقونة خاصة
        
        // WHAT: Restore Most-Used section by tagging famous libraries.
        // HOW: Using the new add() overload with mostUsedFlag=true.
        
        // --- Image Loading ---
        add(libraries, "glide", "Glide", "Fast and efficient image loading", "com.github.bumptech.glide:glide:4.16.0", "Image Loading", "https://github.com/bumptech/glide", R.drawable.ic_lib_glide, true);
        add(libraries, "coil", "Coil", "Image loading backed by Kotlin Coroutines", "io.coil-kt:coil:2.5.0", "Image Loading", "https://github.com/coil-kt/coil", R.drawable.ic_lib_coil, true);
        add(libraries, "picasso", "Picasso", "Powerful image downloading and caching", "com.squareup.picasso:picasso:2.8", "Image Loading", "https://github.com/square/picasso", R.drawable.ic_mtrl_palette);
        add(libraries, "fresco", "Fresco", "Manage images and their memory", "com.facebook.fresco:fresco:3.1.3", "Image Loading", "https://github.com/facebook/fresco", G);
        
        // --- Animation ---
        add(libraries, "lottie", "Lottie", "Render After Effects animations natively", "com.airbnb.android:lottie:6.3.0", "Animation", "https://github.com/airbnb/lottie-android", R.drawable.ic_mtrl_animation, true);
        add(libraries, "shimmer", "Shimmer", "Shimmer loading effect for views", "com.facebook.shimmer:shimmer:0.5.0", "Animation", "https://github.com/facebook/shimmer-android", G);
        
        // --- Networking ---
        add(libraries, "retrofit", "Retrofit", "Type-safe HTTP client", "com.squareup.retrofit2:retrofit:2.9.0", "Networking", "https://github.com/square/retrofit", R.drawable.ic_lib_retrofit, true);
        add(libraries, "okhttp", "OkHttp", "Efficient HTTP & HTTP/2 client", "com.squareup.okhttp3:okhttp:4.12.0", "Networking", "https://github.com/square/okhttp", R.drawable.ic_lib_okhttp, true);
        add(libraries, "okhttp-logging", "OkHttp Logging", "Log HTTP requests and responses", "com.squareup.okhttp3:logging-interceptor:4.12.0", "Networking", "https://github.com/square/okhttp", R.drawable.ic_lib_okhttp);
        add(libraries, "volley", "Volley", "HTTP library for Android by Google", "com.android.volley:volley:1.2.1", "Networking", "https://github.com/google/volley", G);
        
        // --- JSON ---
        add(libraries, "gson", "Gson", "Java serialization/deserialization to JSON", "com.google.code.gson:gson:2.10.1", "JSON", "https://github.com/google/gson", G, true);
        add(libraries, "moshi", "Moshi", "Modern JSON library for Android and Java", "com.squareup.moshi:moshi:1.15.1", "JSON", "https://github.com/square/moshi", G);
        
        // --- Database ---
        add(libraries, "room", "Room", "SQLite abstraction layer by AndroidX", "androidx.room:room-runtime:2.6.1", "Database", "https://developer.android.com/training/data-storage/room", R.drawable.ic_mtrl_database_added, true);
        add(libraries, "datastore", "DataStore", "Modern preferences & data storage", "androidx.datastore:datastore-preferences:1.1.1", "Database", "https://developer.android.com/topic/libraries/architecture/datastore", G);
        
        // --- UI ---
        add(libraries, "material", "Material Components", "Material Design components for Android", "com.google.android.material:material:1.11.0", "UI", "https://github.com/material-components/material-components-android", R.drawable.ic_mtrl_material3, true);
        add(libraries, "appcompat", "AppCompat", "Backward-compatible Material widgets", "androidx.appcompat:appcompat:1.7.0", "UI", "https://developer.android.com/jetpack/androidx/releases/appcompat", G);
        add(libraries, "constraintlayout", "ConstraintLayout", "Flexible relative layout", "androidx.constraintlayout:constraintlayout:2.1.4", "UI", "https://developer.android.com/develop/ui/views/layout/constraint-layout", G);
        add(libraries, "recyclerview", "RecyclerView", "Efficient scrollable lists", "androidx.recyclerview:recyclerview:1.3.2", "UI", "https://developer.android.com/jetpack/androidx/releases/recyclerview", G);
        add(libraries, "viewpager2", "ViewPager2", "Swipeable pages with RecyclerView", "androidx.viewpager2:viewpager2:1.1.0", "UI", "https://developer.android.com/jetpack/androidx/releases/viewpager2", G);
        add(libraries, "cardview", "CardView", "Card-style container widget", "androidx.cardview:cardview:1.0.0", "UI", "https://developer.android.com/jetpack/androidx/releases/cardview", G);
        add(libraries, "circleimageview", "CircleImageView", "Circular ImageView widget", "de.hdodenhof:circleimageview:3.1.0", "UI", "https://github.com/hdodenhof/CircleImageView", G);
        add(libraries, "flexbox", "FlexboxLayout", "CSS flexbox layout for Android", "com.google.android.flexbox:flexbox:3.0.0", "UI", "https://github.com/google/flexbox-layout", G);
        add(libraries, "swiperefresh", "SwipeRefreshLayout", "Pull-to-refresh layout", "androidx.swiperefreshlayout:swiperefreshlayout:1.1.0", "UI", "https://developer.android.com/jetpack/androidx/releases/swiperefreshlayout", G);
        add(libraries, "preference", "Preference", "Settings screens library", "androidx.preference:preference:1.2.1", "UI", "https://developer.android.com/jetpack/androidx/releases/preference", G);
        
        // --- Architecture / Lifecycle ---
        add(libraries, "viewmodel", "ViewModel", "Lifecycle-aware view state holder", "androidx.lifecycle:lifecycle-viewmodel:2.7.0", "Architecture", "https://developer.android.com/topic/libraries/architecture/viewmodel", G);
        add(libraries, "livedata", "LiveData", "Observable lifecycle-aware data holder", "androidx.lifecycle:lifecycle-livedata:2.7.0", "Architecture", "https://developer.android.com/topic/libraries/architecture/livedata", G);
        add(libraries, "core-ktx", "Core KTX", "Kotlin extensions for Android core", "androidx.core:core-ktx:1.13.1", "Architecture", "https://developer.android.com/jetpack/androidx/releases/core", G);
        add(libraries, "fragment-ktx", "Fragment KTX", "Kotlin extensions for Fragment", "androidx.fragment:fragment-ktx:1.7.1", "Architecture", "https://developer.android.com/jetpack/androidx/releases/fragment", G);
        add(libraries, "activity-ktx", "Activity KTX", "Kotlin extensions for Activity", "androidx.activity:activity-ktx:1.9.0", "Architecture", "https://developer.android.com/jetpack/androidx/releases/activity", G);
        add(libraries, "paging", "Paging 3", "Load and display paged data", "androidx.paging:paging-runtime:3.3.0", "Architecture", "https://developer.android.com/topic/libraries/architecture/paging/v3-overview", G);
        add(libraries, "eventbus", "EventBus", "Publish/subscribe event bus", "org.greenrobot:eventbus:3.3.1", "Architecture", "https://github.com/greenrobot/EventBus", G);
        
        // --- Navigation ---
        add(libraries, "navigation", "Navigation", "In-app navigation framework", "androidx.navigation:navigation-fragment:2.7.7", "Navigation", "https://developer.android.com/guide/navigation", G, true);
        
        // --- Background / Concurrency ---
        add(libraries, "coroutines", "Coroutines", "Kotlin coroutines for Android", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3", "Background", "https://github.com/Kotlin/kotlinx.coroutines", G);
        add(libraries, "workmanager", "WorkManager", "Deferrable guaranteed background work", "androidx.work:work-runtime:2.9.0", "Background", "https://developer.android.com/topic/libraries/architecture/workmanager", G);
        add(libraries, "rxjava3", "RxJava 3", "Reactive extensions for the JVM", "io.reactivex.rxjava3:rxjava:3.1.8", "Background", "https://github.com/ReactiveX/RxJava", G);
        add(libraries, "rxandroid", "RxAndroid", "Android bindings for RxJava 3", "io.reactivex.rxjava3:rxandroid:3.0.2", "Background", "https://github.com/ReactiveX/RxAndroid", G);
        
        // --- Dependency Injection ---
        add(libraries, "hilt", "Hilt", "Dependency injection for Android", "com.google.dagger:hilt-android:2.50", "Dependency Injection", "https://dagger.dev/hilt/", G, true);
        
        // --- Camera ---
        add(libraries, "camerax", "CameraX", "Jetpack camera library", "androidx.camera:camera-core:1.3.4", "Camera", "https://developer.android.com/training/camerax", G);
        
        // --- Media ---
        add(libraries, "media3", "Media3 ExoPlayer", "Modern media playback (ExoPlayer)", "androidx.media3:media3-exoplayer:1.3.1", "Media", "https://developer.android.com/guide/topics/media/media3", G);
        
        // --- Security ---
        add(libraries, "biometric", "Biometric", "Fingerprint & face authentication", "androidx.biometric:biometric:1.1.0", "Security", "https://developer.android.com/jetpack/androidx/releases/biometric", G);
        add(libraries, "security-crypto", "Security Crypto", "Encrypted SharedPreferences & files", "androidx.security:security-crypto:1.0.0", "Security", "https://developer.android.com/topic/security/data", G);
        
        // --- Debug ---
        add(libraries, "timber", "Timber", "Logger with a small, extensible API", "com.jakewharton.timber:timber:5.0.1", "Debug", "https://github.com/JakeWharton/timber", G);
        add(libraries, "leakcanary", "LeakCanary", "Memory leak detection for Android", "com.squareup.leakcanary:leakcanary-android:2.13", "Debug", "https://github.com/square/leakcanary", G);
        
        // --- QR / Barcode ---
        add(libraries, "zxing", "ZXing Embedded", "Barcode & QR scanning (embedded)", "com.journeyapps:zxing-android-embedded:4.3.0", "QR & Barcode", "https://github.com/journeyapps/zxing-android-embedded", G);
        
        // --- Firebase ---
        add(libraries, "firebase-auth", "Firebase Auth", "Secure user sign-in", "com.google.firebase:firebase-auth:22.3.1", "Firebase", "https://firebase.google.com/docs/auth", R.drawable.ic_mtrl_firebase_auth, true);
        add(libraries, "firebase-firestore", "Firebase Firestore", "Cloud NoSQL database", "com.google.firebase:firebase-firestore:24.10.3", "Firebase", "https://firebase.google.com/docs/firestore", R.drawable.ic_mtrl_firebase_auth);
        add(libraries, "firebase-storage", "Firebase Storage", "Cloud file storage", "com.google.firebase:firebase-storage:20.3.0", "Firebase", "https://firebase.google.com/docs/storage", R.drawable.ic_mtrl_firebase_auth);
        
        cachedCuratedCatalog = java.util.Collections.unmodifiableList(libraries);
        return cachedCuratedCatalog;
    }
}
