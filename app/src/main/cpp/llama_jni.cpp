#include <jni.h>
#include <string>
#include <vector>
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
