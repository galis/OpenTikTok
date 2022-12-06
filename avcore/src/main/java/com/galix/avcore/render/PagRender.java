package com.galix.avcore.render;

import android.text.TextUtils;
import android.util.Size;
import android.view.View;
import android.widget.TextView;

import com.galix.avcore.avcore.AVFrame;
import com.galix.avcore.avcore.AVPag;
import com.galix.avcore.render.filters.GLTexture;
import com.galix.avcore.render.filters.PagFilter;

import org.libpag.PAGFile;
import org.libpag.PAGView;

import java.util.HashMap;
import java.util.Map;

public class PagRender implements IVideoRender {

    private GLTexture mLastTexture;
    private Map<String, Object> mConfig = new HashMap<>();
    private Size mSurfaceSize = new Size(1920, 1080);
    private PAGView mPagView;

    public PagRender(PAGView pagView) {
        mPagView = pagView;
    }

    @Override
    public GLTexture getOutTexture() {
        return null;
    }

    @Override
    public boolean isOpen() {
        return mPagView != null && mPagView.getPath() != null;
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    @Override
    public void write(Map<String, Object> config) {
    }

    @Override
    public void render(AVFrame avFrame) {
        if (mPagView != null) {
            if (!avFrame.isValid()) {
                mPagView.post(new Runnable() {
                    @Override
                    public void run() {
                        mPagView.setVisibility(View.GONE);
                    }
                });
                return;
            }
            mPagView.post(new Runnable() {
                @Override
                public void run() {
                    mPagView.setVisibility(View.VISIBLE);
                }
            });
            AVPag avPag = (AVPag) avFrame.getExt();
            if (TextUtils.isEmpty(mPagView.getPath())) {
                mPagView.setPath(avPag.getPath());
            }
            mPagView.setProgress((avFrame.getPts() - avPag.getEngineStartTime()) * 1.0f / avPag.getDuration());//不优雅。
            mPagView.flush();
        }
    }
}
