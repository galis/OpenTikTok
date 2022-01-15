//
// Created by galismac on 23/8/2021.
//

#include <unistd.h>

#include <utility>
#include "SLHandlerThread.h"

namespace slutil {

    SLHandlerThread::SLHandlerThread(std::string name) {
        mIsStart = false;
        mName = std::move(name);
    }

    SLHandlerThread::~SLHandlerThread() {
        mIsStart = false;
    }

    void SLHandlerThread::start() {
        pthread_create(&mPThreadHandle, nullptr, main, this);
        pthread_setname_np(mPThreadHandle, "SLHandlerThread");
        mIsStart = true;
    }

    void SLHandlerThread::exit(bool isSafe) {
        if (getLooper()) {
            mLooper->quit(isSafe);
            pthread_join(mPThreadHandle, nullptr);
        }
    }

    std::shared_ptr<SLLooper> SLHandlerThread::getLooper() {
        if (mIsStart) {
            while (!mLooper) {
                usleep(1000);
            }
        }
        return mLooper;
    }

    std::shared_ptr<SLHandler> SLHandlerThread::getThreadHandler() {
        int tryCount = 200;
        while (!mLooper && tryCount) {
            usleep(5000);
            tryCount--;
        }
        if (!mHandler) {
            mHandler = std::make_shared<SLHandler>(mLooper, nullptr);
        }
        return mHandler;
    }

    void *SLHandlerThread::main(void *args) {
        if (!args) return nullptr;
        auto slHandlerThread = static_cast<SLHandlerThread *>(args);
        SLLooper::prepare();
        slHandlerThread->mLooper = SLLooper::myLooper();
        SLLooper::loop();
        return nullptr;
    }

    pthread_t SLHandlerThread::getPThreadId() {
        return mPThreadHandle;
    }
}
