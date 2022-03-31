package com.galix.opentiktok.dp;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Size;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.galix.avcore.avcore.AVAudio;
import com.galix.avcore.avcore.AVEngine;
import com.galix.avcore.gl.GLManager;
import com.galix.avcore.util.OtherUtils;
import com.galix.opentiktok.R;


public class GameActivity extends AppCompatActivity {

    private SurfaceView mGLSurfaceView;
    private AVEngine mAVEngine;
    private Button mToggleButton;
    private Button mResetButton;
    private DPFastRender mDpRender;

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
        mAVEngine.create();
        mDpRender = new DPFastRender();
        mDpRender.write(OtherUtils.buildMap(
                "lut", BitmapFactory.decodeStream(getResources().openRawResource(R.raw.std_lut))
//                "lut", BitmapFactory.decodeStream(getResources().openRawResource(R.raw.test_lut))//标准默认。
        ));
        DpComponent.context = this;
        DpComponent videoCom1 = new DpComponent(0, "/sdcard/coach.mp4",
                "/sdcard/testplayer.mp4", mDpRender);
        AVAudio audio = new AVAudio(0, "/sdcard/coach.mp4", null);
        mAVEngine.addComponent(videoCom1, null);
        mAVEngine.addComponent(audio, null);
        GLManager.getManager().installContext(this);

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
                mAVEngine.fastSeek(0);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAVEngine.release();
        GLManager.getManager().unInstallContext();
    }
}