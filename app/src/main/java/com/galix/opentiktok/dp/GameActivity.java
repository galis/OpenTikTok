package com.galix.opentiktok.dp;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.galix.avcore.avcore.AVAudio;
import com.galix.avcore.avcore.AVEngine;
import com.galix.avcore.avcore.AVPag;
import com.galix.avcore.gl.GLManager;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.TimeUtils;
import com.galix.opentiktok.R;

import java.io.IOException;


public class GameActivity extends AppCompatActivity {

    private static final String TAG = GameActivity.class.getSimpleName();
    private SurfaceView mGLSurfaceView;
    private AVEngine mAVEngine;
    private Button mToggleButton;
    private Switch mBeautyButton;
    private TextView mEngineInfo;
    private GameRender mGameRender;
    private GameComponent mGameComponent;
    private boolean mIsBeauty = true;

    public static void start(Context context) {
        context.startActivity(new Intent(context, GameActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtil.log("Game#onCreate()");
        setContentView(R.layout.activity_game);
        LogUtil.setLogLevel(LogUtil.LogLevel.FULL);
        mAVEngine = AVEngine.getVideoEngine();
        mAVEngine.configure(findViewById(R.id.glsurface_game));
        mAVEngine.setCanvasType("原始", null);
        mAVEngine.create();
        mGameRender = new GameRender();
        try {
            mGameComponent = new GameComponent(this,
                    0,
                    "/sdcard/coach.mp4",
//                    "/data/data/com.galix.opentiktok/cache/test2.mp4",
                    "/sdcard/testplayer.mp4",
                    "pag/player_effect.pag",
                    BitmapFactory.decodeStream(getAssets().open("lut/beauty_lut.png")),
                    BitmapFactory.decodeStream(getAssets().open("lut/std_lut.png")),
                    false,
                    mGameRender);
        } catch (IOException e) {
            e.printStackTrace();
        }
        AVAudio audio = new AVAudio(0, "/sdcard/coach.mp4", null);
        mAVEngine.addComponent(mGameComponent, new AVEngine.EngineCallback() {
            @Override
            public void onCallback(Object... args1) {
                mAVEngine.setCanvasType("原始", new AVEngine.EngineCallback() {
                    @Override
                    public void onCallback(Object... args1) {
                        findViewById(R.id.glsurface_game).post(new Runnable() {
                            @Override
                            public void run() {
                                Size size = (Size) args1[0];
                                findViewById(R.id.glsurface_game).getLayoutParams().width = size.getWidth();
                                findViewById(R.id.glsurface_game).getLayoutParams().height = size.getHeight();
                                findViewById(R.id.glsurface_game).requestLayout();
                            }
                        });
                    }
                });
            }
        });
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
                mGameComponent.write(TimeUtils.BuildMap("use_beauty", mIsBeauty));
//                mAVEngine.fastSeek(mAVEngine.getMainClock());
                Log.d(TAG, "check#" + isChecked);
            }
        });

        mEngineInfo = findViewById(R.id.tv_engine_info);

//        mAVEngine.playPag(this, "pag/screen_effect.pag");
//        mAVEngine.playPag(this, "pag/animation_border.pag");
//        mAVEngine.playPag(this, "pag/sport_scoring_perfect.pag");
//
//
//        mAVEngine.playPag(this, "pag/screen_effect.pag");
//        mAVEngine.playPag(this, "pag/animation_border.pag");
//        mAVEngine.playPag(this, "pag/sport_scoring_perfect.pag");
//
        AVPag testPag1 = mAVEngine.playPag(this, "pag/screen_effect.pag");
//        AVPag testPag2 = mAVEngine.playPag(this, "pag/animation_border.pag");
//        AVPag testPag3 = mAVEngine.playPag(this, "pag/sport_scoring_perfect.pag");
//        testPag2.setMatrix(MathUtils.calMatrix(new Rect(0, 0, 1920, 1080), new Rect(
//                1920 / 2 - 1280 / 2,
//                1080 / 2 - 720 / 2,
//                1920 / 2 + 1280 / 2,
//                1080 / 2 + 720 / 2
//        )));
//
        for (int i = 1; i < 20; i++) {
            mBeautyButton.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mAVEngine.playPag(testPag1);
                }
            }, i * 10000);
        }

        mAVEngine.setOnFrameUpdateCallback(new AVEngine.EngineCallback() {
            @Override
            public void onCallback(Object... args1) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mEngineInfo.setText(TimeUtils.LogStr("pag"));
                    }
                });
            }
        });

    }

    @Override
    protected void onRestart() {
        super.onRestart();
        LogUtil.log("Game#onRestart()");
    }

    @Override
    protected void onStart() {
        super.onStart();
        LogUtil.log("Game#onStart()");
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogUtil.log("Game#onStop()");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.log("Game#onDestroy()");
        mAVEngine.release();
        GLManager.getManager().unInstallContext();
    }
}