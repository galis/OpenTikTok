#include <platform/SLPlatform.h>
#include "SLMessageQueue.h"
#include "SLMessage.h"

slutil::SLMessageQueue::SLMessageQueue() {
    mIsQuit = false;
    mIsQuitSafe = false;
    mQueueSize = 0;
    queue = nullptr;
    pthread_mutex_init(&mMutex, nullptr);
    pthread_cond_init(&mCond, nullptr);
}

slutil::SLMessageQueue::~SLMessageQueue() {
    SLLog("~SLMessageQueue");
    pthread_mutex_lock(&mMutex);
    while (queue) {
        SLMessage *cur = queue;
        queue = queue->next;
        delete cur;
    }
    pthread_mutex_unlock(&mMutex);
    pthread_cond_destroy(&mCond);
    pthread_mutex_destroy(&mMutex);
}

bool slutil::SLMessageQueue::enqueue(slutil::SLMessage *message) {
    pthread_mutex_lock(&mMutex);
    if (mIsQuit) {
        pthread_mutex_unlock(&mMutex);
        return false;
    }
    if (!queue) {
        queue = message;
    } else {
        SLMessage *find = queue;
        while (find->next) {
            if (message->time < find->time) {//根据时间排序
                break;
            }
            find = find->next;
        }
        message->next = find->next;
        find->next = message;
    }
    mQueueSize++;
    pthread_cond_broadcast(&mCond);//唤醒等待队列
    pthread_mutex_unlock(&mMutex);
    SLLog("enqueue");
    dump();
    return true;
}

//return nullptr代表消息队列已经退出
slutil::SLMessage *slutil::SLMessageQueue::dequeue() {
    pthread_mutex_lock(&mMutex);
    while (!queue && !mIsQuit) {
        SLLog("pthread_cond_wait");
        pthread_cond_wait(&mCond, &mMutex);
    }

    //如果消息队列不安全退出，就立即返回nullptr!
    if (mIsQuit && !mIsQuitSafe) {
        SLLog("mIsQuit && !mIsQuitSafe");
        pthread_mutex_unlock(&mMutex);
        return nullptr;
    }
    //如果消息队列安全退出，那么执行完队列的消息才退出!
    if (mIsQuit && mIsQuitSafe && !queue) {
        SLLog("mIsQuit && mIsQuitSafe && !queue");
        pthread_mutex_unlock(&mMutex);
        return nullptr;
    }

    //取队列头部消息返回
    SLMessage *cur = queue;
    queue = queue->next;
    mQueueSize--;
    pthread_mutex_unlock(&mMutex);
    SLLog("dequeue");
    dump();
    return cur;
}

bool slutil::SLMessageQueue::quit(bool isSafe) {
    pthread_mutex_lock(&mMutex);
    SLLog("quit set");
    mIsQuit = true;
    mIsQuitSafe = isSafe;
    pthread_cond_broadcast(&mCond);
    pthread_mutex_unlock(&mMutex);
    dump();
    return true;
}

void slutil::SLMessageQueue::dump() {
    if (isDebug()) {
        pthread_mutex_lock(&mMutex);
        char *p = mLogBuf;
        char *pEnd = p + sizeof(mLogBuf);
        p += snprintf(p, pEnd - p, "\t\nDump message queue start------>\n"
                                   "QueueSize:%d\t"
                                   "isQuit:%s\t"
                                   "isQuitSafe:%s\t\n",
                      mQueueSize,
                      mIsQuit ? "true" : "false",
                      mIsQuitSafe ? "true" : "false");
        auto cur = queue;
        int count = 0;
        while (cur) {
            p += snprintf(p, pEnd - p,
                          "Message%d\t"
                          "time:%ld\t"
                          "what:%d\t\n",
                          count,
                          cur->time,
                          cur->what);
            cur = cur->next;
            count++;
        }
        snprintf(p, pEnd - p, "Dump message queue end<------");
        SLLog("%s", mLogBuf);
        pthread_mutex_unlock(&mMutex);
    }
}
