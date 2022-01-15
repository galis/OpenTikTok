//
// Created by galismac on 6/12/2021.
//

#include "FilterOES.h"
#include "../utils/SLGLUtil.h"
#include <string>
#include <GLES2/gl2ext.h>

static const std::string VS = R"(
#version 300 es
layout(location = 0) in vec3 aVertCoord;
layout(location = 1) in vec2 aTextureCoord;
out vec2 vTextureCoord;
void main(){
    vTextureCoord = aTextureCoord;
    gl_Position = vec4(aVertCoord.xy,0.0,1.0);
}
)";

static const std::string FS = R"(
#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES sOesTexture;
in vec2 vTextureCoord;
out vec4 vFragColor;
void main(){
    vFragColor = texture(sOesTexture,vTextureCoord);
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
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId);
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                        GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                        GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                        GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                        GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        glFlush();
    }

    FilterOES::~FilterOES() {
        if (mVAO) glDeleteVertexArrays(1, &mVAO);
        if (mVBO) glDeleteVertexArrays(1, &mVBO);
        if (mEBO) glDeleteVertexArrays(1, &mEBO);
    }
}