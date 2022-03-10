//
// Created by galismac on 23/8/2021.
//

#ifndef SL_HANDLER_THREAD_H
#define SL_HANDLER_THREAD_H

#include <memory>
#include <pthread.h>
#include "SLLooper.h"
#include "SLMessageQueue.h"
#include "SLMessage.h"
#include "SLHandler.h"

namespace slutil {

    //像安卓HandlerThread一样使用
    class SLHandlerThread {
    private:
        bool mIsStart;
        std::string mName;
        pthread_t mPThreadHandle;
        std::shared_ptr<SLLooper> mLooper;
        std::shared_ptr<SLHandler> mHandler;

        static void *main(void *);//主入口
    public:

        SLHandlerThread(std::string name = "Default");

        ~SLHandlerThread();

        //启动消息线程
        void start();

        //关闭消息线程，以阻塞的方式.
        void exit(bool isSafe = false);

        std::shared_ptr<SLLooper> getLooper();

        std::shared_ptr<SLHandler> getThreadHandler();

        pthread_t getPThreadId();

    };
}

#endif
