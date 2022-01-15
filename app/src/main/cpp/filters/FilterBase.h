#ifndef SL_FILTER_BASE_H
#define SL_FILTER_BASE_H

#include <platform/SLPlatform.h>
#include <string>
#include <map>

using namespace std;

namespace slfilter {

    //滤镜基础类
    //也可以作为一个默认滤镜
    //初始化必须调用init
    //销毁时调用destroy
    enum RenderMode {
        OUTPUT,
        FRAMEBUFFER
    };

    class FilterBase {
    private:
        bool mIsInit;
        string mVertShader;
        string mFragShader;
        map<string, GLuint> mLocations;
        map<string, GLuint> mUniforms;
        mutex mMutex;
        RenderMode mRenderMode;
        vector<function<void(void)>> mTasks;
        GLuint mGLProgram;
        GLuint mGLFrameBuf;
        GLuint mGLColorTexture;
        GLuint mActiveTexture;
        SLSize mOutputSize;
        SLSize mGLFrameBufSize;
    public:
        FilterBase();

        explicit FilterBase(const string &vShader, const string &fShader);

        FilterBase(const FilterBase &filter) = delete;

        FilterBase(FilterBase &&filter) noexcept = delete;

        FilterBase &operator=(const FilterBase &filterBase) = delete;

        FilterBase &operator=(FilterBase &&filterBase) noexcept = delete;

        virtual ~FilterBase();

        //生命周期

        void init();

        GLuint draw(GLuint textureId);

        void destroy();

        virtual void onInit();

        virtual void onInitialized();

        virtual void onDraw(GLuint textureId);

        virtual void onDestroy();

        virtual void onOutputChanged(int width, int height);

        //绑定接口

        //attr
        void bindAttr(string &&key, float values[], int lineWidth = 2);

        //uniform

        void bindTexture(string &&key, GLuint texture);

        void bindTexture(string &&key, SLMat &mat);

        void bindRect(string &&key, SLRect &rect);

        void bindPoint(string &&key, SLRect &point);

        void bindInt(string &&key, int value);//绑定Int值

        void bindFloat(string &&key, float value);//绑定Float值

        void bindTMat(string &&key, SLMat &mat);//绑定变换矩阵 for opencv

        void bindFloatArray(string &&key, float values[], int length);

        void setTask(std::function<void(void)> task);

        //调试接口

        virtual void dump();

        //其他Set接口

        void setRenderMode(RenderMode mode);

        void setFrameBufferSize(const SLSize &size);

        //Get接口

        GLuint getFrameBuffer();

        GLuint getProgram();

        GLuint getFrameBufferColorTexture();

        SLSize &getOutputSize();

        SLSize &getFrameBufferSize();

    };

}
#endif
