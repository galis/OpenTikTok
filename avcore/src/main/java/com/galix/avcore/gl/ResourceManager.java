package com.galix.avcore.gl;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.util.GLUtil;
import com.galix.avcore.util.IOUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * OpenGL数据管理
 *
 * @Author: Galis
 * @Date:2022.03.28
 */
public class ResourceManager {
    private static ResourceManager gManager;
    private WeakReference<Context> mContext;
    private Map<String, Bitmap> mBitmapMap = new HashMap<>();
    private Map<String, GLTexture> mTextureMap = new HashMap<>();

    private ResourceManager() {
    }

    public static ResourceManager getManager() {
        if (gManager == null) {
            synchronized (ResourceManager.class) {
                if (gManager == null) {
                    gManager = new ResourceManager();
                    if (gManager.mContext == null) {
                        try {
                            Class activityThreadClass = Class.forName("android.app.ActivityThread");
                            Method currentMethod = activityThreadClass.getMethod("currentActivityThread");
                            Object activityThread = currentMethod.invoke(activityThreadClass);
                            Method method = activityThreadClass.getDeclaredMethod("getApplication");
                            Application application = (Application) method.invoke(activityThread);
                            gManager.mContext = new WeakReference<>(application.getApplicationContext());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return gManager;
                }
            }
        }
        return gManager;
    }

    public String loadGLSL(int glsl) {
        if (mContext == null || mContext.get() == null) return null;
        try {
            return IOUtils.readStr(mContext.get().getResources().openRawResource(glsl));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Bitmap loadBitmap(String assetPath) {
        if (mContext == null || mContext.get() == null) return null;
        if (mBitmapMap.containsKey(assetPath)) {
            return mBitmapMap.get(assetPath);
        }
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(mContext.get().getAssets().open(assetPath));
            mBitmapMap.put(assetPath, bitmap);
            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public GLTexture loadTexture(String path) {
        Bitmap textureBitmap = loadBitmap(path);
        if (textureBitmap == null) {
            return GLTexture.GL_EMPTY_TEXTURE;
        }
        if (mTextureMap.containsKey(path)) {
            return mTextureMap.get(path);
        }
        GLTexture glTexture = new GLTexture();
        GLUtil.loadTexture(glTexture, textureBitmap);
        mTextureMap.put(path, glTexture);
        return glTexture;
    }

    public GLTexture loadTexture(String path, GLTexture glTexture) {
        Bitmap textureBitmap = loadBitmap(path);
        if (textureBitmap == null) {
            return GLTexture.GL_EMPTY_TEXTURE;
        }
        GLUtil.loadTexture(glTexture, textureBitmap);
        return glTexture;
    }

    public String getCacheDir() {
        if (mContext.get() == null) {
            return "/sdcard";
        }
        return mContext.get().getCacheDir().getAbsolutePath();
    }

    public String getPackageCodePath() {
        if (mContext.get() == null) {
            return "/sdcard";
        }
        return mContext.get().getPackageCodePath();
    }

    public Context getContext() {
        return mContext.get();
    }

    public void release() {
        mBitmapMap.clear();
        mTextureMap.clear();
    }

}
