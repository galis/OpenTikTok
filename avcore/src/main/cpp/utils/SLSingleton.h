#ifndef SINGLETON_H
#define SINGLETON_H

#include <mutex>

namespace slutil {

    template<class T>
    class SLSingleton {
    private:
        static T *mInstance = nullptr;
        static std::mutex mMutex;
    public:
        static T *get();

        static void destroy();
    };
}
#endif
