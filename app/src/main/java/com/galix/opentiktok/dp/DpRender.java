package com.galix.opentiktok.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.util.Log;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.render.IRender;
import com.galix.avcore.util.GLUtil;
import com.galix.avcore.util.IOUtils;
import com.galix.avcore.util.MathUtils;
import com.galix.opentiktok.R;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES20.GL_RGB;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glDeleteTextures;
import static android.opengl.GLES20.glFramebufferTexture2D;
import static android.opengl.GLES20.glTexImage2D;
import static android.opengl.GLES20.glUniformMatrix3fv;
import static android.opengl.GLES30.GL_ARRAY_BUFFER;
import static android.opengl.GLES30.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES30.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES30.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES30.GL_ELEMENT_ARRAY_BUFFER;
import static android.opengl.GLES30.GL_FLOAT;
import static android.opengl.GLES30.GL_FRAGMENT_SHADER;
import static android.opengl.GLES30.GL_FRAMEBUFFER;
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
import static android.opengl.GLES30.glBindFramebuffer;
import static android.opengl.GLES30.glBindTexture;
import static android.opengl.GLES30.glBindVertexArray;
import static android.opengl.GLES30.glBufferData;
import static android.opengl.GLES30.glClear;
import static android.opengl.GLES30.glDrawElements;
import static android.opengl.GLES30.glEnableVertexAttribArray;
import static android.opengl.GLES30.glFlush;
import static android.opengl.GLES30.glGenBuffers;
import static android.opengl.GLES30.glGenFramebuffers;
import static android.opengl.GLES30.glGenTextures;
import static android.opengl.GLES30.glGenVertexArrays;
import static android.opengl.GLES30.glGetUniformLocation;
import static android.opengl.GLES30.glTexParameterf;
import static android.opengl.GLES30.glTexParameteri;
import static android.opengl.GLES30.glUniform1i;
import static android.opengl.GLES30.glUseProgram;
import static android.opengl.GLES30.glVertexAttribPointer;
import static android.opengl.GLES30.glViewport;
import static org.opencv.core.CvType.CV_32F;

/**
 * 地平线渲染器
 */
public class DpRender implements IRender {

    private Map<String, Object> mConfig;
    private Map<String, Object> mNewConfig;
    private SportConfig mSportConfig;
    public static Context context;
    private int mProgram = -1, mDisplayProgram = -1;
    public int[] mVAO, mVBO, mEBO;
    public Size mTextureSize;
    public IntBuffer mPlayerFbo, mPlayerColorTexture;

    public static String KEY_PLAYER_ROI = "KEY_PLAYER_ROI";
    public static String KEY_PLAYER_MASK_ROI = "KEY_PLAYER_MASK_ROI";
    public static String KEY_PLAYER_LUT = "KEY_PLAYER_LUT";
    public static String KEY_PLAYER_MASK = "KEY_PLAYER_MASK";
    public static String KEY_SURFACE_SIZE = "KEY_SURFACE_SIZE";

    public static class SportConfig {
        public int mPlayerId = -1;
        public int mCoachTextureId = 0;
        public int mPlayerLutId = -1;
        public Point[] mSrcPoints;
        public Point[] mDstPoints;
        public IntBuffer mPlayerMaskId;
        public Mat mPlayerMaskMat;
        public FloatBuffer mPlayerMaskMatBuffer;
        public Rect mPlayerMaskRoi;
        public Rect mPlayerRoi;
        public Size mSurfaceSize;
    }

    //解码器纹理上下翻转？
    static float[] DEFAULT_VERT_ARRAY_CODEC = {
            -1.0f, 1.0f, 0.0f, 0.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, -1.0f, 0.0f, 1.0f, 1.0f
    };

    static int[] DRAW_ORDER = {
            0, 1, 2, 1, 2, 3
    };

    @Override
    public boolean isOpen() {
        return mProgram != -1;
    }

