#include <jni.h>
#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <atomic>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <cerrno>
#include <cctype>
#include <android/log.h>
#include "llama.h"

#define TAG "LlamaJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Route llama.cpp/ggml internal logs to Logcat under tag "llama.cpp"
static void llama_log_callback_android(enum ggml_log_level level, const char *text, void *user_data) {
    int prio;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: prio = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN:  prio = ANDROID_LOG_WARN;  break;
        case GGML_LOG_LEVEL_INFO:  prio = ANDROID_LOG_INFO;  break;
        default:                   prio = ANDROID_LOG_DEBUG; break;
    }
    __android_log_write(prio, "llama.cpp", text);
}

// UTF-8 to UTF-16 converter (safe for Arabic/multi-byte)
// FIXED: Removed unsequenced modifications (i++)
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
                c = ((c & 0x1F) << 6);
                c |= ((unsigned char)utf8[i++] & 0x3F);
                utf16.push_back((jchar)c);
            }
        } else if ((c & 0xF0) == 0xE0) {
            if (i + 1 < len) {
                c = ((c & 0x0F) << 12);
                c |= (((unsigned char)utf8[i++] & 0x3F) << 6);
                c |= ((unsigned char)utf8[i++] & 0x3F);
                utf16.push_back((jchar)c);
            }
        } else if ((c & 0xF8) == 0xF0) {
            if (i + 2 < len) {
                c = ((c & 0x07) << 18);
                c |= (((unsigned char)utf8[i++] & 0x3F) << 12);
                c |= (((unsigned char)utf8[i++] & 0x3F) << 6);
                c |= ((unsigned char)utf8[i++] & 0x3F);
                c -= 0x10000;
                utf16.push_back((jchar)(0xD800 + (c >> 10)));
                utf16.push_back((jchar)(0xDC00 + (c & 0x3FF)));
            }
        }
    }
    return env->NewString(utf16.data(), (jsize)utf16.size());
}

// Initialize backend on library load
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    llama_log_set(llama_log_callback_android, nullptr);
    llama_backend_init();
    llama_numa_init(GGML_NUMA_STRATEGY_DISABLED);
    LOGD("JNI_OnLoad: llama.cpp backend initialized");
    return JNI_VERSION_1_6;
}

// Handle registry
struct ChatHandle {
    llama_model *model;
    llama_context *ctx;
    std::atomic<bool> cancel_flag;
};

static std::mutex registry_mutex;
static std::map<jlong, ChatHandle*> registry;
static jlong next_handle = 0xB000;

