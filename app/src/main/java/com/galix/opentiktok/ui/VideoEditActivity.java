package com.galix.opentiktok.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.galix.avcore.avcore.AVAudio;
import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVEngine;
import com.galix.avcore.avcore.AVSticker;
import com.galix.avcore.avcore.AVTransaction;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.avcore.AVWord;
import com.galix.avcore.render.ImageViewRender;
import com.galix.avcore.render.TextRender;
import com.galix.avcore.render.TransactionRender;
import com.galix.avcore.util.GLUtil;
import com.galix.avcore.util.GestureUtils;
import com.galix.avcore.util.GifDecoder;
import com.galix.avcore.util.IOUtils;
import com.galix.avcore.util.VideoUtil;
import com.galix.opentiktok.R;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static androidx.recyclerview.widget.RecyclerView.HORIZONTAL;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.START;

/**
 * 视频编辑界面
 *
 * @Author Galis
 * @Date 2022.01.15
 */
public class VideoEditActivity extends BaseActivity {

    private static final String TAG = VideoEditActivity.class.getSimpleName();
    private static final int THUMB_SLOT_WIDTH = 60;

    private LinkedList<ThumbInfo> mThumbsList;
    private LinkedList<Integer> mStickerList;//贴纸
    private SurfaceView mSurfaceView;
    private RecyclerView mTabRecyclerView;
    private CustomHorizontalScrollView mThumbDragRecyclerView;
    private RecyclerView mStickerRecyclerView;

    private ImageView mStickerView;
    private EditText mEditTextView;
    private GifDecoder mGifDecoder;
    private TextView mWordView;
    private TextView mTimeInfo;
    private ImageView mPlayBtn;
    private ImageView mFullScreenBtn;
    private int mScrollX = 0;
    private AVEngine mAVEngine;
    private Runnable mScrollViewRunnable;

    //底部ICON info
    private static final int[] TAB_INFO_LIST = {
            R.drawable.icon_video_cut, R.string.tab_cut,
            R.drawable.icon_adjust, R.string.tab_audio,
            R.drawable.icon_adjust, R.string.tab_text,
            R.drawable.icon_adjust, R.string.tab_sticker,
            R.drawable.icon_adjust, R.string.tab_inner_picture,
            R.drawable.icon_adjust, R.string.tab_magic,
            R.drawable.icon_filter, R.string.tab_filter,
            R.drawable.icon_adjust, R.string.tab_ratio,
            R.drawable.icon_background, R.string.tab_background,
            R.drawable.icon_adjust, R.string.tab_adjust
    };

    public static void start(Context ctx) {
        Intent intent = new Intent(ctx, VideoEditActivity.class);
        ctx.startActivity(intent);
    }


    private class ThumbInfo {
        public int type;
        public String imgPath;
    }

    private class ImageViewHolder extends RecyclerView.ViewHolder {
        public ImageView imageView;
        public TextView textView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    private class ThumbViewHolder extends RecyclerView.ViewHolder {
        public View view1;
        public View view2;

        public ThumbViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }


