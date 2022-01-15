//
// Created by galismac on 25/11/2021.
//

#include "Filter3D.h"
#include "../utils/SLGLUtil.h"
#include <glm/gtx/euler_angles.hpp>

static const string VS = R"(
#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aTexCoord;
uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
out vec2 vTexCoord;
out vec3 vColor;
out vec4 vPoint;
out vec3 vNormal;
void main(){
    vTexCoord = aTexCoord;
    vColor = aPosition;
    vPoint = projection * view * model*vec4(aPosition,1.0);
    vNormal = vec3(model*vec4(aNormal,0.0));
    gl_Position = vPoint;
}
)";

static const string FS = R"(
#version 300 es
precision mediump float;
in vec2 vTexCoord;
in vec3 vColor;
in vec4 vPoint;
in vec3 vNormal;
out vec4 vFragColor;
uniform vec4 testPoint;
void main(){
    float alpha = vPoint.z/vPoint.w<testPoint.z/testPoint.w ? 0.2:0.0;
    float diff = max(0.0,dot(vec3(0.0,0.0,1.0),normalize(vNormal)));
    float ambient = 0.2;
    vFragColor = vec4(vColor,alpha);
}
)";


slfilter::Filter3D::Filter3D() : FilterBase(VS, FS),
                                 mVAO(0), mVBO(0), mVEO(0), mSum({0, 0, 0}) {
    loadFile("/sdcard/obj3d/fbb.obj");
}

slfilter::Filter3D::~Filter3D() {
    if (mVAO) glDeleteVertexArrays(1, &mVAO);
    if (mVBO) glDeleteBuffers(1, &mVBO);
    if (mVEO) glDeleteBuffers(1, &mVEO);
    mVAO = 0;
    mVBO = 0;
    mVEO = 0;
}

int slfilter::Filter3D::loadFile(string obj) {
    if (!mLoader.LoadFile(obj)) {
        return -1;
    }
    setTask([&]() {
        if (!mVAO) {
            glGenVertexArrays(1, &mVAO);
            slutil::SLGLUtil::checkGLError();
        }
        if (!mVBO) {
            glGenBuffers(1, &mVBO);
            slutil::SLGLUtil::checkGLError();
        }
        if (!mVEO) {
            glGenBuffers(1, &mVEO);
            slutil::SLGLUtil::checkGLError();
        }
        glBindVertexArray(mVAO);
        slutil::SLGLUtil::checkGLError();
        glBindBuffer(GL_ARRAY_BUFFER, mVBO);
        slutil::SLGLUtil::checkGLError();
        glBufferData(GL_ARRAY_BUFFER, sizeof(objloader::Vertex) * mLoader.LoadedVertices.size(), mLoader.LoadedVertices.data(), GL_STATIC_DRAW);
        slutil::SLGLUtil::checkGLError();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mVEO);
        slutil::SLGLUtil::checkGLError();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(unsigned int) * mLoader.LoadedIndices.size(), mLoader.LoadedIndices.data(), GL_STATIC_DRAW);
        slutil::SLGLUtil::checkGLError();
        glVertexAttribPointer(0, 3, GL_FLOAT, false, sizeof(objloader::Vertex), 0);
        slutil::SLGLUtil::checkGLError();
        glEnableVertexAttribArray(0);
        slutil::SLGLUtil::checkGLError();
        glVertexAttribPointer(1, 3, GL_FLOAT, false, sizeof(objloader::Vertex), (GLvoid *) offsetof(objloader::Vertex, Normal));
        slutil::SLGLUtil::checkGLError();
        glEnableVertexAttribArray(1);
        slutil::SLGLUtil::checkGLError();
        glVertexAttribPointer(2, 2, GL_FLOAT, false, sizeof(objloader::Vertex), (GLvoid *) offsetof(objloader::Vertex, TextureCoordinate));
        slutil::SLGLUtil::checkGLError();
        glEnableVertexAttribArray(2);
        slutil::SLGLUtil::checkGLError();
        glBindVertexArray(0);
        slutil::SLGLUtil::checkGLError();

    });
    return 0;
}

