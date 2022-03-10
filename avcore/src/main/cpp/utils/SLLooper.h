#ifndef SL_LOOPER_H
#define SL_LOOPER_H

namespace slutil {

    class SLMessageQueue;

    class SLLooper {
    private:
        std::shared_ptr<SLMessageQueue> mMessageQueue;

    public:

        SLLooper();

        ~SLLooper();

        std::shared_ptr<SLMessageQueue> getMessageQueue();

        static std::shared_ptr<slutil::SLLooper> myLooper();

        static void prepare();

        static void loop();

        void quit(bool isSafe = false);
    };

    extern thread_local std::shared_ptr<slutil::SLLooper> gThreadLooper;

}

#endif
