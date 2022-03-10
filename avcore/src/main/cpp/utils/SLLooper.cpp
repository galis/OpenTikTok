#include <memory>
#include <platform/SLPlatform.h>
#include "SLLooper.h"
#include "SLHandler.h"
#include "SLMessageQueue.h"
#include "SLMessage.h"

thread_local std::shared_ptr<slutil::SLLooper> slutil::gThreadLooper;

std::shared_ptr<slutil::SLLooper> slutil::SLLooper::myLooper() {
    if (!gThreadLooper) {
        SLLog("slutil::SLLooper::myLooper in a no init thread!!");
    }
    return gThreadLooper;
}

void slutil::SLLooper::prepare() {
    if (!gThreadLooper) {
        gThreadLooper = std::make_shared<SLLooper>();
        SLLog("prepare make_shared!");
    }
    SLLog("prepare ok!");
}

void slutil::SLLooper::loop() {
    auto looper = myLooper();
    if (looper) {
        for (;;) {
            SLMessage *message = looper->getMessageQueue()->dequeue();
            if (message) {
                SLLog("loop() hanldeMessage!");
                message->target->handleMessage(message);
            } else {
                SLLog("loop() get empty message!");
                return;
            }
        }
    } else {
        SLLog("Please invoke SLLooper::prepare int new thread");
    }
}

std::shared_ptr<slutil::SLMessageQueue> slutil::SLLooper::getMessageQueue() {
    return mMessageQueue;
}

slutil::SLLooper::SLLooper() {
    mMessageQueue = std::make_shared<SLMessageQueue>();
}

slutil::SLLooper::~SLLooper() {

}

void slutil::SLLooper::quit(bool isSafe) {
    mMessageQueue->quit(isSafe);
}