//x y z translate
int slfilter::Filter3D::setData(Point2f &center, float scale, float pitch, float yaw, float roll) {

    static const int TOTAL_SIZE = 1;
    if (mEuler.size() == TOTAL_SIZE) {
        mSum.x -= mEuler.front().x;
        mSum.y -= mEuler.front().y;
        mSum.z -= mEuler.front().z;
        mEuler.pop();
    }
    mEuler.push({pitch, yaw, roll});
    mSum.x += mEuler.front().x;
    mSum.y += mEuler.front().y;
    mSum.z += mEuler.front().z;

    //必须知道，摄像头的图是横向且x flip的。所以face++得到的欧拉角必须转换方向。
    auto zMat = glm::eulerAngleZ(-mSum.z / mEuler.size());
    zMat = glm::rotate(zMat, glm::radians(-90.f), glm::vec3(0.0f, 0.0f, 1.0f));
    auto yMat = glm::eulerAngleY(-mSum.y / mEuler.size());
    auto xMat = glm::eulerAngleX(mSum.x / mEuler.size());
    auto tMat = yMat * xMat * zMat;

    GLMat iMat = glm::identity<GLMat>();
    glm::vec4 keyPoint(0.0547f, 0.5352f, 4.9674f, 1.0f);
    keyPoint = tMat * keyPoint;
    glm::vec4 faceKeyPoint(center.x * 30, center.y * 30 * 4 / 3, 0.f, 1.f);
    mModel =
//            mModel = glm::translate(iMat, glm::vec3(keyPoint.x - faceKeyPoint.x, keyPoint.y - faceKeyPoint.y, 0.f)) *
            tMat;
    return 0;
}

void slfilter::Filter3D::onDraw(GLuint textureId) {
    glViewport(0, 0, getFrameBufferSize().width, getFrameBufferSize().height);
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    slutil::SLGLUtil::checkGLError();
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindVertexArray(mVAO);
    slutil::SLGLUtil::checkGLError();
    glUniformMatrix4fv(glGetUniformLocation(getProgram(), "model"), 1, GL_FALSE, glm::value_ptr(mModel ));
    glUniformMatrix4fv(glGetUniformLocation(getProgram(), "view"), 1, GL_FALSE, glm::value_ptr(mView));
    glUniformMatrix4fv(glGetUniformLocation(getProgram(), "projection"), 1, GL_FALSE, glm::value_ptr(mProjection));
    glUniform4fv(glGetUniformLocation(getProgram(), "testPoint"), 1,
                 glm::value_ptr(mProjection * mView * mModel * glm::vec4(0, 0, 0, 1.0f)));
    glDrawElements(GL_TRIANGLES, mLoader.LoadedIndices.size(), GL_UNSIGNED_INT, 0);
    slutil::SLGLUtil::checkGLError();
    glBindVertexArray(0);
    slutil::SLGLUtil::checkGLError();
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glFlush();
    slutil::SLGLUtil::checkGLError();
}

void slfilter::Filter3D::onOutputChanged(int width, int height) {
    FilterBase::onOutputChanged(width, height);
    setTask([=]() {
        mModel = glm::identity<glm::mat4>();
        mView = glm::lookAt(glm::vec3(0.0f, 0.0f, 50.f),
                            glm::vec3(0.0f, 0.0f, 0.0f),
                            glm::vec3(0.0f, 1.0f, 0.0f));
        float sceneSize = 25.f;
        float ratio = width * 1.0f / height * sceneSize;
        mProjection = glm::perspective(45.f, 3.f / 4.f, 20.f, 100.f);
//        mProjection = glm::ortho(-ratio, ratio, -sceneSize, sceneSize, -0.1f, 1000.f);
//        mProjection = glm::frustum(-ratio, ratio, -sceneSize, sceneSize, -0.1f, 1000.f);
        SLLog("Filter3D::onOutputChanged#%d#%d", getOutputSize().width, getOutputSize().height);
    });
}


