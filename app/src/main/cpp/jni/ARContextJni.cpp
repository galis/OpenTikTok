
#include <jni.h>
#include "../core/ARContext.h"

extern "C"
JNIEXPORT jlong JNICALL
Java_com_galix_opentiktok_ARContext_nativeCreate(JNIEnv *env, jclass clazz) {
    slar::ARContext *context = new slar::ARContext;
    context->create();
    return reinterpret_cast<jlong>(context);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_galix_opentiktok_ARContext_nativeDraw(JNIEnv *env, jclass clazz, jlong native_ptr, jint surfaceId) {
    auto *arContext = reinterpret_cast<slar::ARContext *>( native_ptr);
    return arContext->draw(surfaceId);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_galix_opentiktok_ARContext_nativeDestroy(JNIEnv *env, jclass clazz, jlong native_ptr) {
    auto *arContext = reinterpret_cast<slar::ARContext *>(native_ptr);
    return arContext->destroy();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_galix_opentiktok_ARContext_nativeSetFace(JNIEnv *env, jclass clazz, jlong native_ptr, jfloat x, jfloat y, jfloat scale,
                                                  jfloat pitch, jfloat yaw,
                                                  jfloat roll) {
    auto *arContext = reinterpret_cast<slar::ARContext *>(native_ptr);
    arContext->setFace(x, y, scale, pitch, yaw, roll);
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_galix_opentiktok_ARContext_nativeSurfaceChanged(JNIEnv *env, jclass clazz, jlong native_ptr, jint width, jint height) {
    auto *arContext = reinterpret_cast<slar::ARContext *>(native_ptr);
    arContext->onSurfaceChanged(width, height);
    return 0;
}