//
// Created by galismac on 6/12/2021.
//

#ifndef FACEPPDEMO_FILTEROES_H
#define FACEPPDEMO_FILTEROES_H

#include "FilterBase.h"

namespace slfilter {
    class FilterOES : public FilterBase {
    private:
        GLuint mVAO = -1, mVBO = -1, mEBO = -1;
        SLSize mTextureSize;
        float mColor[3];
    public:
        FilterOES();

        void onDraw(GLuint textureId) override;

        void setTextureInfo(SLSize size);

        void setBgColor(int color);

        ~FilterOES();
    };
}


#endif //FACEPPDEMO_FILTEROES_H
