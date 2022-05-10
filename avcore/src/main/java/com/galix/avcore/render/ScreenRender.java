package com.galix.avcore.render;

import android.util.Size;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.render.filters.TextureFilter;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glDeleteTextures;
import static android.opengl.GLES20.glViewport;

import java.util.HashMap;
import java.util.Map;

//渲染到屏幕
public class ScreenRender implements IVideoRender {
    private TextureFilter textureFilter;
    private Map<String, Object> configs = new HashMap<>();
    private Size mSurfaceSize = new Size(1920, 1080);

    @Override
    public GLTexture getOutTexture() {
        return null;
    }

    @Override
    public boolean isOpen() {
        return textureFilter != null;
    }

    @Override
    public void open() {
        if (isOpen()) return;
        textureFilter = new TextureFilter();
        textureFilter.open();
    }

    @Override
    public void close() {
        if (!isOpen()) return;
        textureFilter.close();
        textureFilter = null;
    }

    @Override
    public void write(Map<String, Object> config) {
        if (config.containsKey("surface_size")) {
            mSurfaceSize = (Size) config.get("surface_size");
        }
        textureFilter.write(config);
    }

    @Override
    public void render(AVFrame avFrame) {
        configs.clear();
        configs.put("use_fbo", false);
        if (avFrame.getTexture().isOes()) {
            configs.put("oesImageTexture", avFrame.getTexture());
        } else {
            configs.put("inputImageTexture", avFrame.getTexture());
        }
        configs.put("isOes", avFrame.getTexture().isOes());
        textureFilter.write(configs);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        textureFilter.render();
    }
}
