package com.galix.avcore.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public final class GLUtil {

    private static final String TAG = "GLUtil";

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
        Log.d("GLUtil", "Shader compilation info: " + GLES30.glGetShaderInfoLog(shader));
        if (compiled[0] == 0) {
            Log.e("GLUtil", "Shader error: " + GLES30.glGetShaderInfoLog(shader) + "\n" + shaderCode);
            GLES30.glDeleteShader(shader);
        }

        return shader;
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
}
