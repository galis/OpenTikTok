package com.galix.avcore.render.filters;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.opengl.GLES30;
import android.text.TextUtils;
import android.util.Size;

import com.galix.avcore.gl.ResourceManager;
import com.galix.avcore.util.GLUtil;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.MathUtils;

import org.opencv.core.Mat;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_FALSE;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_STENCIL_BUFFER_BIT;
import static android.opengl.GLES20.GL_TRUE;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glDeleteFramebuffers;
import static android.opengl.GLES20.glFramebufferTexture2D;
import static android.opengl.GLES20.glGenFramebuffers;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glTexImage2D;
import static android.opengl.GLES20.glUniform2fv;
import static android.opengl.GLES20.glUniform3fv;
import static android.opengl.GLES20.glUniform4fv;
import static android.opengl.GLES20.glUniformMatrix3fv;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glViewport;
import static android.opengl.GLES30.GL_ARRAY_BUFFER;
import static android.opengl.GLES30.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES30.GL_COLOR_ATTACHMENT1;
import static android.opengl.GLES30.GL_COLOR_ATTACHMENT2;
import static android.opengl.GLES30.GL_COLOR_ATTACHMENT3;
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
import static com.galix.avcore.util.GLUtil.DRAW_ORDER;

/**
 * BaseFilter
 * 使用前请注意FBO参数
 */
public abstract class BaseFilter implements IFilter {

    private static IntBuffer S_ATTACHMENTS = IntBuffer.wrap(new int[]{
            GL_COLOR_ATTACHMENT0,
            GL_COLOR_ATTACHMENT1,
            GL_COLOR_ATTACHMENT2,
            GL_COLOR_ATTACHMENT3
    });

    //    private static IntBuffer S_VAO, S_VBO, S_EBO;
    private String mVs;
    private String mFs;
    private int mProgram = -1;
    private int mMRTNum = 1;
    private Size mLastFboSize;
    private boolean mIsAutoBind = false;
    private boolean mIsOpen = false;
    private boolean mIsClear = true;
    private IntBuffer mVao, mEbo, mVbo;
    private IntBuffer mFbo = IntBuffer.allocate(1);
    private GLTexture[] mColorTextures;
    private List<Runnable> mPreTasks = new LinkedList<>();
    private List<Runnable> mPostTasks = new LinkedList<>();
    private Runnable mDrawTask;
    private Map<String, Object> mConfig = new HashMap<>();
    private Map<String, Object> mTempConfig = new HashMap<>();
    private BaseFilterGroup mParent;
    private Map<String, Integer> mUniformIndexs = new HashMap<>();

    public BaseFilter(String vs, String fs) {
        mVs = vs;
        mFs = fs;
    }

    public BaseFilter(int vs, int fs) {
        mVs = ResourceManager.getManager().loadGLSL(vs);
        mFs = ResourceManager.getManager().loadGLSL(fs);
    }

    @Override
    public boolean isOpen() {
        return mIsOpen;
    }

    @Override
    public void open() {
        if (isOpen()) return;
        mProgram = GLUtil.loadProgram(mVs, mFs);
        fetchUniform(mVs);
        fetchUniform(mFs);
        createDefaultBuffer();
        markOpen(true);
    }

