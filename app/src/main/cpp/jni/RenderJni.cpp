//
// Created by galismac on 9/3/2022.
//

#include <jni.h>
#include "../filters/FilterOES.h"

extern "C"
JNIEXPORT jlong JNICALL
Java_com_galix_opentiktok_render_OESRender_nativeOpen(JNIEnv *env, jobject thiz) {
    auto *filter = new slfilter::FilterOES();
    filter->init();
    return reinterpret_cast<jlong>(filter);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_galix_opentiktok_render_OESRender_nativeWrite(JNIEnv *env, jobject thiz, jlong native_obj, jint surface_width, jint surface_height) {
    auto *filter = reinterpret_cast<slfilter::FilterOES *>(native_obj);
    if (filter) {
        filter->onOutputChanged(surface_width, surface_height);
        filter->setFrameBufferSize(SLSize(surface_width, surface_height));
    }
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_galix_opentiktok_render_OESRender_nativeRender(JNIEnv *env, jobject thiz, jlong native_obj, jint texture_id, jint texture_width,
                                                        jint texture_height,jint color) {
    auto *filter = reinterpret_cast<slfilter::FilterOES *>(native_obj);
    if (filter) {
        filter->setTextureInfo(SLSize(texture_width, texture_height));
        filter->setBgColor(color);
        filter->draw(texture_id);
    }
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_galix_opentiktok_render_OESRender_nativeClose(JNIEnv *env, jobject thiz, jlong native_obj) {
    auto *filter = reinterpret_cast<slfilter::FilterOES *>(native_obj);
    if (filter) {
        filter->destroy();
        delete filter;
    }
    return 0;
}
