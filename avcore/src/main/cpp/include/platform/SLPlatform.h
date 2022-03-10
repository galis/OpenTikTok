#ifndef SL_PLATFORM_H
#define SL_PLATFORM_H

/**
 * 调试开关
 */
#define DEBUG_ENABLE true

#define isDebug() \
    DEBUG_ENABLE

/**
 * 平台相关的配置
 */

#ifdef __ANDROID__

#include <android/log.h>
#include <opencv2/opencv.hpp>
#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>

#define SL_TAG "SLMEDIA"
#define SLLog(...) __android_log_print(ANDROID_LOG_ERROR,SL_TAG,__VA_ARGS__);
typedef cv::Mat SLMat;
typedef cv::Rect SLRect;
typedef cv::Rect2f SLRect2f;
typedef cv::Point SLPoint;
typedef cv::Point2f SLPoint2f;
typedef cv::Size SLSize;

#elif defined(__APPLE__)
#include <stdio.h>
#define SLLog(expr, ...) printf((expr),##__VA_ARGS__);

#else

#include <stdio.h>

#define SLLog(expr, ...) printf((expr),##__VA_ARGS__);
#endif


#endif
