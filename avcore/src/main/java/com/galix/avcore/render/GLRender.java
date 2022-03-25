package com.galix.avcore.render;

import android.opengl.GLES30;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.util.GLUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.LinkedList;
import java.util.List;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES30.GL_ARRAY_BUFFER;
import static android.opengl.GLES30.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES30.GL_ELEMENT_ARRAY_BUFFER;
import static android.opengl.GLES30.GL_FLOAT;
import static android.opengl.GLES30.GL_LINEAR;
import static android.opengl.GLES30.GL_STATIC_DRAW;
import static android.opengl.GLES30.GL_TEXTURE0;
import static android.opengl.GLES30.GL_TEXTURE_2D;
import static android.opengl.GLES30.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES30.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES30.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES30.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES30.GL_TRIANGLES;
import static android.opengl.GLES30.GL_UNSIGNED_INT;
import static android.opengl.GLES30.glActiveTexture;
import static android.opengl.GLES30.glBindBuffer;
import static android.opengl.GLES30.glBindTexture;
import static android.opengl.GLES30.glBindVertexArray;
import static android.opengl.GLES30.glBufferData;
import static android.opengl.GLES30.glDrawElements;
import static android.opengl.GLES30.glEnableVertexAttribArray;
import static android.opengl.GLES30.glFlush;
import static android.opengl.GLES30.glGenBuffers;
import static android.opengl.GLES30.glGenVertexArrays;
import static android.opengl.GLES30.glGetUniformLocation;
import static android.opengl.GLES30.glTexParameterf;
import static android.opengl.GLES30.glTexParameteri;
import static android.opengl.GLES30.glUniform1f;
import static android.opengl.GLES30.glUniform1i;
import static android.opengl.GLES30.glVertexAttribPointer;
import static com.galix.avcore.util.GLUtil.DEFAULT_VERT_ARRAY_CODEC;
import static com.galix.avcore.util.GLUtil.DRAW_ORDER;

/**
 * Opengl Render
 */
public abstract class GLRender implements IRender {

    private String mVs;
    private String mFs;
    private int mProgram;
    private IntBuffer mVAO, mVBO, mEBO;
    private List<Runnable> mTasks = new LinkedList<>();

    public GLRender(String vs, String fs) {
        mVs = vs;
        mFs = fs;
    }

    @Override
    public boolean isOpen() {
        return mProgram != -1;
    }

    @Override
    public void open() {
        if (isOpen()) return;
        mProgram = GLUtil.loadProgram(mVs, mFs);
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
        if (mProgram != -1) {
            GLES30.glDeleteProgram(mProgram);
            mProgram = -1;
        }
    }

    @Override
    public void render(AVFrame avFrame) {
        if (!isOpen()) return;
        runTasks();
        bindCurrentProgram();
        onRender(avFrame);
        bindCurrentVAO();
        drawNow();
        bindEmptyVAO();
        flushNow();
    }

    @Override
    public void write(Object config) {
        onWrite(config);
    }

    public abstract void onRender(AVFrame avFrame);

    public abstract void onWrite(Object config);

    public void setTask(Runnable runnable) {
        if (runnable != null) {
            mTasks.add(runnable);
        }
    }

    public void clearTask() {
        mTasks.clear();
    }

    public int getProgram() {
        return mProgram;
    }

    public IntBuffer getVAO() {
        return mVAO;
    }

    private int mActiveTexture = GL_TEXTURE0;

    public void resetTexture() {
        mActiveTexture = GL_TEXTURE0;
    }

    public void bindTexture(String str, int textureId, boolean oes) {
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

    public void bindFloat(String str, float alpha) {
        glUniform1f(glGetUniformLocation(mProgram, str), alpha);
    }

    public void bindCurrentVAO() {
        glBindVertexArray(mVAO.get(0));
    }

    public void drawNow() {
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    public void flushNow() {
        glFlush();
    }

    public void bindCurrentProgram() {
        GLES30.glUseProgram(mProgram);
        resetTexture();
    }

    public void bindEmptyVAO() {
        glBindVertexArray(0);
    }

    public void runTasks() {
        for (Runnable task : mTasks) {
            task.run();
        }
        mTasks.clear();
    }


}
