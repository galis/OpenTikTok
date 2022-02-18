package com.galix.opentiktok.ui;

import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.galix.opentiktok.R;
import com.galix.opentiktok.util.VideoUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;


/**
 * 资源筛选Activity
 * 固定从/sdcard开始搜索
 *
 * @Author:Galis
 * @Date:2022.01.16
 */
public class VideoPickActivity extends AppCompatActivity {

    private static final String TAG = VideoPickActivity.class.getSimpleName();
    private HandlerThread mLoadThread;
    private Handler mLoadHandler;
    private ArrayList<VideoUtil.FileEntry> mFileCache;
    private RecyclerView mRecyclerView;
    private ContentLoadingProgressBar mProgressBar;
    private LinkedList<VideoUtil.FileEntry> mPickList;

    public static void start(Context context) {
        Intent intent = new Intent(context, VideoPickActivity.class);
        context.startActivity(intent);
    }

    private static class ImageViewHolder extends RecyclerView.ViewHolder {

        public ImageView imageView;
        public ImageView pickBtn;
        public TextView textView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_pick);
        mProgressBar = findViewById(R.id.pb_loading);
        mProgressBar.hide();
        mPickList = new LinkedList<>();
        mRecyclerView = findViewById(R.id.recyclerview_preview);
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        mRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View layout = getLayoutInflater().inflate(R.layout.layout_video_info, parent, false);
                ImageViewHolder imageViewHolder = new ImageViewHolder(layout);
                imageViewHolder.itemView.getLayoutParams().width = parent.getMeasuredWidth() / 2;
                imageViewHolder.itemView.getLayoutParams().height = parent.getMeasuredWidth() / 2;
                imageViewHolder.imageView = layout.findViewById(R.id.image_video_thumb);
                imageViewHolder.pickBtn = layout.findViewById(R.id.image_pick);
                imageViewHolder.textView = layout.findViewById(R.id.text_video_info);
                return imageViewHolder;
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ImageViewHolder imageViewHolder = (ImageViewHolder) holder;
                imageViewHolder.imageView.setImageBitmap(mFileCache.get(position).thumb);
                VideoUtil.FileEntry fileEntry = mFileCache.get(position);
                imageViewHolder.pickBtn.setSelected(
                        mPickList.contains(fileEntry)
                );
                imageViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mPickList.contains(fileEntry)) {
                            mPickList.remove(fileEntry);
                        } else {
                            mPickList.add(fileEntry);
                        }
                        notifyDataSetChanged();
                    }
                });
                imageViewHolder.textView.setText(
                        String.format("width:%d\nheight:%d\nduration:%ds\npath:%s",
                                fileEntry.width, fileEntry.height, fileEntry.duration / 1000000, fileEntry.path));
            }

            @Override
            public int getItemCount() {
                return mFileCache.size();
            }

        });

        //Actionbar
        getSupportActionBar().setTitle(R.string.choose_video);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);


        //创建线程开始加载
        mLoadThread = new HandlerThread("LoadResource");
        mLoadThread.start();
        mLoadHandler = new Handler(mLoadThread.getLooper());
        mLoadHandler.post(() -> {
            long now1 = System.currentTimeMillis();
            mFileCache = new ArrayList<>();
            String path = "/sdcard";
            File dir = new File(path);
            if (!dir.exists()) {
                return;
            }
            File[] mp4List = dir.listFiles((dir1, name) -> name.endsWith(".mp4"));
            for (File mp4 : mp4List) {
                MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                mediaMetadataRetriever.setDataSource(mp4.getAbsolutePath());
                if (Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)) > 0) {
                    Log.d(TAG, mp4.getAbsolutePath());
                    VideoUtil.FileEntry fileEntry = new VideoUtil.FileEntry();
                    fileEntry.duration = Integer.parseInt(mediaMetadataRetriever.extractMetadata
                            (MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
                    fileEntry.thumb = mediaMetadataRetriever.getFrameAtIndex(0);
                    fileEntry.width = Integer.parseInt(mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    fileEntry.height = Integer.parseInt(mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                    fileEntry.path = mp4.getAbsolutePath();
                    fileEntry.adjustPath = VideoUtil.getAdjustGopVideoPath(VideoPickActivity.this, fileEntry.path);
                    mFileCache.add(fileEntry);
                    getWindow().getDecorView().post(new Runnable() {
                        @Override
                        public void run() {
                            mRecyclerView.getAdapter().notifyDataSetChanged();
                        }
                    });
                }
                mediaMetadataRetriever.release();
            }
            long now2 = System.currentTimeMillis();
            Log.d(TAG, "Filter mp4 on /sdcard : Use#" + (now2 - now1));
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return super.onSupportNavigateUp();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_video_pick, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_done:
                handleVideo();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mLoadThread != null) {
            try {
                mLoadHandler.getLooper().quit();
                mLoadThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleVideo() {
        //跳转前先处理资源
        if (!mProgressBar.isShown()) {
            mProgressBar.show();
        }
        VideoUtil.processVideo(VideoPickActivity.this, mPickList, 3, 10000000, msg -> {
            VideoEditActivity.start(VideoPickActivity.this);
            finish();
            return true;
        });
    }
}