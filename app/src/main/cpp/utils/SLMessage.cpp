#include "SLMessage.h"

namespace slutil {

    static SLMessage *gPool;

    static std::mutex gPoolSync;

    SLMessage *SLMessage::obtainMessage() {
        std::lock_guard<std::mutex> dataLock(gPoolSync);
        if (gPool) {
            auto cur = gPool;
            cur->time = -1;
            cur->what = -1;
            cur->arg0 = cur->arg1 = -1;
            cur->str = nullptr;
            cur->next = nullptr;
            gPool = gPool->next;
            return cur;
        } else {
            return new SLMessage();
        }
    }

    bool SLMessage::recycleMessage(SLMessage *message) {
        std::lock_guard<std::mutex> dataLock(gPoolSync);
        if (!message) return false;
        if (gPool) {
            message->next = gPool;
        }
        gPool = message;
        return true;
    }

    bool SLMessage::releaseAllMessage() {
        std::lock_guard<std::mutex> dataLock(gPoolSync);
        while (gPool) {
            delete gPool;
            gPool = gPool->next;
        }
        return true;
    }

    SLMessage::SLMessage() {
        time = what = -1;
        next = nullptr;
        gPool = nullptr;
    }

    SLMessage::~SLMessage() {

    }
}





