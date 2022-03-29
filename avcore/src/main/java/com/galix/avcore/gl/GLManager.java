package com.galix.avcore.gl;

import android.content.Context;
import android.opengl.GLES30;
import android.util.Size;

import com.galix.avcore.util.IOUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static android.opengl.GLES30.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES30.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES30.GL_FRAMEBUFFER;
import static android.opengl.GLES30.GL_LINEAR;
import static android.opengl.GLES30.GL_RGBA;
import static android.opengl.GLES30.GL_TEXTURE_2D;
import static android.opengl.GLES30.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES30.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES30.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES30.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES30.GL_UNSIGNED_BYTE;
import static android.opengl.GLES30.glBindFramebuffer;
import static android.opengl.GLES30.glBindTexture;
import static android.opengl.GLES30.glDeleteFramebuffers;
import static android.opengl.GLES30.glDeleteTextures;
import static android.opengl.GLES30.glFramebufferTexture2D;
import static android.opengl.GLES30.glGenTextures;
import static android.opengl.GLES30.glTexImage2D;
import static android.opengl.GLES30.glTexParameterf;
import static android.opengl.GLES30.glTexParameteri;

/**
 * OpenGL数据管理
 *
 * @Author: Galis
 * @Date:2022.03.28
 */
public class GLManager {
    public static final boolean RESULT_FAILED = false;
    public static final boolean RESULT_OK = true;
    private Map<Object, IntBuffer> mFBO;
    private Map<Object, IntBuffer> mTextures;
    private static GLManager gManager;
    private WeakReference<Context> mContext;

    private GLManager() {
        mFBO = new HashMap<>();
    }

    public static GLManager getManager() {
        if (gManager == null) {
            synchronized (GLManager.class) {
                if (gManager == null) {
                    gManager = new GLManager();
                    return gManager;
                }
            }
        }
        return gManager;
    }


    public boolean generateFBO(Object tag, Size fboSize) {
        if (mFBO.containsKey(tag)) {
            return RESULT_OK;
        }
        IntBuffer fbo = IntBuffer.allocate(1);
        GLES30.glGenFramebuffers(fbo.limit(), fbo);
        if (!generateTexture(tag, fboSize)) {
            return RESULT_FAILED;
        }
        glBindFramebuffer(GL_FRAMEBUFFER, fbo.get());
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, getTexture(tag), 0);
        mFBO.put(tag, fbo);
        return RESULT_OK;
    }

    public int getFBO(Object tag) {
        if (mFBO.containsKey(tag)) {
            return mFBO.get(tag).get();
        }
        return 0;
    }

    public boolean hasFBO(Object tag) {
        return mFBO.containsKey(tag);
    }

    public boolean generateTexture(Object tag, Size textureSize) {
        if (mTextures.containsKey(tag)) {
            return RESULT_OK;
        }
        IntBuffer textureBuf = IntBuffer.allocate(1);
        glGenTextures(textureBuf.limit(), textureBuf);
        glBindTexture(GL_TEXTURE_2D, textureBuf.get());
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, textureSize.getWidth(), textureSize.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
        mFBO.put(tag, textureBuf);
        return RESULT_OK;
    }

    public int getTexture(Object tag) {
        if (mTextures.containsKey(tag)) {
            return mTextures.get(tag).get();
        }
        return 0;
    }

    public void release() {
        for (Object o : mTextures.keySet()) {
            IntBuffer textureBuf = mTextures.get(o);
            assert textureBuf != null;
            glDeleteTextures(textureBuf.limit(), textureBuf);
        }
        for (Object o : mFBO.keySet()) {
            IntBuffer fboBuf = mFBO.get(o);
            assert fboBuf != null;
            glDeleteFramebuffers(fboBuf.limit(), fboBuf);
        }
        mFBO.clear();
        mTextures.clear();
    }

    public void installContext(Context context) {
        mContext = new WeakReference<>(context);
    }

    public void unInstallContext() {
        mContext.clear();
    }

    public String loadGLSL(int glsl) {
        if (mContext == null || mContext.get() == null) {
            return null;
        }
        try {
            return IOUtils.readStr(mContext.get().getResources().openRawResource(glsl));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
