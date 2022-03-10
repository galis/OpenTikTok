#include "FilterBase.h"
#include "../utils/SLGLUtil.h"

using namespace slutil;

namespace slfilter {

    static auto DEFAULT_VSHADER = R"(
attribute vec4 position;
attribute vec4 inputTextureCoordinate;
varying vec2 textureCoordinate;
void main(){
   gl_Position = position;
   textureCoordinate = inputTextureCoordinate.xy;
}
)";

    static auto DEFAULT_FSHADER = R"(
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#else
#define highp
#define mediump
#define lowp
#endif
varying mediump vec2 textureCoordinate;
uniform sampler2D inputImageTexture;
void main(){
   gl_FragColor = texture2D(inputImageTexture, textureCoordinate);
}
)";

    static float DEFAULT_VERT_ARRAY[] = {
            -1.0f, 1.0f,
            1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, -1.0f,
    };

    static float TEXTURE_NO_ROTATION[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };

    static float TEXTURE_ROTATED_90[] = {
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            0.0f, 0.0f,
    };

    static float TEXTURE_ROTATED_180[] = {
            1.0f, 0.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f,
    };

    static float TEXTURE_ROTATED_270[] = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f,
    };

    FilterBase::FilterBase() : FilterBase(DEFAULT_VSHADER, DEFAULT_FSHADER) {
    }

    FilterBase::FilterBase(const string &vShader, const string &fShader) :
            mVertShader(vShader),
            mFragShader(fShader),
            mIsInit(false),
            mGLProgram(-1),
            mGLFrameBuf(-1),
            mOutputSize(-1, -1),
            mGLFrameBufSize(-1, -1),
            mGLColorTexture(-1),
            mActiveTexture(-1),
            mRenderMode(FRAMEBUFFER) {
    }

    FilterBase::~FilterBase() {
        destroy();
    }

    void FilterBase::init() {
        if (!mIsInit) {
            onInit();
            mIsInit = true;
            onInitialized();
        }
    }

    GLuint FilterBase::draw(GLuint textureId) {
        if (!mIsInit) return -1;
        if (mRenderMode == OUTPUT) {
            glViewport(0, 0, mOutputSize.width, mOutputSize.height);
        } else {
            glViewport(0, 0, mGLFrameBufSize.width, mGLFrameBufSize.height);
        }
        glUseProgram(mGLProgram);
        mActiveTexture = GL_TEXTURE0;
        //执行tasks
        {
            lock_guard<mutex> autoLock(mMutex);
            for (const auto &task: mTasks) {
                task();
            }
            mTasks.clear();
        }
        onDraw(textureId);
        return mGLColorTexture;
    }

    void FilterBase::onDraw(GLuint textureId) {
        glBindFramebuffer(GL_FRAMEBUFFER, mGLFrameBuf);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        bindTexture("inputImageTexture", textureId);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glFlush();
    }

    void FilterBase::destroy() {
        if (!mIsInit) return;
        onDestroy();
        mIsInit = false;
    }

    void FilterBase::onInit() {
        mGLProgram = SLGLUtil::loadProgram(mVertShader, mFragShader);
    }

    void FilterBase::onInitialized() {
    }

    void FilterBase::onDestroy() {
        glDeleteProgram(mGLProgram);
        glDeleteTextures(1, &mGLColorTexture);
        glDeleteBuffers(1, &mGLFrameBuf);
    }

    void FilterBase::onOutputChanged(int width, int height) {
        mOutputSize.width = width;
        mOutputSize.height = height;
    }

    void FilterBase::bindInt(string &&key, int value) {
        lock_guard<mutex> autoLock(mMutex);
        mTasks.emplace_back([&]() {
            if (!mLocations[key]) {
                mLocations[key] = glGetUniformLocation(mGLProgram, key.c_str());
            }
            glUniform1i(mLocations[key], value);
        });
    }

    void FilterBase::bindFloat(string &&key, float value) {
        lock_guard<mutex> autoLock(mMutex);
        mTasks.emplace_back([&]() {
            if (!mLocations[key]) {
                mLocations[key] = glGetUniformLocation(mGLProgram, key.c_str());
            }
            glUniform1f(mLocations[key], value);
        });
    }

    void FilterBase::bindPoint(string &&key, SLRect &point) {
        lock_guard<mutex> autoLock(mMutex);
        mTasks.emplace_back([&]() {
            if (!mLocations[key]) {
                mLocations[key] = glGetUniformLocation(mGLProgram, key.c_str());
            }
            float value[] = {point.x * 1.0f, point.y * 1.0f};
            glUniform1fv(mLocations[key], 2, value);
        });
    }

    void FilterBase::bindRect(string &&key, SLRect &rect) {
        lock_guard<mutex> autoLock(mMutex);
        mTasks.emplace_back([&]() {
            if (!mLocations[key]) {
                mLocations[key] = glGetUniformLocation(mGLProgram, key.c_str());
            }
            float value[] = {rect.x * 1.0f, rect.y * 1.0f, rect.width * 1.0f, rect.height * 1.0f};
            glUniform1fv(mLocations[key], 4, value);
        });
    }

    void FilterBase::bindFloatVec3(string &&key, float *floatValue) {
        lock_guard<mutex> autoLock(mMutex);
        mTasks.emplace_back([&]() {
            if (!mLocations[key]) {
                mLocations[key] = glGetUniformLocation(mGLProgram, key.c_str());
            }
            glUniform3fv(mLocations[key], 1, floatValue);
        });
    }

    void FilterBase::bindFloatArray(string &&key, float values[], int length) {
        lock_guard<mutex> autoLock(mMutex);
        mTasks.emplace_back([&]() {
            if (!mLocations[key]) {
                mLocations[key] = glGetUniformLocation(mGLProgram, key.c_str());
            }
            glUniform1fv(mLocations[key], length, values);
        });
    }

    void FilterBase::bindTMat(string &&key, SLMat &mat) {
        if (mat.empty()) {
            return;
        }
        if (!mLocations[key]) {
            mLocations[key] = glGetUniformLocation(mGLProgram, key.c_str());
        }
        glUniformMatrix3fv(mLocations[key], 1, false, reinterpret_cast<const GLfloat *>(mat.data));
    }

    void FilterBase::bindTexture(string &&key, GLuint texture) {
        auto func = [&]() {
            if (!mLocations[key]) {
                mLocations[key] = glGetUniformLocation(mGLProgram, key.c_str());
            }
            glUniform1i(mLocations[key], mActiveTexture - GL_TEXTURE0);
            mActiveTexture++;
        };
        func();
    }

    void FilterBase::bindTexture(string &&key, SLMat &mat) {

    }

    void FilterBase::bindAttr(string &&key, float values[], int lineWidth) {
        lock_guard<mutex> autoLock(mMutex);
        mTasks.emplace_back([&]() {
            if (!mLocations[key]) {
                mLocations[key] = glGetAttribLocation(mGLProgram, key.c_str());
            }
            glVertexAttribPointer(mLocations[key], lineWidth, GL_FLOAT, GL_FALSE, 0, values);
            glEnableVertexAttribArray(mLocations[key]);
        });
    }

    //Set接口

    void FilterBase::setRenderMode(RenderMode mode) {
        mTasks.emplace_back([&] {
            mRenderMode = mode;
        });
    }

    void FilterBase::setFrameBufferSize(const SLSize &size) {
        mTasks.emplace_back([=] {
            SLLog("setFrameBufferSize#%d,%d", size.width, size.height);
            if (mGLFrameBufSize != size) {
                mGLFrameBufSize = size;
                if (mGLFrameBuf == -1) {
                    glGenBuffers(1, &mGLFrameBuf);
                    glGenTextures(1, &mGLColorTexture);
                    glBindTexture(GL_TEXTURE_2D, mGLColorTexture);
                    SLGLUtil::useTexParameter();
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mGLFrameBufSize.width, mGLFrameBufSize.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
                    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mGLColorTexture, 0);
                }
            }
        });
    }


    //Get接口

    GLuint FilterBase::getFrameBuffer() {
        return mGLFrameBuf;
    }

    GLuint FilterBase::getFrameBufferColorTexture() {
        return mGLColorTexture;
    }

    SLSize &FilterBase::getOutputSize() {
        return mOutputSize;
    }

    SLSize &FilterBase::getFrameBufferSize() {
        return mGLFrameBufSize;
    }

    //调试
    void FilterBase::dump() {

    }

    void FilterBase::setTask(std::function<void(void)> task) {
        mTasks.emplace_back(task);
    }

    GLuint FilterBase::getProgram() {
        return mGLProgram;
    }

}