    //UI回调
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_edit);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        mStickerView = findViewById(R.id.image_sticker);
        GestureUtils.setupView(mStickerView, new Rect(0, 0, 1920, 1080));
        mEditTextView = findViewById(R.id.edit_tip);
        GestureUtils.setupView(mEditTextView, new Rect(0, 0, 1920, 1080));
        mWordView = findViewById(R.id.tv_word);
        mSurfaceView = findViewById(R.id.glsurface_preview);
        mTimeInfo = findViewById(R.id.text_duration);
        mPlayBtn = findViewById(R.id.image_play);
        mPlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAVEngine.togglePlayPause();
                freshUI();
            }
        });
        mFullScreenBtn = findViewById(R.id.image_fullscreen);
        mTabRecyclerView = findViewById(R.id.recyclerview_tab_mode);
        mTabRecyclerView.setLayoutManager(new LinearLayoutManager(this, HORIZONTAL, false));
        mTabRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View layout = getLayoutInflater().inflate(R.layout.layout_tab_item, parent, false);
                ImageViewHolder imageViewHolder = new ImageViewHolder(layout);
                imageViewHolder.itemView.getLayoutParams().width = (int) (60 * getResources().getDisplayMetrics().density);
                imageViewHolder.itemView.getLayoutParams().height = (int) (60 * getResources().getDisplayMetrics().density);
                imageViewHolder.imageView = layout.findViewById(R.id.image_video_thumb);
                imageViewHolder.textView = layout.findViewById(R.id.text_video_info);
                return imageViewHolder;
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ImageViewHolder imageViewHolder = (ImageViewHolder) holder;
                imageViewHolder.imageView.setImageResource(TAB_INFO_LIST[2 * position]);
                imageViewHolder.textView.setText(TAB_INFO_LIST[2 * position + 1]);
                imageViewHolder.itemView.setOnClickListener(v -> {
                    int index = TAB_INFO_LIST[2 * position + 1];
                    if (index == R.string.tab_sticker) {
                        mStickerRecyclerView.setVisibility(View.VISIBLE);
                    } else if (index == R.string.tab_text) {
                        mAVEngine.addComponent(new AVWord(mAVEngine.getMainClock(),
                                new TextRender(mEditTextView)), null);
                    } else if (index == R.string.tab_ratio) {
                        ViewGroup view = findViewById(R.id.view_ratio_tablist);
                        ((RatioTabListView) view.getChildAt(0)).buildView(1920, 1080, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                RatioTabListView.RatioInfo info = (RatioTabListView.RatioInfo) v.getTag();
                                if (info == null) {
                                    view.setVisibility(View.GONE);
                                    return;
                                }
                                mAVEngine.setCanvasSize(calCanvasSize(info.text));
                                Toast.makeText(getBaseContext(), info.text, Toast.LENGTH_SHORT).show();
                            }
                        });
                        view.setVisibility(View.VISIBLE);
                    } else if (index == R.string.tab_background) {
                        ViewGroup view = findViewById(R.id.view_background_tablist);
                        ((BackgroundTabListView) view.getChildAt(0)).buildView(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (v.getTag() == null) {
                                    view.setVisibility(View.GONE);
                                    return;
                                }
                                mAVEngine.setBgColor((Integer) v.getTag());
                            }
                        });
                        view.setVisibility(View.VISIBLE);
                    } else {
                        mStickerRecyclerView.setVisibility(View.GONE);
                        Toast.makeText(VideoEditActivity.this, "待实现", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public int getItemCount() {
                return TAB_INFO_LIST.length / 2;
            }
        });
        mTabRecyclerView.getAdapter().notifyDataSetChanged();

        mThumbDragRecyclerView = findViewById(R.id.recyclerview_drag_thumb);
        mThumbDragRecyclerView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                mAVEngine.seek((long) (1000000.f / (THUMB_SLOT_WIDTH * getResources().getDisplayMetrics().density) * scrollX));
                Log.d(TAG, "ss##" + scrollX + "#" + oldScrollX);
            }
        });
        mScrollViewRunnable = new Runnable() {
            @Override
            public void run() {
                mAVEngine.seek(false);
                Log.d(TAG, "ss##seek(false)");
            }
        };
        mThumbDragRecyclerView.setScrollStateListener(new CustomHorizontalScrollView.ScrollStateChangeListener() {
            @Override
            public void onScrollStateChange(CustomHorizontalScrollView.State state) {
                if (state == CustomHorizontalScrollView.State.DOWN) {
                    mThumbDragRecyclerView.removeCallbacks(mScrollViewRunnable);
                    mAVEngine.seek(true);
                } else {
                    mThumbDragRecyclerView.postDelayed(mScrollViewRunnable, 400);
                }
            }
        });
        mStickerList = new LinkedList<>();
        mStickerList.add(R.raw.aini);
        mStickerList.add(R.raw.buyuebuyue);
        mStickerList.add(R.raw.burangwo);
        mStickerList.add(R.raw.dengliao);
        mStickerList.add(R.raw.gandepiaoliang);
        mStickerList.add(R.raw.nizabushagntian);
        mStickerRecyclerView = findViewById(R.id.recyclerview_sticker);
        mStickerRecyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        mStickerRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ImageView view = new ImageView(parent.getContext());
                view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                view.setLayoutParams(new RecyclerView.LayoutParams(parent.getMeasuredWidth() / 4,
                        parent.getMeasuredWidth() / 4));
                return new ThumbViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Glide.with(VideoEditActivity.this)
                        .load(mStickerList.get(position))
                        .asGif()
                        .into((ImageView) holder.itemView);
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mStickerView.setVisibility(View.VISIBLE);
                        mAVEngine.addComponent(new AVSticker(mAVEngine.getMainClock(), getResources().openRawResource(mStickerList.get(position)),
                                new ImageViewRender(mStickerView)), null);
                        mStickerRecyclerView.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return mStickerList.size();
            }
        });

        //初始化Thumb信息
        mAVEngine = AVEngine.getVideoEngine();
        mAVEngine.configure(mSurfaceView);
        mAVEngine.create();
        mAVEngine.setCanvasSize(calCanvasSize("原始"));
        long startTime = 0;
        LinearLayout linearLayout = findViewById(R.id.linear_parent);
        View head = new View(this);
        head.setLayoutParams(new LinearLayout.LayoutParams(getWindowManager().getCurrentWindowMetrics().getBounds().width() / 2, ViewGroup.LayoutParams.MATCH_PARENT));
        linearLayout.addView(head);
        View tail = new View(this);
        tail.setLayoutParams(new LinearLayout.LayoutParams(getWindowManager().getCurrentWindowMetrics().getBounds().width() / 2, ViewGroup.LayoutParams.MATCH_PARENT));
        for (VideoUtil.FileEntry fileEntry : VideoUtil.mTargetFiles) {
            AVVideo video = new AVVideo(true, startTime, fileEntry.adjustPath, null);
            AVAudio audio = new AVAudio(startTime, fileEntry.adjustPath, null);
            LinkedList<AVComponent> components = new LinkedList<>();
            components.add(video);
            components.add(audio);
            ComponentView view = new ComponentView.Builder(this)
                    .setComponents(components)
                    .setPaddingColor(Color.BLACK)
                    .setTileSize((int) (THUMB_SLOT_WIDTH * getResources().getDisplayMetrics().density))
                    .setPaddingLeftRight(0)
                    .setPaddingTopBottom(0)
                    .setClipCallback(new ComponentView.ClipCallback() {
                        @Override
                        public void onClip(ComponentView view, LinkedList<AVComponent> avComponents, Rect src, Rect dst) {
                            mAVEngine.changeComponent(avComponents, src, dst, new AVEngine.EngineCallback() {
                                @Override
                                public void onCallback(Object[] args1) {
                                    view.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            view.buildViews();
                                        }
                                    });
                                }
                            });
                            mAVEngine.seek(true);
                            mAVEngine.seek(mAVEngine.getClock(mAVEngine.getVideoState().extClock));
                            mAVEngine.seek(false);
                        }
                    })
                    .build();
            startTime += fileEntry.duration;
            linearLayout.addView(view);

            mAVEngine.addComponent(video, new AVEngine.EngineCallback() {
                @Override
                public void onCallback(Object[] args1) {
                    view.post(new Runnable() {
                        @Override
                        public void run() {
                            view.buildViews();
                        }
                    });
                }
            });
            mAVEngine.addComponent(audio, new AVEngine.EngineCallback() {
                @Override
                public void onCallback(Object[] args1) {
                    view.post(new Runnable() {
                        @Override
                        public void run() {
                            view.buildViews();
                        }
                    });
                }
            });

        }
        linearLayout.addView(tail);
        mAVEngine.setOnFrameUpdateCallback(new AVEngine.EngineCallback() {
            @Override
            public void onCallback(Object[] args1) {
                freshUI();
            }
        });
    }

    private Size calCanvasSize(String text) {
        int width = getWindowManager().getCurrentWindowMetrics().getBounds().width();
        int height = (int) (350 * getResources().getDisplayMetrics().density);
        if (text.equalsIgnoreCase("原始")) {
            height = (int) (width * VideoUtil.mTargetFiles.get(0).height * 1.0f /
                    VideoUtil.mTargetFiles.get(0).width);
        } else if (text.equalsIgnoreCase("4:3")) {
            height = (int) (width * 3.0f / 4.0f);
        } else if (text.equalsIgnoreCase("3:4")) {
            width = (int) (height * 3.f / 4.0f);
        } else if (text.equalsIgnoreCase("1:1")) {
            width = height;
        } else if (text.equalsIgnoreCase("16:9")) {
            height = (int) (width * 9.0f / 16.0f);
        } else if (text.equalsIgnoreCase("9:16")) {
            width = (int) (height * 9.0f / 16.0f);
        }
        return new Size(width, height);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAVEngine.release();
        Log.d(TAG, "onDestroy");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_video_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.action_export:
                VideoExportActivity.start(this, VideoExportActivity.class);
                break;
            case R.id.action_pixel:
                test();
                break;
            default:
                break;
        }
        return true;
    }

    private void test() {
        List<AVComponent> components = mAVEngine.findComponents(AVComponent.AVComponentType.VIDEO, -1);
        if (components.size() == 2) {
            AVVideo video0 = (AVVideo) components.get(0);
            AVVideo video1 = (AVVideo) components.get(1);
            video1.setEngineStartTime(video1.getEngineStartTime() - 1000000);
            TransactionRender render = new TransactionRender();
            TransactionRender.TransactionConfig config = new TransactionRender.TransactionConfig();
            config.surfaceSize = mAVEngine.getVideoState().mTargetSize;
            try {
                config.vs = IOUtils.readStr(getResources().openRawResource(R.raw.alpha_transaction_vs));
                config.fs = IOUtils.readStr(getResources().openRawResource(R.raw.alpha_transaction_fs));
                render.write(config);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mAVEngine.addComponent(new AVTransaction(video1.getEngineStartTime(), 0, video0, video1, render), null);
            freshUI();
        }
    }

    //不同线程都可以刷新UI
    private Runnable mScrollRunnable = new Runnable() {
        @Override
        public void run() {
            int correctScrollX = (int) ((THUMB_SLOT_WIDTH * getResources().getDisplayMetrics().density) / 1000000.f * AVEngine.getVideoEngine().getMainClock());
            int currentScrollX = mThumbDragRecyclerView.getScrollX();
            if (currentScrollX != correctScrollX) {
                mThumbDragRecyclerView.scrollBy((correctScrollX - currentScrollX > 0 ? 1 : -1) * 5, 0);
                mThumbDragRecyclerView.post(this);
            }
        }
    };

    private void freshUI() {
        getWindow().getDecorView().post(() -> {
            AVEngine.VideoState mVideoState = AVEngine.getVideoEngine().getVideoState();
            if (mVideoState != null) {
                long positionInMS = (AVEngine.getVideoEngine().getMainClock() + 999) / 1000;
                long durationInMS = (mVideoState.durationUS + 999) / 1000;
                mTimeInfo.setText(String.format("%02d:%02d:%03d / %02d:%02d:%03d",
                        positionInMS / 1000 / 60 % 60, positionInMS / 1000 % 60, positionInMS % 1000,
                        durationInMS / 1000 / 60 % 60, durationInMS / 1000 % 60, durationInMS % 1000));
                mPlayBtn.setImageResource(mVideoState.status == START ? R.drawable.icon_video_pause : R.drawable.icon_video_play);
                if (mVideoState.status == START) {
                    mThumbDragRecyclerView.post(mScrollRunnable);
                }
            }
        });
    }


}