package com.galix.opentiktok.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.util.Printer;
import android.util.Size;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVEngine;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.avcore.ThreadManager;
import com.galix.avcore.util.FileUtils;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.VideoUtil;
import com.galix.opentiktok.R;

import java.io.File;

/**
 * 视频导出Activity
 *
 * @Author: Galis
 * @Date:2022.03.21
 */
public class VideoExportActivity extends BaseActivity {

    private static final String TAG = VideoExportActivity.class.getSimpleName();
    private ExportProgressView mProgressView;
    private AVEngine mAVEngine;
    private Bitmap mBackGround;
    private int mProgress = 0;
    private boolean mIsInterrupt = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_export);
        getSupportActionBar().setTitle(R.string.export_video);
        mAVEngine = AVEngine.getVideoEngine();
        mProgressView = findViewById(R.id.progress_export);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAVEngine.findComponents(AVComponent.AVComponentType.VIDEO, 0).isEmpty()) {
            Log.d(TAG, "findComponents empty");
            return;
        }
        AVVideo firstVideo = (AVVideo) mAVEngine.findComponents(AVComponent.AVComponentType.VIDEO, 0).get(0);
        String imgPath = VideoUtil.getThumbJpg(this, firstVideo.getPath(), 0);
        mBackGround = BitmapFactory.decodeFile(imgPath);
        mAVEngine.getVideoState().mTargetGop = 30;
        mAVEngine.getVideoState().mTargetAb = 44100;
        mAVEngine.getVideoState().mTargetVb = (int) (2.5 * 1024 * 1024);
        mAVEngine.getVideoState().mTargetPath = FileUtils.getCompositeDir(this) + File.separator + "composite" + System.currentTimeMillis() + ".mp4";
        mAVEngine.getVideoState().hasAudio = true;
        mAVEngine.getVideoState().hasVideo = true;
        mAVEngine.getVideoState().readyAudio = false;
        mAVEngine.getVideoState().readyVideo = false;
        mAVEngine.getVideoState().mTargetSize = new Size(640, 640);
        mAVEngine.getVideoState().mBgColor = Color.RED;
        Looper.getMainLooper().setMessageLogging(new Printer() {
            @Override
            public void println(String x) {
                LogUtil.logEngine("MainLooper#" + x);
            }
        });
        mAVEngine.compositeMp4(null, new AVEngine.EngineCallback() {
            @Override
            public void onCallback(Object... args1) {
                if (mProgress != (int) args1[0]) {
                    mProgress = (int) args1[0];
                    Log.d(TAG, "onCallback#progress#" + mProgress);
                    mProgressView.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "onCallback#setProgress#" + mProgress);
                            mProgressView.setProgress(mBackGround, mProgress);
                            if (mProgress == 100) {
                                ((TextView) findViewById(R.id.textview_tip)).setText(R.string.export_success);
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