    @Override
    public void open() {
        if (isOpen()) return;
        mSportConfig = new SportConfig();
        try {
            mProgram = GLUtil.createAndLinkProgram(GLUtil.loadShader(GLES20.GL_VERTEX_SHADER, IOUtils.readStr(context.getResources().openRawResource(R.raw.dbrendervs))),
                    GLUtil.loadShader(GL_FRAGMENT_SHADER, IOUtils.readStr(context.getResources().openRawResource(R.raw.dbrenderfs))), null);
            mDisplayProgram = GLUtil.createAndLinkProgram(GLUtil.loadShader(GLES20.GL_VERTEX_SHADER, IOUtils.readStr(context.getResources().openRawResource(R.raw.displayvs))),
                    GLUtil.loadShader(GL_FRAGMENT_SHADER, IOUtils.readStr(context.getResources().openRawResource(R.raw.displayfs))), null);
            mVAO = new int[1];
            mVBO = new int[1];
            mEBO = new int[1];
            glGenVertexArrays(1, mVAO, 0);
            glBindVertexArray(mVAO[0]);
            glGenBuffers(1, mVBO, 0);
            glGenBuffers(1, mEBO, 0);
            glBindBuffer(GL_ARRAY_BUFFER, mVBO[0]);
            glBufferData(GL_ARRAY_BUFFER, 4 * 20, FloatBuffer.wrap(DEFAULT_VERT_ARRAY_CODEC), GL_STATIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mEBO[0]);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, 24, IntBuffer.wrap(DRAW_ORDER), GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * 4, 0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * 4, 3 * 4);
            glEnableVertexAttribArray(1);
            glBindVertexArray(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        mProgram = -1;
    }

    @Override
    public void write(Object config) {
        if (mConfig == null) {
            mConfig = new HashMap<>();
        }
        if (mNewConfig == null) {
            mNewConfig = new HashMap<>();
        }
        if (config instanceof HashMap) {
            mNewConfig.clear();
            mNewConfig.putAll((Map<? extends String, ?>) config);
        }
    }

    @Override
    public void render(AVFrame avFrame) {
        avFrame.getSurfaceTexture().updateTexImage();
        avFrame.getSurfaceTextureExt().updateTexImage();
        renderReady(avFrame);
        renderPlayer();
        renderGpu();
    }


    private void renderReady(AVFrame avFrame) {
        if (!mNewConfig.isEmpty()) {
            if (mNewConfig.containsKey(KEY_PLAYER_LUT)) {
                Bitmap player = (Bitmap) mNewConfig.get(KEY_PLAYER_LUT);
                mSportConfig.mPlayerLutId = GLUtil.loadTexture(mSportConfig.mPlayerLutId, player);
            }
            if (mNewConfig.containsKey(KEY_PLAYER_MASK_ROI)) {
                mSportConfig.mPlayerMaskRoi = (Rect) mNewConfig.get(KEY_PLAYER_MASK_ROI);
            }
            if (mNewConfig.containsKey(KEY_PLAYER_ROI)) {
                mSportConfig.mPlayerRoi = (Rect) mNewConfig.get(KEY_PLAYER_ROI);
            }
            if (mNewConfig.containsKey(KEY_SURFACE_SIZE)) {
                mSportConfig.mSurfaceSize = (Size) mNewConfig.get(KEY_SURFACE_SIZE);
            }

            mConfig.putAll(mNewConfig);
            mNewConfig.clear();
        }
        if (mSportConfig.mPlayerMaskId == null) {
            mSportConfig.mPlayerMaskId = IntBuffer.allocate(1);
            mSportConfig.mPlayerMaskId.put(0);
            mSportConfig.mPlayerMaskId.position(0);
        }

//        :地平线byte[]可以用这个,ByteBuffer.wrap(byte[]);
//        if (avFrame.getByteBuffer() != null) {
//            if (mSportConfig.mPlayerMaskId.get(0) != 0) {
//                glDeleteTextures(1, mSportConfig.mPlayerMaskId);
//            }
//            mSportConfig.mPlayerMaskId.position(0);
//            GLES30.glGenTextures(1,mSportConfig.mPlayerMaskId);
//            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
//            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
//            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
//            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
//            glBindTexture(GL_TEXTURE_2D,mSportConfig.mPlayerMaskId.get(0));
//                    GLES30.glTexImage2D(GL_TEXTURE_2D,0,GL_RGB,
//                mSportConfig.mPlayerMaskRoi.width,mSportConfig.mPlayerMaskRoi.height,0,
//                GL_RGB,
//                GL_UNSIGNED_BYTE,
//                avFrame.getByteBuffer());
//        } else {
//            mSportConfig.mPlayerMaskId.position(0);
//            mSportConfig.mPlayerMaskId.put(0);
//        }
//        mSportConfig.mPlayerMaskId.position(0);

        if (avFrame.getBitmap() != null) {
            if (mSportConfig.mPlayerMaskId.get(0) != 0) {
                glDeleteTextures(1, mSportConfig.mPlayerMaskId);
            }
            mSportConfig.mPlayerMaskId.position(0);
            mSportConfig.mPlayerMaskId.put(GLUtil.loadTexture(0, avFrame.getBitmap()));
        } else {
            mSportConfig.mPlayerMaskId.position(0);
            mSportConfig.mPlayerMaskId.put(0);
        }
        mSportConfig.mPlayerMaskId.position(0);

        if (mSportConfig.mSurfaceSize == null) {
            mSportConfig.mSurfaceSize = new Size();
        }
        mSportConfig.mSurfaceSize.width = avFrame.getRoi().width();
        mSportConfig.mSurfaceSize.height = avFrame.getRoi().height();
        mSportConfig.mPlayerId = avFrame.getTexture();
        mSportConfig.mCoachTextureId = avFrame.getTextureExt();
        if (mSportConfig.mSrcPoints == null) {
            mSportConfig.mSrcPoints = new Point[3];
            for (int i = 0; i < 3; i++) {
                mSportConfig.mSrcPoints[i] = new Point();
            }
        }
        if (mSportConfig.mDstPoints == null) {
            mSportConfig.mDstPoints = new Point[3];
            for (int i = 0; i < 3; i++) {
                mSportConfig.mDstPoints[i] = new Point();
            }
        }
        mSportConfig.mSrcPoints[0].x = 0;
        mSportConfig.mSrcPoints[0].y = 0;
        mSportConfig.mSrcPoints[1].x = mSportConfig.mSurfaceSize.width;
        mSportConfig.mSrcPoints[1].y = 0;
        mSportConfig.mSrcPoints[2].x = 0;
        mSportConfig.mSrcPoints[2].y = mSportConfig.mSurfaceSize.height;

        mSportConfig.mDstPoints[0].x = mSportConfig.mPlayerMaskRoi.x;
        mSportConfig.mDstPoints[0].y = mSportConfig.mPlayerMaskRoi.y;
        mSportConfig.mDstPoints[1].x = mSportConfig.mPlayerMaskRoi.x + mSportConfig.mPlayerMaskRoi.width;
        mSportConfig.mDstPoints[1].y = mSportConfig.mPlayerMaskRoi.y;
        mSportConfig.mDstPoints[2].x = mSportConfig.mPlayerMaskRoi.x;
        mSportConfig.mDstPoints[2].y = mSportConfig.mPlayerMaskRoi.y + mSportConfig.mPlayerMaskRoi.height;

        if (mSportConfig.mPlayerMaskMat == null) {
            mSportConfig.mPlayerMaskMat = Mat.eye(3, 3, CV_32F);
        }
        mSportConfig.mPlayerMaskMat = MathUtils.getTransform(mSportConfig.mSrcPoints, mSportConfig.mSurfaceSize,
                mSportConfig.mDstPoints, mSportConfig.mSurfaceSize);
        if (mSportConfig.mPlayerMaskMatBuffer == null) {
            mSportConfig.mPlayerMaskMatBuffer = FloatBuffer.allocate(3 * 3);
        }
        mSportConfig.mPlayerMaskMatBuffer.clear();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                mSportConfig.mPlayerMaskMatBuffer.put((float) mSportConfig.mPlayerMaskMat.get(i, j)[0]);
            }
        }
        mSportConfig.mPlayerMaskMatBuffer.position(0);
    }

    private void renderPlayer() {
        if (mPlayerFbo == null) {
            mPlayerFbo = IntBuffer.allocate(1);
            glGenFramebuffers(1, mPlayerFbo);
            mPlayerColorTexture = IntBuffer.allocate(1);
            glGenTextures(1, mPlayerColorTexture);
            glBindFramebuffer(GL_FRAMEBUFFER, mPlayerFbo.get(0));
            glBindTexture(GL_TEXTURE_2D, mPlayerColorTexture.get(0));
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, (int) mSportConfig.mSurfaceSize.width, (int) mSportConfig.mSurfaceSize.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mPlayerColorTexture.get(0), 0);
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, mPlayerFbo.get(0));
        }
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glUseProgram(mProgram);
        glViewport(0, 0, (int) mSportConfig.mSurfaceSize.width, (int) mSportConfig.mSurfaceSize.height);
        glBindVertexArray(mVAO[0]);
        mActiveTexture = GL_TEXTURE0;
        bindTexture("coachTexture", mSportConfig.mCoachTextureId, true);
        bindTexture("playerTexture", mSportConfig.mPlayerId, true);
        bindTexture("playerMaskTexture", mSportConfig.mPlayerMaskId.get(0), false);
        bindMat3("playerMaskMat", mSportConfig.mPlayerMaskMatBuffer);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    private void renderGpu() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glUseProgram(mDisplayProgram);
        glViewport(0, 0, 1080, 853);
        glBindVertexArray(mVAO[0]);
        mActiveTexture = GL_TEXTURE0;
        bindTexture("playerTexture", mPlayerColorTexture.get(0), false);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        glFlush();
    }

    private int mActiveTexture = GL_TEXTURE0;

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

    private void bindMat3(String str, FloatBuffer buffer) {
        glUniformMatrix3fv(glGetUniformLocation(mProgram, str), 1, false, buffer);
    }

    public static HashMap<String, Object> buildMap(Object[] configs) {
        if (configs == null) return null;
        HashMap<String, Object> maps = new HashMap<>();
        for (int i = 0; i < configs.length / 2; i++) {
            maps.put((String) configs[2 * i], configs[2 * i + 1]);
        }
        return maps;
    }
}
