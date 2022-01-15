#include "SLGLUtil.h"

#include <utility>

namespace slutil {

    GLuint SLGLUtil::loadProgram(string vShader, string fShader) {
        auto vShaderId = compileShader(std::move(vShader), GL_VERTEX_SHADER);
        auto fShaderId = compileShader(std::move(fShader), GL_FRAGMENT_SHADER);
        if (!vShaderId || !fShaderId) {
            SLLog("compileShader error!!");
            return 0;
        }
        auto program = glCreateProgram();
        glAttachShader(program, vShaderId);
        glAttachShader(program, fShaderId);
        glLinkProgram(program);
        GLint compileStatus;
        glGetProgramiv(program, GL_LINK_STATUS, &compileStatus);
        if (!compileStatus) {
            GLint infoLogLen = 0;
            glGetProgramiv(program, GL_INFO_LOG_LENGTH, &infoLogLen);
            if (infoLogLen) {
                GLchar infoLog[infoLogLen];
                glGetProgramInfoLog(program, infoLogLen, nullptr, infoLog);
                SLLog("SLGLUtil#Could not link program:\n%s\n", infoLog);
            }
            glDeleteProgram(program);
            glDeleteShader(vShaderId);
            glDeleteShader(fShaderId);
        }
        return program;
    }

    GLuint SLGLUtil::compileShader(string shader, GLenum type) {
        auto newShader = glCreateShader(type);
        if (!newShader) {
            SLLog("SLGLUtil::glCreateShader error!");
            return 0;
        }
        auto src = shader.c_str();
        glShaderSource(newShader, 1, &src, nullptr);
        glCompileShader(newShader);
        GLint compileStatus;
        glGetShaderiv(newShader, GL_COMPILE_STATUS, &compileStatus);
        if (!compileStatus) {
            auto infoLogLen = 0;
            glGetShaderiv(newShader, GL_INFO_LOG_LENGTH, &infoLogLen);
            if (infoLogLen > 0) {
                GLchar infoLog[infoLogLen];
                glGetShaderInfoLog(newShader, infoLogLen, nullptr, infoLog);
                SLLog("SLGLUtil#Could not compile %s shader:\n\t%s\n\t",
                      type == GL_VERTEX_SHADER ? "vertex" : "fragment",
                      infoLog);
            }
            glDeleteShader(newShader);
            return 0;
        }
        return newShader;
    }

    void SLGLUtil::useTexParameter() {
        //设置缩小过滤为使用纹理中坐标最接近的一个像素的颜色作为需要绘制的像素颜色..这里改为线性
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        //设置放大过滤为使用纹理中坐标最接近的若干个颜色，通过加权平均算法得到需要绘制的像素颜色
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        //设置环绕方向S，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        //设置环绕方向T，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    void SLGLUtil::checkGLError() {
        auto error = glGetError();
        string log;
        if (error != GL_NO_ERROR) {
            switch (error) {
                case GL_INVALID_ENUM:
                    log = "INVALID_ENUM";
                    break;
                case GL_INVALID_VALUE:
                    log = "INVALID_VALUE";
                    break;
                case GL_INVALID_OPERATION:
                    log = "INVALID_OPERATION";
                    break;
                case GL_OUT_OF_MEMORY:
                    log = "OUT_OF_MEMORY";
                    break;
                case GL_INVALID_FRAMEBUFFER_OPERATION:
                    log = "INVALID_FRAMEBUFFER_OPERATION";
                default:
                    log = "UNKNOWN ERROR";
                    break;
            }
            SLLog("CHECK_GL_ERROR:%s", log.c_str());
        }
    }
}