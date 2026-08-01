package pro.sketchware.marketplace.catalog;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.marketplace.models.MarketplaceLibrary;

/**
 * الكتالوج المعرّف للمكتبات - يعمل كمرجع محلي موثوق.
 * Curated catalog of libraries - acts as a reliable local reference.
 */
public class LibraryCatalog {

    public static List<MarketplaceLibrary> getCuratedLibraries() {
        List<MarketplaceLibrary> libraries = new ArrayList<>();

        // Glide
        libraries.add(new MarketplaceLibrary(
                "glide",
                "Glide",
                "Fast and efficient image loading library",
                "Glide is a fast and efficient open source media management and image loading framework for Android that wraps media decoding, memory and disk caching, and resource pooling into a simple and easy to use interface.",
                "4.16.0",
                "com.github.bumptech.glide:glide:4.16.0",
                "https://bumptech.github.io/glide/",
                true,
                "https://repo1.maven.org/maven2/com/github/bumptech/glide/glide/4.16.0/glide-4.16.0.aar",
                150000,
                "Glide.with(context).load(url).into(imageView);",
                null,
                true
        ));

        // Retrofit
        libraries.add(new MarketplaceLibrary(
                "retrofit",
                "Retrofit",
                "A type-safe HTTP client for Android and Java",
                "Retrofit turns your HTTP API into a Java interface. It is the most popular library for network requests in Android development.",
                "2.9.0",
                "com.squareup.retrofit2:retrofit:2.9.0",
                "https://square.github.io/retrofit/",
                true,
                "https://repo1.maven.org/maven2/com/squareup/retrofit2/retrofit/2.9.0/retrofit-2.9.0.jar",
                200000,
                "public interface GitHubService {\n  @GET(\"users/{user}/repos\")\n  Call<List<Repo>> listRepos(@Path(\"user\") String user);\n}",
                null,
                true
        ));

        // Gson
        libraries.add(new MarketplaceLibrary(
                "gson",
                "Gson",
                "A Java library that can be used to convert Java Objects into JSON and vice-versa",
                "Gson is a Java library that can be used to convert Java Objects into their JSON representation. It can also be used to convert a JSON string to an equivalent Java object.",
                "2.10.1",
                "com.google.code.gson:gson:2.10.1",
                "https://github.com/google/gson",
                false,
                "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar",
                300000,
                "Gson gson = new Gson();\nString json = gson.toJson(myObject);",
                null,
                true
        ));

        // Lottie
        libraries.add(new MarketplaceLibrary(
                "lottie",
                "Lottie",
                "Render After Effects animations natively on Android",
                "Lottie is a mobile library for Android and iOS that parses Adobe After Effects animations exported as json with Bodymovin and renders them natively on mobile!",
                "6.1.0",
                "com.airbnb.android:lottie:6.1.0",
                "http://airbnb.io/lottie/",
                true,
                "https://repo1.maven.org/maven2/com/airbnb/android/lottie/6.1.0/lottie-6.1.0.aar",
                80000,
                "<com.airbnb.lottie.LottieAnimationView\n    android:id=\"@+id/animation_view\"\n    app:lottie_fileName=\"hello_world.json\" />",
                null,
                false
        ));

        // OkHttp
        libraries.add(new MarketplaceLibrary(
                "okhttp",
                "OkHttp",
                "An HTTP & HTTP/2 client for Android and Java applications",
                "OkHttp is an HTTP client that’s efficient by default: HTTP/2 support allows all requests to the same host to share a socket. Response caching avoids the network entirely for repeat requests.",
                "4.11.0",
                "com.squareup.okhttp3:okhttp:4.11.0",
                "https://square.github.io/okhttp/",
                true,
                "https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/4.11.0/okhttp-4.11.0.jar",
                180000,
                "OkHttpClient client = new OkHttpClient();\nRequest request = new Request.Builder().url(url).build();",
                null,
                false
        ));

        return libraries;
    }
}
