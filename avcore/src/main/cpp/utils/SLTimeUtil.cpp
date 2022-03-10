//
// Created by galismac on 25/8/2021.
//

#include <chrono>
#include "SLTimeUtil.h"

int64_t slutil::SLTimeUtil::nowUs() {
    return std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

int64_t slutil::SLTimeUtil::nowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

int64_t slutil::SLTimeUtil::nowS() {
    return std::chrono::duration_cast<std::chrono::seconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}
