package com.galix.opentiktok.dp;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.galix.avcore.avcore.AVAudio;
import com.galix.avcore.avcore.AVEngine;
import com.galix.avcore.avcore.AVVideo;
import com.galix.opentiktok.R;

import org.opencv.core.Rect;

public class GameActivity extends AppCompatActivity {

    private GLSurfaceView mGLSurfaceView;
    private AVEngine mAVEngine;
    private Button mToggleButton;
    private Button mResetButton;
    private DpRender mDpRender;

    public static void start(Context context) {
        context.startActivity(new Intent(context, GameActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        mGLSurfaceView = findViewById(R.id.glsurface_game);
        mAVEngine = AVEngine.getVideoEngine();
        mAVEngine.configure(mGLSurfaceView);
        mDpRender = new DpRender();
        mDpRender.write(DpRender.buildMap(new Object[]{
                DpRender.KEY_PLAYER_LUT, BitmapFactory.decodeResource(getResources(), R.drawable.srclut),
                DpRender.KEY_PLAYER_ROI, new Rect(),
                DpRender.KEY_PLAYER_MASK_ROI, new Rect(276, 234, 1059, 845),
        }));
        DpComponent.context = this;
        DpRender.context = this;
        DpComponent videoCom1 = new DpComponent(0, "/sdcard/testplayer.mp4", mAVEngine.nextValidTexture(),
                "/sdcard/testplayer.mp4", mAVEngine.nextValidTexture(), mDpRender);
        AVAudio audio = new AVAudio(0, "/sdcard/coach.mp4", null);
        mAVEngine.addComponent(videoCom1, null);
        mAVEngine.addComponent(audio, null);
        mToggleButton = findViewById(R.id.btn_toggle_play_pause);
        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAVEngine.togglePlayPause();
            }
        });
        findViewById(R.id.btn_toggle_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAVEngine.seek(true);
                mAVEngine.seek(0);
                mAVEngine.seek(false);
            }
        });
    }
}