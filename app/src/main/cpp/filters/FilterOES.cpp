//
// Created by galismac on 6/12/2021.
//

#include "FilterOES.h"
#include "../utils/SLGLUtil.h"
#include "../utils/SLMath.h"
#include <string>
#include <GLES2/gl2ext.h>

static const std::string VS = R"(#version 300 es
layout(location = 0) in vec3 aVertCoord;
layout(location = 1) in vec2 aTextureCoord;
out vec2 vTextureCoord;
void main(){
    vTextureCoord = aTextureCoord;
    gl_Position = vec4(aVertCoord.xy,0.0,1.0);
}
)";

static const std::string FS = R"(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 vTextureCoord;
out vec4 vFragColor;
uniform samplerExternalOES sOesTexture;
uniform mat3 inputMat;
uniform vec3 bgColor;


void main(){
    vec3 targetCoord = inputMat*vec3(vTextureCoord,1.0);
   if(targetCoord.x < 0.0||targetCoord.x>1.0||targetCoord.y<0.0||targetCoord.y>1.0) {
        vFragColor = vec4(bgColor,1.0);
   }else{
        vFragColor = texture(sOesTexture,targetCoord.xy);
   }
}
)";

//解码器纹理上下翻转？
static float DEFAULT_VERT_ARRAY_CODEC[] = {
        -1.0f, 1.0f, 0.0f, 0.0f, 0.0f,
        1.0f, 1.0f, 0.0f, 1.0f, 0.0f,
        -1.0f, -1.0f, 0.0f, 0.0f, 1.0f,
        1.0f, -1.0f, 0.0f, 1.0f, 1.0f
};

static unsigned int DRAW_ORDER[]{
        0, 1, 2, 1, 2, 3
};

namespace slfilter {

    FilterOES::FilterOES() : FilterBase(VS, FS) {
        setTask([this]() {
            glGenVertexArrays(1, &mVAO);
            slutil::SLGLUtil::checkGLError();
            glBindVertexArray(mVAO);
            slutil::SLGLUtil::checkGLError();
            glGenBuffers(1, &mVBO);
            slutil::SLGLUtil::checkGLError();
            glGenBuffers(1, &mEBO);
            slutil::SLGLUtil::checkGLError();
            glBindBuffer(GL_ARRAY_BUFFER, mVBO);
            slutil::SLGLUtil::checkGLError();
            glBufferData(GL_ARRAY_BUFFER, sizeof(float) * 20, DEFAULT_VERT_ARRAY_CODEC, GL_STATIC_DRAW);
            slutil::SLGLUtil::checkGLError();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mEBO);
            slutil::SLGLUtil::checkGLError();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(unsigned int) * 6, DRAW_ORDER, GL_STATIC_DRAW);
            slutil::SLGLUtil::checkGLError();
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * sizeof(float), 0);
            slutil::SLGLUtil::checkGLError();
            glEnableVertexAttribArray(0);
            slutil::SLGLUtil::checkGLError();
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * sizeof(float), (void *) (3 * sizeof(float)));
            slutil::SLGLUtil::checkGLError();
            glEnableVertexAttribArray(1);
            slutil::SLGLUtil::checkGLError();
            glBindVertexArray(0);
            slutil::SLGLUtil::checkGLError();
        });
    }

    void FilterOES::onDraw(GLuint textureId) {
        glViewport(0, 0, getFrameBufferSize().width, getFrameBufferSize().height);
        glBindVertexArray(mVAO);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glClearColor(1.0, 1.0, 1.0, 1.0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId);
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                        GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                        GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                        GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                        GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        SLPoint2f srcPoints[]{SLPoint2f(0, 0), SLPoint2f(getFrameBufferSize().width, 0),
                              SLPoint2f(getFrameBufferSize().width / 2, getFrameBufferSize().height / 2)};
        int targetHeight = getFrameBufferSize().width * (mTextureSize.height * 1.0f / mTextureSize.width);
        int targetWidth = getFrameBufferSize().height * (mTextureSize.width * 1.0f / mTextureSize.height);
        if (targetHeight <= getFrameBufferSize().height) {//宽度铺满
            int deltaHeight = (getFrameBufferSize().height - targetHeight) / 2;
            SLPoint2f dstPoints[]{SLPoint2f(0, deltaHeight), SLPoint2f(getFrameBufferSize().width, deltaHeight),
                                  SLPoint2f(getFrameBufferSize().width / 2, getFrameBufferSize().height / 2)};
            SLMat mat = slutil::SLMath::getTransformMat(srcPoints, dstPoints, getFrameBufferSize(), getFrameBufferSize());
            bindTMat("inputMat", mat);
        } else {
            int deltaWidth = (getFrameBufferSize().width - targetWidth) / 2;
            SLPoint2f dstPoints[]{SLPoint2f(deltaWidth, 0), SLPoint2f(getFrameBufferSize().width - deltaWidth, 0),
                                  SLPoint2f(getFrameBufferSize().width / 2, getFrameBufferSize().height / 2)};
            SLMat mat = slutil::SLMath::getTransformMat(srcPoints, dstPoints, getFrameBufferSize(), getFrameBufferSize());
            bindTMat("inputMat", mat);
        }
        glUniform1i(glGetUniformLocation(getProgram(), "sOesTexture"), 0);
        glUniform3fv(glGetUniformLocation(getProgram(), "bgColor"), 1, mColor);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        glFlush();
    }

    FilterOES::~FilterOES() {
        if (mVAO) glDeleteVertexArrays(1, &mVAO);
        if (mVBO) glDeleteVertexArrays(1, &mVBO);
        if (mEBO) glDeleteVertexArrays(1, &mEBO);
    }

    void FilterOES::setTextureInfo(SLSize size) {
        mTextureSize = size;
    }

    void FilterOES::setBgColor(int color) {
        slutil::SLMath::getNormalColor(color, mColor);
//        bindFloatVec3("bgColor", mColor);
    }
}