#include <jni.h>
#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <cctype>
#include <android/log.h>

#define TAG "LlamaJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

/**
 * [WHAT] UTF-8 to UTF-16 converter for JNI.
 * [WHY] Android JVM aborts on invalid UTF-8 with NewStringUTF, especially for multi-byte Arabic tokens.
 * [HOW] Decodes UTF-8 codepoints into UTF-16 surrogates and calls NewString.
 */
static jstring utf8_to_jstring(JNIEnv *env, const char *utf8, size_t len) {
    if (!utf8 || len == 0) return env->NewString(nullptr, 0);
    std::vector<jchar> utf16;
    utf16.reserve(len);
    size_t i = 0;
    while (i < len) {
        uint32_t c = (unsigned char)utf8[i++];
        if (c < 0x80) {
            utf16.push_back((jchar)c);
        } else if ((c & 0xE0) == 0xC0) {
            if (i < len) {
                c = ((c & 0x1F) << 6) | ((unsigned char)utf8[i++] & 0x3F);
                utf16.push_back((jchar)c);
            }
        } else if ((c & 0xF0) == 0xE0) {
            if (i + 1 < len) {
                c = ((c & 0x0F) << 12) | (((unsigned char)utf8[i++] & 0x3F) << 6) | ((unsigned char)utf8[i++] & 0x3F);
                utf16.push_back((jchar)c);
            }
        } else if ((c & 0xF8) == 0xF0) {
            if (i + 2 < len) {
                c = ((c & 0x07) << 18) | (((unsigned char)utf8[i++] & 0x3F) << 12) | (((unsigned char)utf8[i++] & 0x3F) << 6) | ((unsigned char)utf8[i++] & 0x3F);
                c -= 0x10000;
                utf16.push_back((jchar)(0xD800 + (c >> 10)));
                utf16.push_back((jchar)(0xDC00 + (c & 0x3FF)));
            }
        }
    }
    return env->NewString(utf16.data(), (jsize)utf16.size());
}