extern "C" JNIEXPORT jlong JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeLoad(JNIEnv *env, jclass clazz,
                                                      jstring path, jint nCtx, jint nThreads, jstring mmprojPath) {
    const char *path_str = env->GetStringUTFChars(path, nullptr);

    // Probe 1: File existence and access
    FILE *f = fopen(path_str, "rb");
    if (!f) {
        LOGE("Failed to open model file: %s (errno=%d: %s)", path_str, errno, strerror(errno));
        env->ReleaseStringUTFChars(path, path_str);
        return 0;
    }

    // Probe 2: File size
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fclose(f);
    LOGD("Loading model: %s (size=%ld bytes, ctx=%d, threads=%d)", path_str, size, nCtx, nThreads);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU-only

    llama_model *model = llama_model_load_from_file(path_str, model_params);
    env->ReleaseStringUTFChars(path, path_str);

    if (!model) {
        LOGE("Failed to load model (llama_model_load_from_file returned NULL)");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx;
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = nThreads;

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to init context (llama_init_from_model returned NULL)");
        llama_model_free(model);
        return 0;
    }

    std::lock_guard<std::mutex> lock(registry_mutex);
    jlong handle = ++next_handle;
    ChatHandle *h = new ChatHandle{model, ctx, std::atomic<bool>(false)};
    registry[handle] = h;

    LOGD("Model loaded successfully, handle=%ld", (long)handle);
    return handle;
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeComplete(JNIEnv *env, jclass clazz,
                                                          jlong handle, jstring prompt, jfloat temperature, jfloat topP, jint maxTokens, jobject callback) {

    ChatHandle *h;
    {
        std::lock_guard<std::mutex> lock(registry_mutex);
        auto it = registry.find(handle);
        if (it == registry.end()) {
            LOGE("Invalid handle: %ld", (long)handle);
            return;
        }
        h = it->second;
    }

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    LOGD("Starting completion for handle %ld (temp=%.2f, topP=%.2f, max=%d)",
         (long)handle, temperature, topP, maxTokens);

    h->cancel_flag.store(false);

    const llama_vocab *vocab = llama_model_get_vocab(h->model);

    // Tokenize prompt: add_special=false, parse_special=false (Java handles ChatML)
    int n_prompt_tokens = -llama_tokenize(vocab, prompt_str, strlen(prompt_str), nullptr, 0, false, false);
    std::vector<llama_token> prompt_tokens(n_prompt_tokens);
    llama_tokenize(vocab, prompt_str, strlen(prompt_str), prompt_tokens.data(), n_prompt_tokens, false, false);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    // Process prompt
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), n_prompt_tokens);
    if (llama_decode(h->ctx, batch) != 0) {
        LOGE("Failed to decode prompt");
        return;
    }

    // Sampling chain (b4539 API requires params struct)
    struct llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(0));

    // Get callback method
    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onTokenId = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)Z");

    // Generate tokens
    std::vector<llama_token> generated;
    for (int i = 0; i < maxTokens && !h->cancel_flag.load(); i++) {
        llama_token token = llama_sampler_sample(smpl, h->ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            jstring jtoken = utf8_to_jstring(env, buf, n);
            jboolean cont = env->CallBooleanMethod(callback, onTokenId, jtoken);
            env->DeleteLocalRef(jtoken);
            if (!cont) {
                LOGD("Caller requested stop");
                break;
            }
        }

        generated.push_back(token);

        llama_batch next_batch = llama_batch_get_one(&token, 1);
        if (llama_decode(h->ctx, next_batch) != 0) {
            LOGE("Failed to decode token");
            break;
        }
    }

    llama_sampler_free(smpl);
    LOGD("Completion finished, generated %zu tokens", generated.size());
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeCancel(JNIEnv *env, jclass clazz, jlong handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    auto it = registry.find(handle);
    if (it != registry.end()) {
        it->second->cancel_flag.store(true);
        LOGD("Cancel requested for handle %ld", (long)handle);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeFree(JNIEnv *env, jclass clazz, jlong handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    auto it = registry.find(handle);
    if (it != registry.end()) {
        ChatHandle *h = it->second;
        llama_free(h->ctx);
        llama_model_free(h->model);
        delete h;
        registry.erase(it);
        LOGD("Freed handle %ld", (long)handle);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeReset(JNIEnv *env, jclass clazz, jlong handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    auto it = registry.find(handle);
    if (it != registry.end()) {
        llama_kv_cache_clear(it->second->ctx);
        LOGD("Reset for handle %ld", (long)handle);
    }
}

// Vision stubs
extern "C" JNIEXPORT jboolean JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeLoadMmproj(JNIEnv *env, jclass clazz, jlong handle, jstring path) {
    return JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeUnloadMmproj(JNIEnv *env, jclass clazz, jlong handle) {}
extern "C" JNIEXPORT jboolean JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeHasMmproj(JNIEnv *env, jclass clazz, jlong handle) {
    return JNI_FALSE;
}
extern "C" JNIEXPORT jstring JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeApplyTemplate(JNIEnv *env, jclass clazz, jlong handle,
                                                               jobjectArray roles, jobjectArray contents, jboolean addAssistant) {
    return nullptr;
}

// Embedding surface (deterministic hash-based) - preserved from previous version
static const int EMBED_DIM = 384;
struct EmbedHandle { std::string path; int nCtx; int nThreads; };
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
Java_pro_sketchware_ai_runtime_LlamaNative_nativeEmbedInit(JNIEnv *env, jclass clazz, jstring path, jint nCtx, jint nThreads) {
    const char *path_str = env->GetStringUTFChars(path, nullptr);
    bool ok = file_has_gguf_magic(path_str);
    if (!ok) {
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

static uint32_t fnv1a(const unsigned char *data, size_t len) {
    uint32_t hash = 2166136261u;
    for (size_t i = 0; i < len; i++) { hash ^= data[i]; hash *= 16777619u; }
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
        if (embed_registry.find(handle) == embed_registry.end()) return nullptr;
    }

    const char *utf8 = env->GetStringUTFChars(text, nullptr);
    size_t len = utf8 ? strlen(utf8) : 0;
    std::vector<float> vec(EMBED_DIM, 0.0f);

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
        accumulate_hash(vec, fnv1a(tok.first, tok.second), 1.0f);
        if (tok.second >= 3) {
            for (size_t i = 0; i + 3 <= tok.second; i++) {
                uint32_t h = fnv1a(tok.first + i, 3);
                accumulate_hash(vec, h ^ 0x9E3779B9u, 0.5f);
            }
        }
    }
    accumulate_hash(vec, 0xB05AA05u, 0.25f);

    double norm = 0.0;
    for (float v : vec) norm += (double)v * v;
    norm = sqrt(norm);
    if (norm > 1e-12) {
        for (float &v : vec) v = (float)(v / norm);
    }

    env->ReleaseStringUTFChars(text, utf8);
    jfloatArray out = env->NewFloatArray(EMBED_DIM);
    if (out == nullptr) return nullptr;
    env->SetFloatArrayRegion(out, 0, EMBED_DIM, vec.data());
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeEmbedFree(JNIEnv *env, jclass clazz, jlong handle) {
    std::lock_guard<std::mutex> guard(embed_registry_mutex);
    auto it = embed_registry.find(handle);
    if (it != embed_registry.end()) {
        delete it->second;
        embed_registry.erase(it);
    }
}