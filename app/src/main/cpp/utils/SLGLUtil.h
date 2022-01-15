#ifndef SL_GL_UTIL_H
#define SL_GL_UTIL_H

#include <platform/SLPlatform.h>
#include <string>

using namespace std;

namespace slutil{

    class SLGLUtil {
    public:
        static GLuint loadProgram(string vShader, string fShader);

        static GLuint compileShader(string shader, GLenum type);

        static void useTexParameter();

        static void checkGLError();
    };
}


#endif
