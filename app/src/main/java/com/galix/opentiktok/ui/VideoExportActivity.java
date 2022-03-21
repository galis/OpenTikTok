package com.galix.opentiktok.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVEngine;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.util.VideoUtil;
import com.galix.opentiktok.R;

public class VideoExportActivity extends BaseActivity {

    private ExportProgressView mProgressView;
    private AVEngine mAVEngine;
    private Bitmap mBackGround;
    private int mProgress = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_export);
        getSupportActionBar().setTitle(R.string.export_video);
        mAVEngine = AVEngine.getVideoEngine();
        mProgressView = findViewById(R.id.progress_export);
        AVVideo firstVideo = (AVVideo) mAVEngine.findComponents(AVComponent.AVComponentType.VIDEO, 0).get(0);
        String imgPath = VideoUtil.getThumbJpg(this, firstVideo.getPath(), 0);
        mBackGround = BitmapFactory.decodeFile(imgPath);
        mProgressView.post(new Runnable() {
            @Override
            public void run() {
                mProgressView.setProgress(mBackGround, mProgress);
                mProgress++;
                if (mProgress <= 100) {
                    mProgressView.postDelayed(this,30);
                }
            }
        });
    }
}
