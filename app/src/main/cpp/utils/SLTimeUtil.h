//
// Created by galismac on 25/8/2021.
//

#ifndef SL_TIME_UTIL_H
#define SL_TIME_UTIL_H

#include <cstdint>

using namespace std;

namespace slutil {
    class SLTimeUtil {
    public:
        static int64_t nowUs();

        static int64_t nowMs();

        static int64_t nowS();
    };
}


#endif
