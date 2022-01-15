//
// Created by galismac on 25/11/2021.
//

#ifndef FACEPPDEMO_FILTER3D_H
#define FACEPPDEMO_FILTER3D_H

#include <string>
#include <glm/glm.hpp>
#include <glm/ext.hpp>
#include "FilterBase.h"

#include "ObjLoader.h"
//
using namespace objloader;

using std::string;
using cv::Point2f;
using glm::mat4x4;

typedef mat4x4 GLMat;

namespace slfilter {

    class Filter3D : public FilterBase {
    private:

        typedef struct Euler {
            float x, y, z;
        } Euler;
        typedef struct Sum {
            float x, y, z;
        };

        queue<Euler> mEuler;
        Sum mSum;

        Loader mLoader;
        GLMat mModel;
        GLMat mView;
        GLMat mProjection;
        GLMat mMvp;
        GLuint mVAO, mVBO, mVEO;
    public:
        Filter3D();

        int loadFile(string obj);

        int setData(Point2f &center, float scale, float pitch, float yaw, float roll);

        void onDraw(GLuint textureId) override;

        void onOutputChanged(int width, int height) override;

        ~Filter3D();
    };
}


#endif //FACEPPDEMO_FILTER3D_H
