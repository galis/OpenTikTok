package com.galix.avcore.render.filters;

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

    GLTexture getOutputTexture();

    Map<String, Object> getConfig();

}
