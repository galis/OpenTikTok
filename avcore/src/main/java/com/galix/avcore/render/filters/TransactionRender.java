package com.galix.avcore.render.filters;

import android.opengl.GLES30;
import android.util.Log;
import android.util.Size;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.render.IRender;
import com.galix.avcore.util.GLUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES30.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES30.GL_FRAMEBUFFER;
import static android.opengl.GLES30.GL_LINEAR;
import static android.opengl.GLES30.GL_TEXTURE0;
import static android.opengl.GLES30.GL_TEXTURE_2D;
import static android.opengl.GLES30.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES30.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES30.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES30.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES30.GL_TRIANGLES;
import static android.opengl.GLES30.GL_UNSIGNED_INT;
import static android.opengl.GLES30.glActiveTexture;
import static android.opengl.GLES30.glBindFramebuffer;
import static android.opengl.GLES30.glBindTexture;
import static android.opengl.GLES30.glDrawElements;
import static android.opengl.GLES30.glFlush;
import static android.opengl.GLES30.glGetUniformLocation;
import static android.opengl.GLES30.glTexParameterf;
import static android.opengl.GLES30.glTexParameteri;
import static android.opengl.GLES30.glUniform1f;
import static android.opengl.GLES30.glUniform1i;
import static android.opengl.GLES30.glViewport;
import static android.opengl.GLES30.GL_ARRAY_BUFFER;
import static android.opengl.GLES30.GL_ELEMENT_ARRAY_BUFFER;
import static android.opengl.GLES30.GL_FLOAT;
import static android.opengl.GLES30.GL_FRAGMENT_SHADER;
import static android.opengl.GLES30.GL_STATIC_DRAW;
import static android.opengl.GLES30.glBindBuffer;
import static android.opengl.GLES30.glBindVertexArray;
import static android.opengl.GLES30.glBufferData;
import static android.opengl.GLES30.glEnableVertexAttribArray;
import static android.opengl.GLES30.glGenBuffers;
import static android.opengl.GLES30.glGenVertexArrays;
import static android.opengl.GLES30.glUseProgram;
import static android.opengl.GLES30.glVertexAttribPointer;

/**
 * 转场渲染器
 *
 * @Author: Galis
 * @Date:2022.03.22
 */
public class TransactionRender implements IRender {

    private static final String TAG = TransactionConfig.class.getSimpleName();
    //解码器纹理上下翻转？
    private static float[] DEFAULT_VERT_ARRAY_CODEC = {
            -1.0f, 1.0f, 0.0f, 0.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, -1.0f, 0.0f, 1.0f, 1.0f
    };

    private static int[] DRAW_ORDER = {
            0, 1, 2, 1, 2, 3
    };

    public static class TransactionConfig {
        public Size surfaceSize;
        public String vs;
        public String fs;
        public float interplator;//插值？
    }

    private TransactionConfig mConfig;
    private int mProgram = -1;
    private IntBuffer mVAO, mVBO, mEBO;
    private List<Runnable> mGLRunnables = new LinkedList<>();

    @Override
    public boolean isOpen() {
        return mProgram != -1;
    }

    @Override
    public void open() {
        if (isOpen()) return;
        if (mConfig == null) return;
        mProgram = GLUtil.createAndLinkProgram(GLUtil.loadShader(GLES30.GL_VERTEX_SHADER, mConfig.vs),
                GLUtil.loadShader(GL_FRAGMENT_SHADER, mConfig.fs), null);
        mVAO = IntBuffer.allocate(1);
        mVBO = IntBuffer.allocate(1);
        mEBO = IntBuffer.allocate(1);
        glGenVertexArrays(1, mVAO);
        glGenBuffers(1, mVBO);
        glGenBuffers(1, mEBO);
        glBindVertexArray(mVAO.get(0));
        glBindBuffer(GL_ARRAY_BUFFER, mVBO.get(0));
        glBufferData(GL_ARRAY_BUFFER, 4 * 20, FloatBuffer.wrap(DEFAULT_VERT_ARRAY_CODEC), GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mEBO.get(0));
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, 24, IntBuffer.wrap(DRAW_ORDER), GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * 4, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * 4, 3 * 4);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        if (!isOpen()) return;
        GLES30.glDeleteProgram(mProgram);
        GLES30.glDeleteVertexArrays(1, mVAO);
        GLES30.glDeleteBuffers(1, mVBO);
        GLES30.glDeleteBuffers(1, mEBO);
        mProgram = -1;
        mVAO = null;
        mVBO = null;
        mEBO = null;
    }

    @Override
    public void write(Map<String, Object> config) {
        if (!(config instanceof TransactionConfig)) {
            Log.d(TAG, "无法识别的配置");
            return;
        }
        this.mConfig = (TransactionConfig) config;
    }

    @Override
    public void render(AVFrame avFrame) {
        if (!isOpen()) return;
        avFrame.getSurfaceTexture().updateTexImage();
        avFrame.getSurfaceTextureExt().updateTexImage();
        resetTexture();

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glUseProgram(mProgram);
        glViewport(0, 0, mConfig.surfaceSize.getWidth(), mConfig.surfaceSize.getHeight());
        bindTexture("video0", avFrame.getTexture(), true);
        bindTexture("video1", avFrame.getTextureExt(), true);
        bindFloat("alpha", avFrame.getDelta());
        Log.d(TAG, "avFrame.getDelta()#" + avFrame.getDelta());
        glBindVertexArray(mVAO.get(0));
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        glFlush();
    }

    private int mActiveTexture = GL_TEXTURE0;

    private void resetTexture() {
        mActiveTexture = GL_TEXTURE0;
    }

    private void bindTexture(String str, int textureId, boolean oes) {
        glActiveTexture(mActiveTexture);
        int type = oes ? GL_TEXTURE_EXTERNAL_OES : GL_TEXTURE_2D;
        glBindTexture(type, textureId);
        glTexParameterf(type, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameterf(type, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(type, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(type, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glUniform1i(glGetUniformLocation(mProgram, str), mActiveTexture - GL_TEXTURE0);
        mActiveTexture++;
    }

    private void bindFloat(String str, float alpha) {
        glUniform1f(glGetUniformLocation(mProgram, str), alpha);
    }
}
