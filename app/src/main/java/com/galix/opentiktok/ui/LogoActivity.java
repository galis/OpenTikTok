package com.galix.opentiktok.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.galix.opentiktok.R;
import com.galix.opentiktok.dp.GameActivity;

public class LogoActivity extends BaseActivity {

    private static final int REQUEST_CODE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logo);

        getSupportActionBar().setTitle(R.string.opentt);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);

        findViewById(R.id.btn_start_work).setOnClickListener(v -> {
            VideoEditActivity.start(LogoActivity.this);
        });

        getSystemService(ACTIVITY_SERVICE);

        findViewById(R.id.btn_take_video).setOnClickListener(v -> {
            Toast.makeText(LogoActivity.this, "暂未支持!", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_record_screen).setOnClickListener(v -> {
            GameActivity.start(LogoActivity.this);
//            Toast.makeText(LogoActivity.this, "暂未支持!", Toast.LENGTH_SHORT).show();
        });

//        getWindow().getDecorView().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                startActivity(new Intent(LogoActivity.this, LogoActivity.class));
//                finish();
//            }
//        }, 3000);

        checkPermission();
    }

    //权限部分
    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(LogoActivity.this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(LogoActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(LogoActivity.this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(LogoActivity.this,
                    new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.INTERNET,
                            Manifest.permission.ACCESS_NETWORK_STATE
                    }, REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, permissions[i] + "IS NOT ALLOW!!", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
        }
    }
}