    private void createDefaultBuffer() {
        mVao = IntBuffer.allocate(1);
        mVbo = IntBuffer.allocate(1);
        mEbo = IntBuffer.allocate(1);
        glGenVertexArrays(1, mVao);
        glGenBuffers(1, mVbo);
        glGenBuffers(1, mEbo);
        glBindVertexArray(mVao.get(0));
        glBindBuffer(GL_ARRAY_BUFFER, mVbo.get(0));
        glBufferData(GL_ARRAY_BUFFER, 4 * 20, FloatBuffer.wrap(DEFAULT_VERT_ARRAY_0), GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mEbo.get(0));
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, 24, IntBuffer.wrap(DRAW_ORDER), GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * 4, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * 4, 3 * 4);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);
    }

    public void markOpen(boolean open) {
        mIsOpen = open;
    }

    @Override
    public void close() {
        if (mProgram != -1) {
            GLES30.glDeleteProgram(mProgram);
            mProgram = -1;
        }
        if (mFbo != null) {
            mFbo.position(0);
            GLES30.glDeleteFramebuffers(1, mFbo);
        }
        if (mVao != null) {
            mVao.position(0);
            GLES30.glDeleteVertexArrays(1, mVao);
        }
        if (mVbo != null) {
            mVbo.position(0);
            GLES30.glDeleteBuffers(1, mVbo);
        }
        if (mEbo != null) {
            mEbo.position(0);
            GLES30.glDeleteBuffers(1, mEbo);
        }
        releaseTextures();
    }

    @Override
    public void render() {
        if (!isOpen()) return;
//        TimeUtils.RecordStart(getClass().getSimpleName() + "#filter_render");
        runTasks();
        bindCurrentProgram();
        bindCurrentVAO();
        onRenderPre();
        bindFBO();
        bindMRT();
        drawNow();
        onRenderPost();
        bindEmptyVAO();
//        TimeUtils.RecordEnd(getClass().getSimpleName() + "#filter_render");
    }

    @Override
    public void renderSimulate() {//模拟render,without draw
        if (!isOpen()) return;
        runTasks();
        bindCurrentProgram();
        bindCurrentVAO();
        onRenderPre();
        bindFBO();
        bindMRT();
        //drawNow();
        onRenderPost();
        bindEmptyVAO();
    }

    private void releaseTextures() {
        if (mColorTextures != null && mColorTextures.length > 0) {
            for (int i = 0; i < mColorTextures.length; i++) {
                if (mColorTextures[i].id() != 0) {
                    GLES30.glDeleteTextures(1, mColorTextures[i].idAsBuf());
                }
            }
        }
        mColorTextures = null;
    }

    private void bindFBO() {
        if ((Boolean) getConfig(USE_FBO, false)) {
            Size fboSize = (Size) getConfig(FBO_SIZE);
            mFbo.position(0);
            if (mLastFboSize == null || !mLastFboSize.equals(fboSize)) {
                //process fbo
                if (mFbo.get() != 0) {
                    mFbo.position(0);
                    glDeleteFramebuffers(1, mFbo);
                }
                mFbo.position(0);
                glGenFramebuffers(1, mFbo);
                mFbo.position(0);
                glBindFramebuffer(GL_FRAMEBUFFER, mFbo.get());
                //process textures
                releaseTextures();
                mColorTextures = new GLTexture[mMRTNum];
                for (int i = 0; i < mMRTNum; i++) {
                    mColorTextures[i] = new GLTexture(0, false);
                    glGenTextures(1, mColorTextures[i].idAsBuf());
                    glBindTexture(GL_TEXTURE_2D, mColorTextures[i].id());
                    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, fboSize.getWidth(), fboSize.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
                    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, mColorTextures[i].id(), 0);
                    mColorTextures[i].setSize(fboSize.getWidth(), fboSize.getHeight());
                }
                mLastFboSize = fboSize;
            } else {
                glBindFramebuffer(GL_FRAMEBUFFER, mFbo.get());
            }
            glViewport(0, 0, fboSize.getWidth(), fboSize.getHeight());
            if (mIsClear) {
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);//这里可以扩展
            }
        }
    }

    private void bindMRT() {
        if (mMRTNum > 1) {
            GLES30.glDrawBuffers(mMRTNum, S_ATTACHMENTS);
        }
    }

    @Override
    public IFilter clear(boolean isClear) {
        mIsClear = isClear;
        return this;
    }

    @Override
    public void write(Map<String, Object> config) {
        if (config == null) return;
        onWrite(config);
        mConfig.putAll(config);
    }

    @Override
    public void write(Object... configs) {
        if (configs == null) return;
        mTempConfig.clear();
        for (int i = 0; i < configs.length / 2; i++) {
            mTempConfig.put((String) configs[2 * i], configs[2 * i + 1]);
        }
        onWrite(mTempConfig);
        mConfig.putAll(mTempConfig);
    }

    /**
     * 主要做绑定参数
     */
    public void onRenderPre() {
        if (mIsAutoBind) {
            autoBind();
        }
    }

    public void onRenderPost() {
        for (Runnable task : mPostTasks) {
            task.run();
        }
        mPostTasks.clear();

        if (mParent != null) {
            mParent.setLastFilter(this);
        }
    }

    public void onWrite(Map<String, Object> config) {

    }

    @Override
    public IFilter setPreTask(Runnable runnable) {//draw之前
        if (runnable != null) {
            mPreTasks.add(runnable);
        }
        return this;
    }

    @Override
    public IFilter setPostTask(Runnable runnable) {//draw之后
        if (runnable != null) {
            mPostTasks.add(runnable);
        }
        return this;
    }

    @Override
    public IFilter customDraw(Runnable drawTask) {
        mDrawTask = drawTask;
        return this;
    }

    public void clearTask() {
        mPreTasks.clear();
        mPostTasks.clear();
    }

    public int getProgram() {
        return mProgram;
    }

    public IntBuffer getVAO() {
        return mVao;
    }

    private int mActiveTexture = GL_TEXTURE0;

    public void resetTexture() {
        mActiveTexture = GL_TEXTURE0;
    }

    public void bindMat3(String str, FloatBuffer buffer) {
        if (!mUniformIndexs.containsKey(str)) {
            mUniformIndexs.put(str, glGetUniformLocation(mProgram, str));
        }
        glUniformMatrix3fv(mUniformIndexs.get(str), 1, false, buffer);
    }

    public void bindMat3(String str) {
        if (mConfig.containsKey(str) && mConfig.get(str) instanceof FloatBuffer) {
            bindMat3(str, (FloatBuffer) mConfig.get(str));
        } else if (mConfig.containsKey(str) && mConfig.get(str) instanceof Mat) {
            bindMat3(str, MathUtils.mat2FloatBuffer9((Mat) mConfig.get(str)));
        } else if (mConfig.containsKey(str) && mConfig.get(str) instanceof Matrix) {
            bindMat3(str, MathUtils.matrixFloatBuffer9((Matrix) mConfig.get(str)));
        } else {
            bindMat3(str, FloatBuffer.allocate(9));
        }
    }

    public void bindMat4(String str, FloatBuffer buffer) {
        if (!mUniformIndexs.containsKey(str)) {
            mUniformIndexs.put(str, glGetUniformLocation(mProgram, str));
        }
        glUniformMatrix4fv(mUniformIndexs.get(str), 1, false, buffer);
    }

    public void bindMat4(String str) {
        if (mConfig.containsKey(str) && mConfig.get(str) instanceof FloatBuffer) {
            bindMat4(str, (FloatBuffer) mConfig.get(str));
        } else if (mConfig.containsKey(str) && mConfig.get(str) instanceof Mat) {
            bindMat4(str, MathUtils.mat2FloatBuffer16((Mat) mConfig.get(str)));
        } else {
            bindMat4(str, FloatBuffer.allocate(16));
        }
    }

    public void bindTexture(String str, int textureId, boolean oes) {
        glActiveTexture(mActiveTexture);
        int type = oes ? GL_TEXTURE_EXTERNAL_OES : GL_TEXTURE_2D;
        glBindTexture(type, textureId);
        glTexParameterf(type, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameterf(type, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(type, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(type, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        if (!mUniformIndexs.containsKey(str)) {
            mUniformIndexs.put(str, glGetUniformLocation(mProgram, str));
        }
        glUniform1i(mUniformIndexs.get(str), mActiveTexture - GL_TEXTURE0);
        mActiveTexture++;
    }

    public void bindTexture(String str, GLTexture texture) {
        if (texture == null) {
            bindTexture(str, 0, false);
            return;
        }
        bindTexture(str, texture.id(), texture.isOes());
    }

    public void bindTexture(String str) {//按照名字索引..
        if (mConfig.containsKey(str) && mConfig.get(str) instanceof GLTexture) {
            bindTexture(str, (GLTexture) mConfig.get(str));
        } else {
            bindTexture(str, GLUtil.DEFAULT_TEXTURE);
        }
    }

    private static FloatBuffer gPointBuffer = FloatBuffer.allocate(2);

    public void bindVec2(String str) {
        if (mConfig.containsKey(str)) {
            if (mConfig.get(str) instanceof FloatBuffer) {
                bindVec2(str, (FloatBuffer) mConfig.get(str));
                return;
            } else if (mConfig.get(str) instanceof PointF) {
                PointF point = (PointF) mConfig.get(str);
                gPointBuffer.position(0);
                gPointBuffer.put(point.x);
                gPointBuffer.put(point.y);
                gPointBuffer.position(0);
                bindVec2(str, gPointBuffer);
                return;
            }
        }
        bindVec2(str, GLUtil.DEFAULT_VEC2);
    }

    public void bindVec2(String str, FloatBuffer byteBuffer) {
        if (!mUniformIndexs.containsKey(str)) {
            mUniformIndexs.put(str, glGetUniformLocation(mProgram, str));
        }
        glUniform2fv(mUniformIndexs.get(str), 1, byteBuffer);
    }

    public void bindVec3(String str) {
        if (mConfig.containsKey(str) && mConfig.get(str) instanceof FloatBuffer) {
            bindVec3(str, (FloatBuffer) mConfig.get(str));
        } else {
            bindVec3(str, GLUtil.DEFAULT_VEC3);
        }
    }

    public void bindVec3(String str, FloatBuffer byteBuffer) {
        if (!mUniformIndexs.containsKey(str)) {
            mUniformIndexs.put(str, glGetUniformLocation(mProgram, str));
        }
        glUniform3fv(mUniformIndexs.get(str), 1, byteBuffer);
    }

    public void bindVec4(String str) {
        if (mConfig.containsKey(str) && mConfig.get(str) instanceof FloatBuffer) {
            bindVec4(str, (FloatBuffer) mConfig.get(str));
        } else {
            bindVec4(str, GLUtil.DEFAULT_VEC4);
        }
    }

    public void bindVec4(String str, FloatBuffer byteBuffer) {
        if (!mUniformIndexs.containsKey(str)) {
            mUniformIndexs.put(str, glGetUniformLocation(mProgram, str));
        }
        glUniform4fv(mUniformIndexs.get(str), 1, byteBuffer);
    }

    public void bindFloat(String str) {
        if (mConfig.containsKey(str)) {
            bindFloat(str, (Float) mConfig.get(str));
        } else {
            bindFloat(str, 0);
        }
    }

    public void bindFloat(String str, float floatV) {
        if (!mUniformIndexs.containsKey(str)) {
            mUniformIndexs.put(str, glGetUniformLocation(mProgram, str));
        }
        glUniform1f(mUniformIndexs.get(str), floatV);
    }

    public void bindBool(String str) {
        if (mConfig.containsKey(str)) {
            bindBool(str, (boolean) mConfig.get(str));
        } else {
            bindBool(str, false);
        }
    }

    public void bindBool(String str, boolean bb) {
        if (!mUniformIndexs.containsKey(str)) {
            mUniformIndexs.put(str, glGetUniformLocation(mProgram, str));
        }
        GLES30.glUniform1i(mUniformIndexs.get(str), bb ? GL_TRUE : GL_FALSE);
    }

    public void bindCurrentVAO() {
        glBindVertexArray(((IntBuffer) mVao.position(0)).get());
    }

    public void drawNow() {
        if (mDrawTask != null) {
            mDrawTask.run();
            return;
        }
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    public void flush() {
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
        for (Runnable task : mPreTasks) {
            task.run();
        }
        mPreTasks.clear();
    }

    @Override
    public GLTexture getOutputTexture() {
        if ((Boolean) getConfig(USE_FBO)) {
            return mColorTextures[0];
        } else {
            return null;
        }
    }

    @Override
    public GLTexture getOutputTexture(int idx) {
        if (idx < mMRTNum && (Boolean) getConfig(USE_FBO)) {
            return mColorTextures[idx];
        } else {
            if (getConfig().containsKey(INPUT_IMAGE)) {
                return (GLTexture) getConfig(INPUT_IMAGE);
            }
            return (GLTexture) getConfig(INPUT_IMAGE_OES);
        }
    }

    @Override
    public int getFBO() {
        return mFbo.get(0);
    }

    @Override
    public Map<String, Object> getConfig() {
        return mConfig;
    }

    public Object getConfig(String key) {
        if (mConfig.containsKey(key)) {
            return mConfig.get(key);
        }
        return null;
    }

    public Object getConfig(String key, Object defaultValue) {
        if (mConfig.containsKey(key)) {
            return mConfig.get(key);
        }
        return defaultValue;
    }

    @Override
    public IFilter useFBO(boolean isUse) {
        getConfig().put(USE_FBO, isUse);
        return this;
    }

    @Override
    public IFilter setFBOSize(Size fboSize) {
        getConfig().put(FBO_SIZE, fboSize);
        return this;
    }

    @Override
    public IFilter setVAO(IntBuffer buffer) {
        if (buffer != null) {
            mVao = buffer;
        }
        return this;
    }

    @Override
    public IFilter bind(Object... kv) {
        if (kv != null && kv.length % 2 == 0) {
            for (int i = 0; i < kv.length / 2; i++) {
                getConfig().put((String) kv[2 * i], kv[2 * i + 1]);
            }
        }
        mIsAutoBind = true;
        return this;
    }

    //处理Uniform
    private enum UniformType {
        SAMPLER_2D,
        SAMPLER_External_OES,
        MAT3,
        MAT4,
        FLOAT,
        VEC2,
        VEC3,
        VEC4,
    }

    private LinkedHashMap<String, UniformType> mUniforms = new LinkedHashMap<>();

    private void fetchUniform(String vs) {
        if (TextUtils.isEmpty(vs)) return;
        String[] lines = vs.split("\n");
        for (String line : lines) {
            if (!line.contains(";")) continue;
            String valueLine = line.substring(0, line.indexOf(";"));
            String[] vals = valueLine.split(" ");
            if (vals.length < 3) continue;
            if (vals[0].contains("uniform")) {
                if (vals[1].contains("sampler2D")) {
                    mUniforms.put(vals[2], UniformType.SAMPLER_2D);
                } else if (vals[1].contains("samplerExternalOES")) {
                    mUniforms.put(vals[2], UniformType.SAMPLER_External_OES);
                } else if (vals[1].contains("mat3")) {
                    mUniforms.put(vals[2], UniformType.MAT3);
                } else if (vals[1].contains("mat4")) {
                    mUniforms.put(vals[2], UniformType.MAT4);
                } else if (vals[1].contains("float")) {
                    mUniforms.put(vals[2], UniformType.FLOAT);
                } else if (vals[1].contains("vec2")) {
                    mUniforms.put(vals[2], UniformType.VEC2);
                } else if (vals[1].contains("vec3")) {
                    mUniforms.put(vals[2], UniformType.VEC3);
                } else if (vals[1].contains("vec4")) {
                    mUniforms.put(vals[2], UniformType.VEC4);
                } else {
                    LogUtil.logMain("GLSL#Unkonw uniform type!!!!!!!!!!!#" + line);
                }
            }
        }
    }

    private void autoBind() {
        Set<String> keys = mUniforms.keySet();
        for (String key : keys) {
            UniformType type = mUniforms.get(key);
            if (type == UniformType.SAMPLER_2D ||
                    mUniforms.get(key) == UniformType.SAMPLER_External_OES) {
                bindTexture(key);
            } else if (type == UniformType.MAT3) {
                bindMat3(key);
            } else if (type == UniformType.MAT4) {
                bindMat4(key);
            } else if (type == UniformType.FLOAT) {
                bindFloat(key);
            } else if (type == UniformType.VEC2) {
                bindVec2(key);
            } else if (type == UniformType.VEC3) {
                bindVec3(key);
            } else if (type == UniformType.VEC4) {
                bindVec4(key);
            } else {
                LogUtil.logMain("autoBind#GLSL#Unkonw uniform type!!!!!!!!!!!#");
            }
        }
    }

    @Override
    public void setParent(BaseFilterGroup baseFilterGroup) {
        mParent = baseFilterGroup;
    }

    @Override
    public IFilter setMRT(int n) {
        if (n > 1) {
            mMRTNum = n;
        }
        return this;
    }

    public BaseFilterGroup getParent() {
        return mParent;
    }

    private static final Size DefaultSize = new Size(1920, 1080);

    @Override
    public Size fboSize() {
        return (Size) getConfig(FBO_SIZE, DefaultSize);
    }
}
