#ifndef SL_HANDLERE_H
#define SL_HANDLERE_H

#include <memory>
#include <functional>

namespace slutil {
    class SLLooper;

    class SLMessage;

    class SLHandler : public std::enable_shared_from_this<SLHandler> {
    private:
        std::shared_ptr<SLLooper> mLooper;
        std::function<void(SLMessage *message)> mCallBack;
    public:
        SLHandler();

        SLHandler(std::shared_ptr<SLLooper> looper, std::function<void(SLMessage *)> callback);

        SLHandler(const SLHandler &) = delete;

        SLHandler(const SLHandler &&) = delete;

        ~SLHandler();

        void postMessage(SLMessage *message);

        void postMessage(const int what);

        void postMessageDelay(const int what, const long delay);

        void setCallback(std::function<void(SLMessage *message)> callback);

        virtual void handleMessage(SLMessage *message);

    };

}
#endif


