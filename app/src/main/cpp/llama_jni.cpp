#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define TAG "LlamaJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeLoad(JNIEnv *env, jclass clazz, jstring path, jint nCtx, jint nThreads) {
    const char *path_str = env->GetStringUTFChars(path, nullptr);
    LOGD("Loading model from %s with ctx=%d, threads=%d", path_str, nCtx, nThreads);

    // In a real implementation, this would initialize llama_model and llama_context.
    // For now, we return a mock pointer.
    long mock_handle = 0xABCDEF;

    env->ReleaseStringUTFChars(path, path_str);
    return (jlong)mock_handle;
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_runtime_LlamaNative_nativeComplete(JNIEnv *env, jclass clazz, jlong handle, jstring prompt, jobject callback) {
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    LOGD("Starting completion for prompt: %s", prompt_str);

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onTokenId = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)Z");

    // Mock streaming tokens
    std::vector<std::string> tokens = {"Hello", "!", " I", " am", " TAJ", ",", " your", " AI", " assistant", " running", " locally", " via", " llama.cpp", "."};

    for (const auto& token_text : tokens) {
        jstring jtoken = env->NewStringUTF(token_text.c_str());
        jboolean cont = env->CallBooleanMethod(callback, onTokenId, jtoken);
        env->DeleteLocalRef(jtoken);

        if (!cont) {
            LOGD("Completion aborted by caller");
            break;
        }

        // Small delay to simulate inference
        // usleep(50000);
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
