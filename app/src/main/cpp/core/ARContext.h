//
// Created by galismac on 25/11/2021.
//

#ifndef FACEPPDEMO_ARCONTEXT_H
#define FACEPPDEMO_ARCONTEXT_H

#include <opencv2/opencv.hpp>
#include <map>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include "../utils/SLHandlerThread.h"
#include "../filters/Filter3D.h"
#include "../filters/FilterOES.h"
#include <../filters/FilterBase.h>

using namespace slutil;
using cv::Mat;
using std::map;
using std::string;

namespace slar {

    enum CONFIG_TYPE {
        EYELINE,
        EYELASH,
        EYESHADOW,
        LIPSTICK,
        SURFACE,
    };

    enum MSG_TYPE {
        CREATE,
        DRAW,
        CONFIG_SURFACE
    };

    struct ARConfig {
        string lipstickType;
        Mat lipstick;
        bool lipstickChange;

    };

    struct AREgl {
    };

    class ARContext {
    private:
        bool mState = false;
        std::unique_ptr<slfilter::Filter3D> mFilter;
        std::unique_ptr<slfilter::FilterOES> mCameraFilter;

        void createInternal();

        void drawInternal(int textureId);

        void configSurfaceInternal();

    public:
        ARContext();

        int onSurfaceChanged(int width,int height);

        int create();

        int draw(int textureId);

        int destroy();

        ~ARContext();

        void setFace(float x, float y,float scale, float pitch, float yaw, float roll);
    };
}


#endif //FACEPPDEMO_ARCONTEXT_H
