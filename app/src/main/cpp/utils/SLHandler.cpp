#include <chrono>
#include <utility>
#include "SLHandler.h"
#include "SLLooper.h"
#include "SLMessage.h"
#include "SLMessageQueue.h"

slutil::SLHandler::SLHandler() {
    mLooper = SLLooper::myLooper();
}

slutil::SLHandler::SLHandler(std::shared_ptr<SLLooper> looper, std::function<void(SLMessage *)> callback) {
    mLooper = std::move(looper);
    mCallBack = std::move(callback);
}


slutil::SLHandler::~SLHandler() = default;

void slutil::SLHandler::postMessage(slutil::SLMessage *message) {
    if (!mLooper) return;
    mLooper->getMessageQueue()->enqueue(message);
}

void slutil::SLHandler::postMessage(int what) {
    if (!mLooper) return;
    auto message = SLMessage::obtainMessage();
    message->what = what;
    message->time = std::chrono::steady_clock::now().time_since_epoch().count();
    message->target = shared_from_this();
    mLooper->getMessageQueue()->enqueue(message);
}

void slutil::SLHandler::postMessageDelay(int what, long delay) {
    if (!mLooper) return;
    auto message = SLMessage::obtainMessage();
    message->what = what;
    message->time = std::chrono::steady_clock::now().time_since_epoch().count() + delay;
    message->target = shared_from_this();
    mLooper->getMessageQueue()->enqueue(message);
}

void slutil::SLHandler::handleMessage(slutil::SLMessage *message) {
    if (mCallBack) {
        mCallBack(message);
    }
}

void slutil::SLHandler::setCallback(std::function<void(SLMessage * )> callback) {
    mCallBack = std::move(callback);
}





