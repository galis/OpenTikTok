package com.galix.opentiktok.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.galix.opentiktok.R;

public class LogoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logo);

        findViewById(R.id.btn_start_work).setOnClickListener(v -> {
            startActivity(new Intent(LogoActivity.this, VideoPickActivity.class));
        });

        findViewById(R.id.btn_take_video).setOnClickListener(v -> {
            Toast.makeText(LogoActivity.this, "暂未支持!", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_record_screen).setOnClickListener(v -> {
            Toast.makeText(LogoActivity.this, "暂未支持!", Toast.LENGTH_SHORT).show();
        });

//        getWindow().getDecorView().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                startActivity(new Intent(LogoActivity.this, VideoEditActivity.class));
//                finish();
//            }
//        }, 3000);
    }
}