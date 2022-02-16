//
// Created by galismac on 10/2/2022.
//
#include <string>
#include "FilterTransactionAlpha.h"
#include "../utils/SLGLUtil.h"


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
precision mediump float;
uniform sampler texture1;
uniform sampler texture2;
uniform float alpha;
in vec2 vTextureCoord;
out vec4 vFragColor;
void main(){
    vFragColor = mix(texture(texture1,vTextureCoord),texture(texture2,vTextureCoord),alpha);
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

slfilter::FilterTransactionAlpha::FilterTransactionAlpha() {
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

void slfilter::FilterTransactionAlpha::onDraw(GLuint textureId) {
    glViewport(0, 0, getFrameBufferSize().width, getFrameBufferSize().height);
    glBindVertexArray(mVAO);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    bindTexture("texture1", mTexture1);
    bindTexture("texture2", mTexture2);
    bindFloat("alpha", mAlpha);
    glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    glBindVertexArray(0);
    glFlush();
}

slfilter::FilterTransactionAlpha::~FilterTransactionAlpha() {
    if (mVAO) glDeleteVertexArrays(1, &mVAO);
    if (mVBO) glDeleteVertexArrays(1, &mVBO);
    if (mEBO) glDeleteVertexArrays(1, &mEBO);
}
