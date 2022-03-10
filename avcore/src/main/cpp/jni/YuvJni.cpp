#include <jni.h>
#include <libyuv/scale.h>
#include <libyuv/convert.h>
#include <opencv2/opencv.hpp>
#include <map>


using cv::Mat;
using cv::Size;
using std::map;

#define COLOR_FormatYUV420PackedPlanar 20
map<int, long> memCache;//内存缓存。。

extern "C"
JNIEXPORT void JNICALL
Java_com_galix_avcore_util_YuvUtils_scaleAndSaveYuvAsJPEG(JNIEnv *env, jclass clazz, jobject yuv_buffer, jint format,
                                                              jint src_w, jint src_h, jint dst_w, jint dst_h, jstring path) {
    //I420
    //yyyyyyyyvvvvuuuu
    if (format == COLOR_FormatYUV420PackedPlanar) {
        const char *str = env->GetStringUTFChars(path, nullptr);
        Mat result(Size(dst_w, dst_h), CV_8UC3);
        uint8_t *yuvBuffer = static_cast<uint8_t *>(env->GetDirectBufferAddress(yuv_buffer));
        long yuvBufferSize = src_w * src_h * 3 / 2;
        if (memCache.count(yuvBufferSize) == 0) {
            char *newYuvBuffer = static_cast<char *>(malloc(dst_w * dst_h * 3 / 2));
            if (!newYuvBuffer) {
                return;
            }
            memCache[yuvBufferSize] = reinterpret_cast<long>(newYuvBuffer);
        }
        uint8_t *dstBuffer = reinterpret_cast<uint8_t *>(memCache[yuvBufferSize]);
        libyuv::I420Scale(yuvBuffer, src_w, yuvBuffer + src_w * src_h, src_w / 2, yuvBuffer + src_w * src_h * 5 / 4, src_w / 2, src_w, src_h,
                          dstBuffer, dst_w, dstBuffer + dst_w * dst_h, dst_w / 2, dstBuffer + dst_w * dst_h * 5 / 4, dst_w / 2, dst_w, dst_h,
                          libyuv::FilterMode::kFilterLinear);
        Mat mat(Size(dst_w, dst_h * 3 / 2), CV_8UC1, dstBuffer);
        Mat resultMat;
        cv::cvtColor(mat, resultMat, CV_YUV2BGR_I420);
        cv::imwrite(str, resultMat);
//    env->ReleaseStringUTFChars(path, str);
    } else {
        //TODO
    }
}