package com.nordicsemi.nrfUARTv2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;

public class ImageActivity extends AppCompatActivity implements View.OnClickListener {
    static final String EXTRA_IMAGE_PATH = "image_path";

    private String imagePath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_image);
        imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);

        ImageView image = findViewById(R.id.image);
        image.setImageBitmap(bitmap);
        findViewById(R.id.btn_drive).setOnClickListener(this);
        findViewById(R.id.btn_delete).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_drive:
                Toast.makeText(this, "Not implemented", Toast.LENGTH_SHORT).show();
                break;
            case R.id.btn_delete:
                if (new File(imagePath).delete()) {
                    Toast.makeText(this, R.string.deleted_image, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.delete_image_fail, Toast.LENGTH_SHORT).show();
                }
                finish();
                break;
        }
    }
}