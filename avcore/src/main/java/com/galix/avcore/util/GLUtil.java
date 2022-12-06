package com.galix.avcore.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.util.Log;
import android.util.Size;

import com.galix.avcore.render.filters.GLTexture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glTexParameterf;
import static android.opengl.GLES20.glTexParameteri;

public final class GLUtil {

    public static final GLTexture DEFAULT_OES_TEXTURE = new GLTexture(0, true);
    public static final GLTexture DEFAULT_TEXTURE = new GLTexture(0, false);
    public static final FloatBuffer DEFAULT_VEC2;
    public static final FloatBuffer DEFAULT_VEC3;
    public static final FloatBuffer DEFAULT_VEC4;
    private static final String TAG = "GLUtil";

    static {
        DEFAULT_VEC2 = FloatBuffer.allocate(2).put(1920).put(1080);
        DEFAULT_VEC2.position(0);
        DEFAULT_VEC3 = FloatBuffer.allocate(3);
        DEFAULT_VEC4 = FloatBuffer.allocate(4);
    }

    private GLUtil() {

    }

    /**
     * Helper function to compile and link a program.
     *
     * @param vertexShaderHandle   An OpenGL handle to an already-compiled vertex shader.
     * @param fragmentShaderHandle An OpenGL handle to an already-compiled fragment shader.
     * @param attributes           Attributes that need to be bound to the program.
     * @return An OpenGL handle to the program.
     */
    public static int createAndLinkProgram(final int vertexShaderHandle, final int fragmentShaderHandle,
                                           final String[] attributes) {
        int programHandle = GLES30.glCreateProgram();

        if (programHandle != 0) {
            // Bind the vertex shader to the program.
            GLES30.glAttachShader(programHandle, vertexShaderHandle);

            // Bind the fragment shader to the program.
            GLES30.glAttachShader(programHandle, fragmentShaderHandle);

            // Bind attributes
            if (attributes != null) {
                final int size = attributes.length;
                for (int i = 0; i < size; i++) {
                    GLES30.glBindAttribLocation(programHandle, i, attributes[i]);
                }
            }

            // Link the two shaders together into a program.
            GLES30.glLinkProgram(programHandle);

            // Get the link status.
            final int[] linkStatus = new int[1];
            GLES30.glGetProgramiv(programHandle, GLES30.GL_LINK_STATUS, linkStatus, 0);

            // If the link failed, delete the program.
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Error compiling program: " + GLES30.glGetProgramInfoLog(programHandle));
                GLES30.glDeleteProgram(programHandle);
                programHandle = 0;
            }
        }

        if (programHandle == 0) {
            throw new RuntimeException("Error creating program.");
        }

