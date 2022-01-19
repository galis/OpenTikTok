package com.galix.opentiktok;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.galix.opentiktok.util.VideoUtil;

import java.io.File;
import java.util.ArrayList;


/**
 * 资源筛选Activity
 * 固定从/sdcard开始搜索
 *
 * @Author:Galis
 * @Date:2022.01.16
 */
public class ResourceChooseActivity extends AppCompatActivity {

    private static final String TAG = ResourceChooseActivity.class.getSimpleName();
    private HandlerThread mLoadThread;
    private Handler mLoadHandler;
    private ArrayList<FileEntry> mFileCache;
    private RecyclerView mRecyclerView;

    private static class ImageViewHolder extends RecyclerView.ViewHolder {

        public ImageView imageView;
        public TextView textView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    private static class FileEntry {
        public String path;
        public long duration;
        public long frameRate;
        public int width;
        public int height;
        public Bitmap thumb;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resource_choose);
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
                imageViewHolder.textView = layout.findViewById(R.id.text_video_info);
                return imageViewHolder;
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ImageViewHolder imageViewHolder = (ImageViewHolder) holder;
                imageViewHolder.imageView.setImageBitmap(mFileCache.get(position).thumb);
                FileEntry fileEntry = mFileCache.get(position);
                imageViewHolder.textView.setText(
                        String.format("width:%d\nheight:%d\nduration:%ds\npath:%s",
                                fileEntry.width, fileEntry.height, fileEntry.duration, fileEntry.path));
                imageViewHolder.itemView.setOnClickListener(v -> {
                    //跳转前先处理资源
                    VideoUtil.mTargetPath = "/data/data/com.galix.opentiktok/cache/output.mp4";
                    ArrayList<File> files = new ArrayList<>();
                    files.add(new File(mFileCache.get(position).path));
                    VideoUtil.processVideo(ResourceChooseActivity.this, files, new Handler.Callback() {
                        @Override
                        public boolean handleMessage(@NonNull Message msg) {
//                            if (msg != null) {
//                                finish();
//                            } else {
//
//                            }
                            VideoEditActivity.start(ResourceChooseActivity.this);
                            finish();
                            return true;
                        }
                    });
                });
            }

            @Override
            public int getItemCount() {
                return mFileCache.size();
            }

        });

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
                    FileEntry fileEntry = new FileEntry();
                    fileEntry.duration = Integer.parseInt(mediaMetadataRetriever.extractMetadata
                            (MediaMetadataRetriever.METADATA_KEY_DURATION)) / 1000;
                    fileEntry.thumb = mediaMetadataRetriever.getFrameAtIndex(0);
                    fileEntry.width = Integer.parseInt(mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    fileEntry.height = Integer.parseInt(mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                    fileEntry.path = mp4.getAbsolutePath();
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
}