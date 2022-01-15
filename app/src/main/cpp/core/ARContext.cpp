//
// Created by galismac on 25/11/2021.
//

#include "ARContext.h"
#include <../filters/Filter3D.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>

int slar::ARContext::create() {
    if (mState) {
        return 0;
    }
    mFilter.reset(new slfilter::Filter3D);
    mFilter->init();
    mCameraFilter.reset(new slfilter::FilterOES);
    mCameraFilter->init();
    return 0;
}

int slar::ARContext::draw(int textureId) {
    drawInternal(textureId);
    return 0;
}

int slar::ARContext::destroy() {
    return 0;
}

void slar::ARContext::createInternal() {
}

void slar::ARContext::drawInternal(int textureId) {
    mCameraFilter->draw(textureId);
}

void slar::ARContext::configSurfaceInternal() {
}

slar::ARContext::ARContext() {

}

slar::ARContext::~ARContext() {

}

void slar::ARContext::setFace(float x, float y, float scale, float pitch, float yaw, float roll) {
    Point2f center(x, y);
    mFilter->setData(center, scale, pitch, yaw, roll);
}

int slar::ARContext::onSurfaceChanged(int width, int height) {
    mFilter->onOutputChanged(width, height);
    mCameraFilter->onOutputChanged(width, height);
    mFilter->setFrameBufferSize(SLSize(width, height));
    mCameraFilter->setFrameBufferSize(SLSize(width, height));
    return 0;
}

