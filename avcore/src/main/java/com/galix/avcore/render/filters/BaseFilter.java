package com.galix.avcore.render.filters;

import android.graphics.Rect;
import android.opengl.GLES30;
import android.util.Size;

import com.galix.avcore.gl.GLManager;
import com.galix.avcore.util.GLUtil;
import com.galix.avcore.util.MathUtils;

import org.opencv.core.Mat;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_FALSE;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_TRUE;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glDeleteFramebuffers;
import static android.opengl.GLES20.glDeleteTextures;
import static android.opengl.GLES20.glFramebufferTexture2D;
import static android.opengl.GLES20.glGenFramebuffers;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glTexImage2D;
import static android.opengl.GLES20.glUniformMatrix3fv;
import static android.opengl.GLES20.glViewport;
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
import static com.galix.avcore.util.GLUtil.DEFAULT_VERT_ARRAY_0;
import static com.galix.avcore.util.GLUtil.DEFAULT_VERT_ARRAY_90;
import static com.galix.avcore.util.GLUtil.DRAW_ORDER;

/**
 * BaseFilter
 * 使用前请注意FBO参数
 */
public abstract class BaseFilter implements IFilter {

    private String mVs;
    private String mFs;
    private int mProgram = -1;
    private IntBuffer mVAO, mVBO, mEBO;
    private List<Runnable> mTasks = new LinkedList<>();
    private Rect mViewPort = new Rect(0, 0, 0, 0);
    private Map<String, Object> mConfig = new HashMap<>();

    //FBO相关
    private boolean mUseFbo = true;//是否启用FBO
    private Size mFboSize = new Size(0, 0);
    private Size mLastFboSize;
    private GLTexture mColorTexture = new GLTexture(0, false);
    private IntBuffer mFbo = IntBuffer.allocate(1);

    public BaseFilter(String vs, String fs) {
        mVs = vs;
        mFs = fs;
    }

    public BaseFilter(int vs, int fs) {
        mVs = GLManager.getManager().loadGLSL(vs);
        mFs = GLManager.getManager().loadGLSL(fs);
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
        glBufferData(GL_ARRAY_BUFFER, 4 * 20, FloatBuffer.wrap(DEFAULT_VERT_ARRAY_0), GL_STATIC_DRAW);
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
    public void render() {
        if (!isOpen()) return;
        runTasks();
        bindCurrentProgram();
        bindCurrentVAO();
        onRenderPre();
        bindFBO();
        drawNow();
        onRenderPost();
        bindEmptyVAO();
//        flushNow();
    }

    private void bindFBO() {
        if (mUseFbo) {
            mFbo.position(0);
            if (mLastFboSize != mFboSize) {
                if (mFbo.get() != 0) {
                    mFbo.position(0);
                    glDeleteFramebuffers(1, mFbo);
                }
                if (mColorTexture.id() != 0) {
                    glDeleteTextures(1, mColorTexture.idAsBuf());
                }
                mFbo.position(0);
                glGenFramebuffers(1, mFbo);
                mFbo.position(0);
                glGenTextures(1, mColorTexture.idAsBuf());
                glBindFramebuffer(GL_FRAMEBUFFER, mFbo.get());
                glBindTexture(GL_TEXTURE_2D, mColorTexture.id());
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mFboSize.getWidth(), mFboSize.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mColorTexture.id(), 0);
                mLastFboSize = mFboSize;
                mColorTexture.setSize(mLastFboSize.getWidth(), mLastFboSize.getHeight());
            } else {
                glBindFramebuffer(GL_FRAMEBUFFER, mFbo.get());
            }
            glViewport(0, 0, mFboSize.getWidth(), mFboSize.getHeight());
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);//这里可以扩展
        }
    }

    @Override
    public void write(Map<String, Object> config) {
        if (config == null) return;
        if (config.containsKey("use_fbo")) {
            mUseFbo = (boolean) config.get("use_fbo");
        }
        if (config.containsKey("fbo_size")) {
            mFboSize = (Size) config.get("fbo_size");
        }
        mConfig.clear();
        mConfig.putAll(config);
        onWrite(config);
    }

    @Override
    public void write(Object... configs) {
        if (configs == null) return;
        mConfig.clear();
        for (int i = 0; i < configs.length / 2; i++) {
            mConfig.put((String) configs[2 * i], configs[2 * i + 1]);
        }
        onWrite(mConfig);
    }

    /**
     * 主要做绑定参数
     */
    public abstract void onRenderPre();

    public void onRenderPost() {

    }

    public abstract void onWrite(Map<String, Object> config);

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

    public void bindMat3(String str, FloatBuffer buffer) {
        glUniformMatrix3fv(glGetUniformLocation(mProgram, str), 1, false, buffer);
    }

    public void bindMat3(String str) {
        if (mConfig.containsKey(str) && mConfig.get(str) instanceof FloatBuffer) {
            bindMat3(str, (FloatBuffer) mConfig.get(str));
        } else if (mConfig.containsKey(str) && mConfig.get(str) instanceof Mat) {
            bindMat3(str, MathUtils.mat2FloatBuffer((Mat) mConfig.get(str)));
        } else {
            bindMat3(str, FloatBuffer.allocate(9));
        }
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

    public void bindTexture(String str, GLTexture texture) {
        if (texture == null) {
            bindTexture(str, 0, false);
            return;
        }
        bindTexture(str, texture.id(), texture.isOes());
    }

    public void bindTexture(String str) {
        if (mConfig.containsKey(str) && mConfig.get(str) instanceof GLTexture) {
            bindTexture(str, (GLTexture) mConfig.get(str));
        } else {
            bindTexture(str, GLUtil.DEFAULT_TEXTURE);
        }
    }

    public void bindFloat(String str) {
        if (mConfig.containsKey(str)) {
            bindFloat(str, (Float) mConfig.get(str));
        } else {
            bindFloat(str, 0);
        }
    }

    public void bindFloat(String str, float alpha) {
        glUniform1f(glGetUniformLocation(mProgram, str), alpha);
    }

    public void bindBool(String str) {
        if (mConfig.containsKey(str)) {
            bindBool(str, (boolean) mConfig.get(str));
        } else {
            bindBool(str, false);
        }
    }

    public void bindBool(String str, boolean bb) {
        GLES30.glUniform1i(glGetUniformLocation(mProgram, str), bb ? GL_TRUE : GL_FALSE);
    }

    public void bindCurrentVAO() {
        mVAO.position(0);
        glBindVertexArray(mVAO.get());
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

    @Override
    public GLTexture getOutputTexture() {
        return mColorTexture;
    }

}
