//
// Created by galismac on 31/8/2021.
//

#include "SLSingleton.h"

template<class T>
T *slutil::SLSingleton<T>::get() {
    if (!mInstance) {
        std::lock_guard<std::mutex> autoLock(mMutex);
        if (!mInstance) {
            mInstance = new T;
        }
    }
    return mInstance;
}

template<class T>
void slutil::SLSingleton<T>::destroy() {
    if (mInstance) {
        std::lock_guard<std::mutex> autoLock(mMutex);
        if (mInstance) {
            delete mInstance;
        }
    }
}