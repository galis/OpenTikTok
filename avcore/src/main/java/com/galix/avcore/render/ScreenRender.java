package com.galix.avcore.render;

import android.util.Size;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.render.filters.ScreenFilter;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_LUMINANCE;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_UNPACK_ALIGNMENT;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glDeleteTextures;
import static android.opengl.GLES20.glTexParameterf;
import static android.opengl.GLES20.glTexParameteri;
import static android.opengl.GLES20.glViewport;

import java.util.HashMap;
import java.util.Map;

//渲染到屏幕
public class ScreenRender implements IVideoRender {
    private ScreenFilter screenFilter;
    private Map<String, Object> configs = new HashMap<>();
    private Size mSurfaceSize = new Size(1920, 1080);

    @Override
    public GLTexture getOutTexture() {
        return null;
    }

    @Override
    public boolean isOpen() {
        return screenFilter != null;
    }

    @Override
    public void open() {
        if (isOpen()) return;
        screenFilter = new ScreenFilter();
        screenFilter.open();
    }

    @Override
    public void close() {
        if (!isOpen()) return;
        screenFilter.close();
        screenFilter = null;
    }

    @Override
    public void write(Map<String, Object> config) {
        if (config.containsKey("surface_size")) {
            mSurfaceSize = (Size) config.get("surface_size");
        }
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
        screenFilter.write(configs);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, mSurfaceSize.getWidth(), mSurfaceSize.getHeight());
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        screenFilter.render();
    }
}
