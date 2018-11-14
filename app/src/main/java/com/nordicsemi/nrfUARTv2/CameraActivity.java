package com.nordicsemi.nrfUARTv2;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.nordicsemi.nrfUARTv2.ImageActivity.EXTRA_IMAGE_PATH;

public class CameraActivity extends BLEActivity implements View.OnClickListener {
    private static final String TAG = "CameraActivity";
    private static int MY_PERMISSIONS_REQUEST_CAMERA = 2;
    private CameraPreview mPreview;
    private Camera mCamera;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);

        Button captureBtn = findViewById(R.id.button_capture);
        captureBtn.setOnClickListener(this);

        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CAMERA);
            }
        } else {
            initCamera();
        }

    }

    @Override
    protected void notSupported() {

    }

    @Override
    protected void connected() {

    }

    @Override
    protected void disconnected() {

    }

    @Override
    protected void dataAvailable(String text) {
        if (text.equals("2")) {
            mCamera.takePicture(null, null, mPicture);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_CAMERA) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCamera();
            } else {
                showNoCameraMessage();
            }
        }
    }

    private void initCamera() {
        if (!hasCamera()) {
            showNoCameraMessage();
            return;
        }

        mCamera = null;
        try {
            mCamera = Camera.open(); // attempt to get a Camera instance
            mCamera.setDisplayOrientation(90);
            Camera.Parameters params = mCamera.getParameters();
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

            List<Camera.Size> sizes = params.getSupportedPictureSizes();
            Camera.Size size = sizes.get(0);
            for(int i=0;i<sizes.size();i++)
            {
                if(sizes.get(i).width > size.width)
                    size = sizes.get(i);
            }
            params.setPictureSize(size.width, size.height);

            mCamera.setParameters(params);
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
            e.printStackTrace();
            showNoCameraMessage();
        }

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = findViewById(R.id.camera_preview);
        preview.addView(mPreview);
    }

    private boolean hasCamera() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    private void showNoCameraMessage() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.no_camera)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile();
            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions");
                return;
            }

            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                Bitmap rotatedBitmap = rotateImage(bitmap, 90);
                FileOutputStream fos = new FileOutputStream(pictureFile);
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }

            Toast.makeText(CameraActivity.this, "Saved photo to " + pictureFile.getPath(), Toast.LENGTH_LONG).show();
            Intent intent = new Intent(CameraActivity.this, ImageActivity.class);
            intent.putExtra(EXTRA_IMAGE_PATH, pictureFile.getAbsolutePath());
            startActivity(intent);
        }
    };

    private File getOutputMediaFile() {
        File picPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File savePath = new File(picPath, "ble_app");
        if (!savePath.exists()) {
            if (!savePath.mkdirs()){
                Log.d(TAG, "failed to create directory");
                return null;
            }

        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        return new File(savePath.getPath() + File.separator +
                "IMG_"+ timeStamp + ".jpg");
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_capture) {
            // Here, thisActivity is the current activity
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                // Permission is not granted
                // Should we show an explanation?
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    // No explanation needed; request the permission
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            0);
                }
            } else {
                mCamera.takePicture(null, null, mPicture);
            }
        }
    }


    private static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }
}
