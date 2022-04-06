package com.galix.opentiktok.dp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import com.galix.avcore.avcore.AVAudio;
import com.galix.avcore.avcore.AVEngine;
import com.galix.avcore.gl.GLManager;
import com.galix.avcore.util.OtherUtils;
import com.galix.opentiktok.R;


public class GameActivity extends AppCompatActivity {

    private static final String TAG = GameActivity.class.getSimpleName();
    private SurfaceView mGLSurfaceView;
    private AVEngine mAVEngine;
    private Button mToggleButton;
    private Switch mBeautyButton;
    private GameRender mGameRender;
    private GameComponent mGameComponent;
    private boolean mIsBeauty = true;

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
        mGameRender = new GameRender();
        mGameComponent = new GameComponent(this,
                0,
                "/sdcard/coach.mp4",
                "/sdcard/testplayer.mp4",
                "pag/screen_effect.pag",
                "pag/player_effect.pag",
                "lut/beauty_lut.png",
                "lut/std_lut.png",
                false,
                mGameRender);
        AVAudio audio = new AVAudio(0, "/sdcard/coach.mp4", null);
        mAVEngine.addComponent(mGameComponent, null);
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
        mBeautyButton = findViewById(R.id.btn_beauty);
        mBeautyButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mIsBeauty = isChecked;
                mGameComponent.write(OtherUtils.buildMap("use_beauty", mIsBeauty));
                Log.d(TAG, "check#" + isChecked);
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