        return programHandle;
    }

    public static int loadProgram(Context context, int rawVs, int rawFs) {
        try {
            return GLUtil.createAndLinkProgram(
                    GLUtil.loadShader(GLES30.GL_VERTEX_SHADER,
                            IOUtils.readStr(context.getResources().openRawResource(rawVs))),
                    GLUtil.loadShader(GL_FRAGMENT_SHADER,
                            IOUtils.readStr(context.getResources().openRawResource(rawFs))), null);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static int loadProgram(String vs, String fs) {
        return GLUtil.createAndLinkProgram(
                GLUtil.loadShader(GLES30.GL_VERTEX_SHADER,
                        vs),
                GLUtil.loadShader(GL_FRAGMENT_SHADER,
                        fs), null);
    }

    /**
     * Utility method for compiling a OpenGL shader.
     *
     * <p>
     * <strong>Note:</strong> When developing shaders, use the checkGlError() method to debug shader coding errors.
     * </p>
     *
     * @param type       - Vertex or fragment shader type.
     * @param shaderCode - String containing the shader code.
     * @return - Returns an id for the shader.
     */
    public static int loadShader(int type, String shaderCode) {

        // create a vertex shader type (GLES30.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES30.GL_FRAGMENT_SHADER)
        int shader = GLES30.glCreateShader(type);

        // add the source code to the shader and compile it
        GLES30.glShaderSource(shader, shaderCode);
        GLES30.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("GLUtil", "Shader error: " + GLES30.glGetShaderInfoLog(shader) + "\n" + shaderCode);
            GLES30.glDeleteShader(shader);
        }

        return shader;
    }

    public static GLTexture gen2DTexture() {
        IntBuffer textureBuffer = IntBuffer.allocate(1);
        GLES30.glGenTextures(1, textureBuffer);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureBuffer.get(0));
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return new GLTexture(textureBuffer.get(0), false);
    }

    public static GLTexture gen2DOesTexture() {
        IntBuffer textureBuffer = IntBuffer.allocate(1);
        GLES30.glGenTextures(1, textureBuffer);
        GLES30.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureBuffer.get(0));
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return new GLTexture(textureBuffer.get(0), true);
    }

    public static int loadTexture(final byte[] textureData) {
        ByteArrayInputStream textureIs = new ByteArrayInputStream(textureData);
        return loadTexture(textureIs);
    }

    public static int loadTexture(final InputStream is) {
        Log.v("GLUtil", "Loading texture from stream...");

        final int[] textureHandle = new int[1];

        GLES30.glGenTextures(1, textureHandle, 0);
        checkGlError("glGenTextures");
        if (textureHandle[0] == 0) {
            throw new RuntimeException("Error loading texture.");
        }

        Log.v("GLUtil", "Handler: " + textureHandle[0]);

        final Bitmap bitmap = loadBitmap(is);

        // Bind to the texture in OpenGL
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle[0]);
        checkGlError("glBindTexture");
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);
        checkGlError("texImage2D");
        bitmap.recycle();
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);

        Log.v("GLUtil", "Loaded texture ok");
        return textureHandle[0];
    }

    public static void loadTexture(GLTexture glTexture, Bitmap bitmap) {
        if (bitmap == null) return;
        if (glTexture.data() == bitmap) return;
        if (glTexture.id() == 0) {
            glTexture.idAsBuf().put(loadTexture(bitmap));
        } else {
            GLUtil.checkGlError("test");
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, glTexture.id());
            GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bitmap);
            GLUtil.checkGlError("test");
        }
        glTexture.setSize(bitmap.getWidth(), bitmap.getHeight());
        glTexture.setData(bitmap);
    }

    public static int loadTexture(int textureId, Bitmap bitmap) {
        if (bitmap == null) return -1;
        if (textureId == 0) {
            return loadTexture(bitmap);
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
        GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bitmap);
//        bitmap.recycle();TODO
        return textureId;
    }

    public static Bitmap dumpTexture(GLTexture texture) {
//        if (texture.isOes()) {
//            return null;
//        }
        IntBuffer lastBuf = IntBuffer.allocate(1);
        IntBuffer newFboBuf = IntBuffer.allocate(1);
        GLES30.glGetIntegerv(GL_FRAMEBUFFER_BINDING, lastBuf);
        GLES30.glGenFramebuffers(1, newFboBuf);
        GLES30.glBindFramebuffer(GL_FRAMEBUFFER, newFboBuf.get());
        Bitmap bitmap = Bitmap.createBitmap(texture.size().getWidth(), texture.size().getHeight(), Bitmap.Config.ARGB_8888);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(texture.size().getWidth() * texture.size().getHeight() * 4);
        GLES30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, texture.isOes() ? GL_TEXTURE_EXTERNAL_OES : GL_TEXTURE_2D, texture.id(), 0);
        GLES30.glReadPixels(0, 0, texture.size().getWidth(), texture.size().getHeight(), GLES30.GL_RGBA, GL_UNSIGNED_BYTE, byteBuffer);
        bitmap.copyPixelsFromBuffer(byteBuffer);
        GLES30.glBindFramebuffer(GL_FRAMEBUFFER, lastBuf.get());
        newFboBuf.position(0);
        GLES30.glDeleteFramebuffers(1, newFboBuf);

        Matrix matrix = new Matrix();
        matrix.postRotate(180);
        matrix.postScale(-1, 1.f);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
        return bitmap;
    }

    public static Bitmap dumpScreenTexture(Size surfaceSize) {
        Bitmap bitmap = Bitmap.createBitmap(surfaceSize.getWidth(), surfaceSize.getHeight(), Bitmap.Config.ARGB_8888);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(surfaceSize.getWidth() * surfaceSize.getHeight() * 4);
        GLES30.glReadPixels(0, 0, surfaceSize.getWidth(), surfaceSize.getHeight(), GLES30.GL_RGBA, GL_UNSIGNED_BYTE, byteBuffer);
        bitmap.copyPixelsFromBuffer(byteBuffer);
        Matrix matrix = new Matrix();
        matrix.postRotate(180);
        matrix.postScale(-1, 1.f);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
        return bitmap;
    }

    public static int loadTexture(Bitmap bitmap) {
        if (bitmap == null) return -1;
        Log.v("GLUtil", "Loading texture from bitmap...");

        final int[] textureHandle = new int[1];

        GLES30.glGenTextures(1, textureHandle, 0);
        checkGlError("glGenTextures");
        if (textureHandle[0] == 0) {
            throw new RuntimeException("Error loading texture.");
        }

        Log.v("GLUtil", "Handler: " + textureHandle[0]);

        // Bind to the texture in OpenGL
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle[0]);
        checkGlError("glBindTexture");
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);
        checkGlError("texImage2D");
