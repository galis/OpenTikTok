#ifndef SL_MESSAGE_QUEUE_H
#define SL_MESSAGE_QUEUE_H

#include <pthread.h>

namespace slutil {
    class SLMessage;

    class SLMessageQueue {
    private:
        pthread_mutex_t mMutex;
        pthread_cond_t mCond;
        SLMessage *queue;   //消息队列
        bool mIsQuit;       //是否退出
        bool mIsQuitSafe;   //是否安全退出
        int mQueueSize;     //队列大小
        char mLogBuf[1000]; //日志缓冲区

    public:
        SLMessageQueue();

        ~SLMessageQueue();

        bool enqueue(SLMessage *message);

        SLMessage *dequeue();

        bool quit(bool isSafe = false);

        void dump();

    };
}

#endif

