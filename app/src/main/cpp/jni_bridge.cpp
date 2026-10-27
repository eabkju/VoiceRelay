#include <jni.h>
#include <android/log.h>
#include <memory>
#include <mutex>
#include "audio_engine.h"

#define TAG "VoiceRelayJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::unique_ptr<AudioEngine> gAudioEngine;
static JavaVM* gJavaVM = nullptr;
static jobject gBridgeObjectGlobal = nullptr;
static jmethodID gPacketCallbackMethod = nullptr;
static std::mutex gJniMutex;

jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    gJavaVM = vm;
    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeInit(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(gJniMutex);

    if (gBridgeObjectGlobal) {
        env->DeleteGlobalRef(gBridgeObjectGlobal);
        gBridgeObjectGlobal = nullptr;
    }
    gBridgeObjectGlobal = env->NewGlobalRef(thiz);

    jclass clazz = env->GetObjectClass(thiz);
    gPacketCallbackMethod = env->GetMethodID(clazz, "onAudioPacketCaptured", "([B)V");

    if (!gAudioEngine) {
        gAudioEngine = std::make_unique<AudioEngine>();
    }

    gAudioEngine->setPacketSendCallback([](const uint8_t* data, size_t size) {
        if (!gJavaVM || !gBridgeObjectGlobal || !gPacketCallbackMethod) return;

        JNIEnv* callbackEnv = nullptr;
        bool attached = false;
        jint res = gJavaVM->GetEnv(reinterpret_cast<void**>(&callbackEnv), JNI_VERSION_1_6);

        if (res == JNI_EDETACHED) {
            if (gJavaVM->AttachCurrentThread(&callbackEnv, nullptr) == 0) {
                attached = true;
            } else {
                return;
            }
        }

        if (callbackEnv) {
            jbyteArray byteArray = callbackEnv->NewByteArray(static_cast<jsize>(size));
            if (byteArray) {
                callbackEnv->SetByteArrayRegion(byteArray, 0, static_cast<jsize>(size),
                                                reinterpret_cast<const jbyte*>(data));
                callbackEnv->CallVoidMethod(gBridgeObjectGlobal, gPacketCallbackMethod, byteArray);
                callbackEnv->DeleteLocalRef(byteArray);
            }
        }

        if (attached) {
            gJavaVM->DetachCurrentThread();
        }
    });

    LOGI("AudioEngineBridge native initialized");
}

JNIEXPORT jboolean JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeStart(
    JNIEnv* /*env*/, jobject /*thiz*/, jint role, jint codec) {
    if (!gAudioEngine) return JNI_FALSE;
    return gAudioEngine->start(static_cast<RelayRole>(role), static_cast<CodecType>(codec)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeStop(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    if (gAudioEngine) {
        gAudioEngine->stop();
    }
}

JNIEXPORT void JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeSetRole(
    JNIEnv* /*env*/, jobject /*thiz*/, jint role) {
    if (gAudioEngine) {
        gAudioEngine->setRole(static_cast<RelayRole>(role));
    }
}

JNIEXPORT void JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeSetCodec(
    JNIEnv* /*env*/, jobject /*thiz*/, jint codec) {
    if (gAudioEngine) {
        gAudioEngine->setCodec(static_cast<CodecType>(codec));
    }
}

JNIEXPORT void JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeSetEffectPreset(
    JNIEnv* /*env*/, jobject /*thiz*/, jint preset) {
    if (gAudioEngine) {
        gAudioEngine->setEffectPreset(static_cast<EffectPreset>(preset));
    }
}

JNIEXPORT void JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeSetEffectIntensity(
    JNIEnv* /*env*/, jobject /*thiz*/, jfloat intensity) {
    if (gAudioEngine) {
        gAudioEngine->setEffectIntensity(intensity);
    }
}

JNIEXPORT void JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeSetMasterGain(
    JNIEnv* /*env*/, jobject /*thiz*/, jfloat gain) {
    if (gAudioEngine) {
        gAudioEngine->setMasterGain(gain);
    }
}

JNIEXPORT void JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeSetAutoTuneKey(
    JNIEnv* /*env*/, jobject /*thiz*/, jint rootKey) {
    if (gAudioEngine) {
        gAudioEngine->setAutoTuneKey(rootKey);
    }
}

JNIEXPORT void JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeSetAutoTuneScale(
    JNIEnv* /*env*/, jobject /*thiz*/, jint scale) {
    if (gAudioEngine) {
        gAudioEngine->setAutoTuneScale(scale);
    }
}

JNIEXPORT void JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeReceiveAudioPacket(
    JNIEnv* env, jobject /*thiz*/, jbyteArray data, jint size) {
    if (!gAudioEngine || !data || size <= 0) return;

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (bytes) {
        gAudioEngine->receiveAudioPacket(reinterpret_cast<const uint8_t*>(bytes), static_cast<size_t>(size));
        env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    }
}

JNIEXPORT jfloat JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeGetPeakLevel(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    return gAudioEngine ? gAudioEngine->getPeakLevel() : 0.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeGetRmsLevel(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    return gAudioEngine ? gAudioEngine->getRmsLevel() : 0.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeGetEstimatedLatencyMs(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    return gAudioEngine ? gAudioEngine->getEstimatedLatencyMs() : 0.0f;
}

JNIEXPORT void JNICALL
Java_com_voicerelay_app_audio_AudioEngineBridge_nativeDestroy(
    JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(gJniMutex);
    if (gAudioEngine) {
        gAudioEngine->stop();
        gAudioEngine.reset();
    }
    if (gBridgeObjectGlobal) {
        env->DeleteGlobalRef(gBridgeObjectGlobal);
        gBridgeObjectGlobal = nullptr;
    }
    gPacketCallbackMethod = nullptr;
}

} // extern "C"