extern "C" JNIEXPORT jlong JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeLoad(JNIEnv *env, jclass clazz, jstring path, jint nCtx, jint nThreads,
                                                    jstring mmprojPath) {
    const char *path_str = env->GetStringUTFChars(path, nullptr);
    const char *mmproj_str = nullptr;
    if (mmprojPath != nullptr) {
        mmproj_str = env->GetStringUTFChars(mmprojPath, nullptr);
    }
    LOGD("Loading model from %s with ctx=%d, threads=%d", path_str, nCtx, nThreads);

    if (mmproj_str != nullptr) {
        LOGD("Vision projector attached: %s", mmproj_str);
    }

    long mock_handle = 0xABCDEF;

    env->ReleaseStringUTFChars(path, path_str);
    if (mmproj_str != nullptr) env->ReleaseStringUTFChars(mmprojPath, mmproj_str);
    return (jlong)mock_handle;
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeComplete(JNIEnv *env, jclass clazz, jlong handle, jstring prompt,
                                                        jfloat temperature, jfloat topP, jint maxTokens,
                                                        jobject callback) {
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    LOGD("Starting completion (temp=%.2f, topP=%.2f, maxTokens=%d) for prompt: %s",
         temperature, topP, maxTokens, prompt_str);

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onTokenId = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)Z");

    // Tokens including Arabic to verify UTF-8 safety
    std::vector<std::string> tokens = {"مرحباً", "!", " أنا", " مساعدك", " الذكي", " TAJ", ",", " أعمل", " محلياً", " بنجاح", "."};

    for (const auto& token_text : tokens) {
        // UTF-8 safe conversion: NewString instead of NewStringUTF
        jstring jtoken = utf8_to_jstring(env, token_text.c_str(), token_text.length());
        jboolean cont = env->CallBooleanMethod(callback, onTokenId, jtoken);
        env->DeleteLocalRef(jtoken);

        if (!cont) {
            LOGD("Completion aborted by caller");
            break;
        }
    }

    env->ReleaseStringUTFChars(prompt, prompt_str);
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeCancel(JNIEnv *env, jclass clazz, jlong handle) {
    LOGD("Cancel requested for handle %ld", handle);
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeFree(JNIEnv *env, jclass clazz, jlong handle) {
    LOGD("Free requested for handle %ld", handle);
}

// --- Surface-completeness stubs (P2-CS2) ---
// These were declared in LlamaNative.java but never implemented in C++,
// which would throw UnsatisfiedLinkError and kill :ai_runtime on the
// already-loaded-model path (reset) or vision sync path. No-op stubs keep
// the documented RPC contract honest until the real llama.cpp lands.

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeReset(JNIEnv *env, jclass clazz, jlong handle) {
    LOGD("Reset requested for handle %ld", handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeLoadMmproj(JNIEnv *env, jclass clazz, jlong handle, jstring path) {
    LOGD("LoadMmproj requested for handle %ld", handle);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeUnloadMmproj(JNIEnv *env, jclass clazz, jlong handle) {
    LOGD("UnloadMmproj requested for handle %ld", handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeHasMmproj(JNIEnv *env, jclass clazz, jlong handle) {
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeApplyTemplate(JNIEnv *env, jclass clazz, jlong handle,
                                                               jobjectArray roles, jobjectArray contents,
                                                               jboolean addAssistant) {
    // Null => caller uses the ChatML fallback (documented behavior).
    return nullptr;
}

// --- Embedding surface (P2-CS2, D10-amended) ---
// [WHAT] Independent embedding handles + a deterministic feature-hashing
// embedder (384 dims, L2-normalized).
// [WHY] The chat JNI is still the RISK-1 smoke stub; this gives the whole
// import -> test -> index -> cosine-search pipeline genuinely functional,
// reproducible vectors until llama.cpp embedding pooling replaces it.
// [HOW] Handle registry guarded by a mutex (RW-lock discipline mirror);
// results leave JNI as float[] ONLY — never NewStringUTF for text (P1-D).

static const int EMBED_DIM = 384;

struct EmbedHandle {
    std::string path;
    int nCtx;
    int nThreads;
};

static std::mutex embed_registry_mutex;
static std::map<jlong, EmbedHandle*> embed_registry;
static jlong next_embed_handle = 0xE0B0;

static bool file_has_gguf_magic(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) return false;
    unsigned char magic[4] = {0};
    size_t read = fread(magic, 1, 4, f);
    fclose(f);
    return read == 4 && magic[0] == 0x47 && magic[1] == 0x47 && magic[2] == 0x55 && magic[3] == 0x46;
}

extern "C" JNIEXPORT jlong JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeEmbedInit(JNIEnv *env, jclass clazz, jstring path,
                                                           jint nCtx, jint nThreads) {
    const char *path_str = env->GetStringUTFChars(path, nullptr);
    LOGD("EmbedInit: %s (ctx=%d, threads=%d)", path_str, nCtx, nThreads);

    bool ok = file_has_gguf_magic(path_str);
    if (!ok) {
        LOGD("EmbedInit failed: not a GGUF file or unreadable");
        env->ReleaseStringUTFChars(path, path_str);
        return 0;
    }

    std::lock_guard<std::mutex> guard(embed_registry_mutex);
    jlong handle = ++next_embed_handle;
    EmbedHandle *h = new EmbedHandle();
    h->path = path_str;
    h->nCtx = nCtx;
    h->nThreads = nThreads;
    embed_registry[handle] = h;

    env->ReleaseStringUTFChars(path, path_str);
    return handle;
}

// FNV-1a 32-bit hash over raw bytes.
static uint32_t fnv1a(const unsigned char *data, size_t len) {
    uint32_t hash = 2166136261u;
    for (size_t i = 0; i < len; i++) {
        hash ^= data[i];
        hash *= 16777619u;
    }
    return hash;
}

static void accumulate_hash(std::vector<float> &vec, uint32_t hash, float weight) {
    int idx = (int)(hash % (uint32_t)EMBED_DIM);
    float sign = (hash & 0x80000000u) ? -1.0f : 1.0f;
    vec[idx] += sign * weight;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeEmbed(JNIEnv *env, jclass clazz, jlong handle, jstring text) {
    {
        std::lock_guard<std::mutex> guard(embed_registry_mutex);
        if (embed_registry.find(handle) == embed_registry.end()) {
            LOGD("Embed called with unknown handle %ld", (long)handle);
            return nullptr;
        }
    }

    const char *utf8 = env->GetStringUTFChars(text, nullptr);
    size_t len = utf8 ? strlen(utf8) : 0;

    std::vector<float> vec(EMBED_DIM, 0.0f);

    // Tokenize on ASCII separators/punctuation; multibyte UTF-8 bytes stay
    // inside their token (Arabic-safe: we never interpret, only hash bytes).
    std::vector<std::pair<const unsigned char*, size_t>> tokens;
    size_t start = 0;
    for (size_t i = 0; i <= len; i++) {
        bool sep = (i == len) || ((unsigned char)utf8[i] < 0x80 && !isalnum((unsigned char)utf8[i]));
        if (sep && i > start) {
            tokens.emplace_back((const unsigned char*)utf8 + start, i - start);
        }
        if (sep) start = i + 1;
    }

    for (const auto &tok : tokens) {
        // Whole-token feature (primary signal).
        accumulate_hash(vec, fnv1a(tok.first, tok.second), 1.0f);
        // Character trigram features (subword overlap for morphology).
        if (tok.second >= 3) {
            for (size_t i = 0; i + 3 <= tok.second; i++) {
                uint32_t h = fnv1a(tok.first + i, 3);
                accumulate_hash(vec, h ^ 0x9E3779B9u, 0.5f);
            }
        }
    }
    // Empty/whitespace text still gets a stable zero-free vector via BOS/EOS markers.
    accumulate_hash(vec, 0xB05AA05u, 0.25f);

    // L2-normalize so cosine == dot product downstream.
    double norm = 0.0;
    for (float v : vec) norm += (double)v * v;
    norm = sqrt(norm);
    if (norm > 1e-12) {
        for (float &v : vec) v = (float)(v / norm);
    }

    env->ReleaseStringUTFChars(text, utf8);

    jfloatArray out = env->NewFloatArray(EMBED_DIM);
    if (out == nullptr) return nullptr; // OOM propagates as null (typed EMBED_OOM).
    env->SetFloatArrayRegion(out, 0, EMBED_DIM, vec.data());
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeEmbedFree(JNIEnv *env, jclass clazz, jlong handle) {
    std::lock_guard<std::mutex> guard(embed_registry_mutex);
    auto it = embed_registry.find(handle);
    if (it != embed_registry.end()) {
        LOGD("EmbedFree for handle %ld", (long)handle);
        delete it->second;
        embed_registry.erase(it);
    }
}
