//
// Created by galismac on 6/12/2021.
//

#ifndef FACEPPDEMO_FILTEROES_H
#define FACEPPDEMO_FILTEROES_H

#include "FilterBase.h"

namespace slfilter {
    class FilterOES : public FilterBase {
    private:
        GLuint mVAO, mVBO, mEBO;
    public:
        FilterOES();

        void onDraw(GLuint textureId) override;

        ~FilterOES();
    };
}


#endif //FACEPPDEMO_FILTEROES_H
