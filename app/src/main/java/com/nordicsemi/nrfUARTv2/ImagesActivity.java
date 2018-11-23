package com.nordicsemi.nrfUARTv2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayout;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;

public class ImagesActivity extends BLEActivity implements View.OnClickListener{
    static final String EXTRA_IMAGE_PATHS = "extra_image_path";
    private ImageView[] images = new ImageView[4];
    private String[] paths;
    private GridLayout gridLayout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(2);
        gridLayout.setRowCount(2);
        gridLayout.post(new Runnable() {
            @Override
            public void run() {
                init();
            }
        });
        setContentView(gridLayout);

        paths = getIntent().getStringArrayExtra(EXTRA_IMAGE_PATHS);

    }

    @Override
    protected void notSupported() {

    }

    @Override
    protected void connected() {
        byte value[] = {'0'};
        mService.writeRXCharacteristic(value);
    }

    @Override
    protected void disconnected() {

    }

    @Override
    protected void dataAvailable(String text) {
        if (text.equals("8")){
            for (int i = 0; i<4; i++){
                handleDelete(i);
            }
            finish();
        }
    }

    private void init() {
        int screenWidth = gridLayout.getWidth();
        int screenHeight = gridLayout.getHeight();
        int halfScreenWidth = (int)(screenWidth *0.5);
        int halfScreenHeight = (int)(screenHeight *0.5);

        for (int i = 0; i < 4; i++) {
            View itemImage = getLayoutInflater().inflate(R.layout.item_image, gridLayout, false);
            GridLayout.Spec row = GridLayout.spec(i < 2 ? 0 : 1);
            GridLayout.Spec col = GridLayout.spec(i % 2);
            GridLayout.LayoutParams first = new GridLayout.LayoutParams(row, col);

            first.width = halfScreenWidth;
            first.height = halfScreenHeight;
            gridLayout.addView(itemImage, first);

            images[i] = itemImage.findViewById(R.id.image);
            Bitmap bitmap = BitmapFactory.decodeFile(paths[i]);
            images[i].setImageBitmap(bitmap);

            Button deleteButton = itemImage.findViewById(R.id.btn_delete);
            deleteButton.setOnClickListener(handleDelete(i));
        }
    }

    private View.OnClickListener handleDelete(final int index) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (new File(paths[index]).delete()) {
                    Toast.makeText(ImagesActivity.this, R.string.deleted_image, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ImagesActivity.this, R.string.delete_image_fail, Toast.LENGTH_SHORT).show();
                }
                v.setOnClickListener(null);
                images[index].setVisibility(View.INVISIBLE);
            }
        };
    }

    @Override
    public void onClick(View v) {

    }
}
