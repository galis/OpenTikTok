package com.galix.avcore.render.filters;

import android.util.Size;

import java.nio.IntBuffer;
import java.util.Map;

public interface IFilter {

    String USE_FBO = "use_fbo";

    String FBO_SIZE = "fbo_size";

    String INPUT_IMAGE = "inputImageTexture";

    String INPUT_IMAGE_OES = "inputImageOesTexture";

    boolean isOpen();

    void open();

    void close();

    void write(Map<String, Object> config);

    void write(Object... config);

    void render();

    void renderSimulate();

    IFilter useFBO(boolean isUse);

    IFilter setFBOSize(Size fboSize);

    int getFBO();

    IFilter setMRT(int n);

    IFilter setVAO(IntBuffer buffer);

    Size fboSize();

    IFilter clear(boolean isClear);

    IFilter bind(Object... kv);

    GLTexture getOutputTexture();

    GLTexture getOutputTexture(int idx);

    Map<String, Object> getConfig();

    IFilter setPreTask(Runnable task);

    IFilter setPostTask(Runnable task);

    IFilter customDraw(Runnable drawTask);

    void setParent(BaseFilterGroup baseFilterGroup);

    BaseFilterGroup getParent();
}
