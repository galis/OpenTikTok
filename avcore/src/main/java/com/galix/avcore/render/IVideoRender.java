package com.galix.avcore.render;

import com.galix.avcore.render.filters.GLTexture;

public interface IVideoRender extends IRender {
    public GLTexture getOutTexture();
}