//        bitmap.recycle();//TODO
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);

        Log.v("GLUtil", "Loaded texture ok");
        return textureHandle[0];
    }

    private static Bitmap loadBitmap(byte[] is) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        // By default, Android applies pre-scaling to bitmaps depending on the resolution of your device and which
        // resource folder you placed the image in. We don’t want Android to scale our bitmap at all, so to be sure,
        // we set inScaled to false.
        options.inScaled = false;

        // Read in the resource
        final Bitmap bitmap = BitmapFactory.decodeByteArray(is, 0, is.length, options);
        if (bitmap == null) {
            throw new RuntimeException("couldn't load bitmap");
        }
        return bitmap;
    }

    private static Bitmap loadBitmap(InputStream is) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        // By default, Android applies pre-scaling to bitmaps depending on the resolution of your device and which
        // resource folder you placed the image in. We don’t want Android to scale our bitmap at all, so to be sure,
        // we set inScaled to false.
        options.inScaled = false;

        // Read in the resource
        final Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
        if (bitmap == null) {
            throw new RuntimeException("couldn't load bitmap");
        }
        return bitmap;
    }


    /**
     * Utility method for debugging OpenGL calls. Provide the name of the call just after making it:
     *
     * <pre>
     * mColorHandle = GLES30.glGetUniformLocation(mProgram, &quot;vColor&quot;);
     * MyGLRenderer.checkGlError(&quot;glGetUniformLocation&quot;);
     * </pre>
     * <p>
     * If the operation is not successful, the check throws an error.
     *
     * @param glOperation - Name of the OpenGL call to check.
     */
    public static boolean checkGlError(String glOperation) {
        int glError;
        boolean error = false;
        while ((glError = GLES30.glGetError()) != GLES30.GL_NO_ERROR) {
            Log.e(TAG, glOperation + ": glError " + glError);
            error = true;
            Log.e(TAG, Thread.currentThread().getStackTrace()[3].toString());
            Log.e(TAG, Thread.currentThread().getStackTrace()[4].toString());
            Log.e(TAG, Thread.currentThread().getStackTrace()[5].toString());
            Log.e(TAG, Thread.currentThread().getStackTrace()[6].toString());

            // throw new RuntimeException(glOperation + ": glError " + error);
        }
        return error;
    }

    ///Constants
    //解码器纹理上下翻转？
    public static float[] DEFAULT_VERT_ARRAY_90 = {
            -1.0f, 1.0f, 0.0f, 0.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, -1.0f, 0.0f, 1.0f, 1.0f
    };

    public static float[] DEFAULT_VERT_ARRAY_0 = {
            -1.0f, 1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f, 1.0f,
            -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 1.0f, 0.0f
    };

    public static int[] DRAW_ORDER = {
            0, 1, 2, 1, 2, 3
    };

    public static final String COMMON_GAUSS = "" +
            "uniform vec2 imageSize;\n" +
            "uniform float blurStep;\n" +
            "const float GAUSS_WEIGHT[9]=float[9](0.2, 0.19, 0.17, 0.15, 0.13, 0.11, 0.08, 0.05, 0.02);\n" +
            "vec4 gaussianBlur(sampler2D inputTexture, vec2 textureCoordinate)\n" +
            "{\n" +
            "    vec2 unitUV         = vec2(blurStep)/imageSize;\n" +
            "    float sumWeight     = GAUSS_WEIGHT[0];\n" +
            "    vec4 sumColor       = texture(inputTexture,  textureCoordinate)*sumWeight;\n" +
            "    for(int i=-4;i<=4;i++)\n" +
            "    {\n" +
            "       for(int j=-4;j<=4;j++){\n" +
            "           vec2 coord = textureCoordinate+vec2(i,j)*unitUV;\n" +
            "           float curWeight = GAUSS_WEIGHT[j+5]*GAUSS_WEIGHT[i+5];\n" +
            "           sumColor+= texture(inputTexture,coord)*curWeight;\n" +
            "           sumWeight+= curWeight;\n" +
            "       }\n" +
            "    }\n" +
            "    return sumColor/sumWeight;\n" +
            "}\n";

}
