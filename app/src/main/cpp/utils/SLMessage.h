#ifndef SL_MESSAGE_H
#define SL_MESSAGE_H

#include <mutex>

namespace slutil {

    class SLHandler;

    class SLMessage {
    public:
        SLMessage();

        ~SLMessage();

        int what;       //消息类型
        int arg0, arg1; //提供两个int参数
        long time;      //消息时间
        std::string str;//string参数
        SLMessage *next;//下个消息指针

        std::shared_ptr<SLHandler> target;

        static SLMessage *obtainMessage();

        static bool releaseAllMessage();

        static bool recycleMessage(SLMessage *message);

    };


}

#endif


