package com.nordicsemi.nrfUARTv2;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static android.hardware.Camera.Parameters.FLASH_MODE_OFF;
import static android.hardware.Camera.Parameters.FLASH_MODE_ON;
import static com.nordicsemi.nrfUARTv2.ImageActivity.EXTRA_IMAGE_PATH;

public class CameraActivity extends BLEActivity implements View.OnClickListener {
    private static final String TAG = "CameraActivity";
    private static int MY_PERMISSIONS_REQUEST_CAMERA = 2;
    private CameraPreview mPreview;
    private Camera mCamera;

    //done by jun rong
    private static final List<Integer> DELAY_DURATIONS = Arrays.asList(0, 5, 15, 30);
    private static final int DEFAULT_DELAY = 0;
    private int pictureDelay = DEFAULT_DELAY;
    static final String DELAY_PREFERENCES_KEY = "delay";

    Button timerBtn;
    ImageView changeCamBtn;
    ImageView flashButton;
    List<Uri>pictureURIs;
    TextView statusTextField;

    int currentPictureID = 0;
    int currentCameraID = Camera.CameraInfo.CAMERA_FACING_BACK;
    int pictureTimer = 0;
    Handler handler = new Handler();
    private boolean enableFlash = false;


    private boolean faceDetectionRunning = false;
    private Camera.Face[] mFaces;

    private FaceOverlayView mFaceView;

    private int mOrientation;
    private int mOrientationCompensation;
    private OrientationEventListener mOrientationEventListener;

    private int mDisplayRotation;
    private int mDisplayOrientation;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);

        Button captureBtn = findViewById(R.id.button_capture);
        captureBtn.setOnClickListener(this);

        flashButton = findViewById(R.id.flash_button);
        timerBtn = findViewById(R.id.DelayButton);
        timerBtn.setOnClickListener(this);
        statusTextField = findViewById(R.id.statusText);


        changeCamBtn = findViewById(R.id.changeCamBtn);
        changeCamBtn.setOnClickListener(this);

        mFaceView = new FaceOverlayView(this);
        addContentView(mFaceView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        mOrientationEventListener = new SimpleOrientationEventListener(this);
        mOrientationEventListener.enable();

        checkCameraPermission();
        readDelayPreference();
        updateFlashButton();
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
            savePicture();
        } else if (text.equals("3")) {
            cycleDelay();
        }else if (text.equals("4")){
            switchToNextCamera();
        }
        else if (text.equals("5")){
            enableFlash = !enableFlash;
            Camera.Parameters parameters = mCamera.getParameters();
            if (enableFlash) {
                parameters.setFlashMode(FLASH_MODE_ON);
            } else {
                parameters.setFlashMode(FLASH_MODE_OFF);
            }
            mCamera.setParameters(parameters);
        }else if(text.equals("6")){
            mCamera.takePicture(null, null, mBurstPicture);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_CAMERA) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCamera(currentCameraID);
            } else {
                showNoCameraMessage();
            }
        }
    }

    private void checkCameraPermission() {
        Dexter.withActivity(this)
                .withPermission(Manifest.permission.CAMERA)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        initCamera(currentCameraID);
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {
                        new AlertDialog.Builder(CameraActivity.this)
                                .setMessage("Enable camera to use the application")
                                .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        checkCameraPermission();
                                    }
                                })
                                .setNegativeButton("Quit", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        finish();
                                    }
                                })
                                .show();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                })
                .check();
    }

    private FaceDetectionListener faceDetectionListener = new FaceDetectionListener() {
        @Override
        public void onFaceDetection(Face[] faces, Camera camera) {
            Log.d("onFaceDetection", "Number of Faces:" + faces.length);
            // Update the view now!
            mFaceView.setFaces(faces);
        }
    };

    private void initCamera(int cameraType) {
        if (!hasCamera()) {
            showNoCameraMessage();
            return;
        }

        mCamera = null;
        try {
            mCamera = Camera.open(cameraType); // attempt to get a Camera instance
            mCamera.setFaceDetectionListener(faceDetectionListener);
            mCamera.startFaceDetection();
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
            setDisplayOrientation();
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


    //done by jun rong
    void writeDelayPreference(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        prefs.edit()
                .putInt(DELAY_PREFERENCES_KEY, this.pictureDelay)
                .apply();
    }

    void readDelayPreference(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        int delay = prefs.getInt(DELAY_PREFERENCES_KEY, -1);
        if (!DELAY_DURATIONS.contains(delay)){
            delay = DEFAULT_DELAY;
        }
        this.pictureDelay = delay;
        updateDelayButton();
    }


    Runnable makeDecrementTimerFunction(final int pictureID){
        return new Runnable() {
            @Override
            public void run() {
                decrementTimer(pictureID);
            }
        };
    }

    void updateTimerMessage() {
        statusTextField.setTextColor(0xAAFFFFFF);
        String timerCountdownFormat = "%d";
        statusTextField.setText(String.format(timerCountdownFormat, pictureTimer));
    }


    protected void updateDelayButton(){
        if (pictureDelay==0){
            timerBtn.setText("Delay: None");
        }else{
            String labelFormat = "Delay: %d Sec";
            timerBtn.setText(String.format(labelFormat, this.pictureDelay));
        }
    }

    private void updateFlashButton() {
        if (enableFlash) {
            flashButton.setImageResource(R.drawable.flash_on);
        } else {
            flashButton.setImageResource(R.drawable.flash_off);
        }
    }


    protected void cycleDelay(){
        int index = DELAY_DURATIONS.indexOf(this.pictureDelay);

        if (index<0){
            this.pictureDelay = DEFAULT_DELAY;
        }else{
            this.pictureDelay = DELAY_DURATIONS.get((index+1) % DELAY_DURATIONS.size());
        }
        writeDelayPreference();
        updateDelayButton();
    }

    private void decrementTimer(final int pictureID){
        if (pictureID != this.currentPictureID){
            return;
        }
        boolean takePicture = (pictureTimer == 1);
        --pictureTimer;
        if (takePicture) {
            savePictureNow();
        }else if (pictureTimer > 0){
            updateTimerMessage();
            handler.postDelayed(makeDecrementTimerFunction(pictureID), 1000);
        }
    }


    public void savePicture(){
        Dexter.withActivity(this)
                .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        if(pictureDelay == 0){
                            savePictureNow();
                        }else{
                            savePictureAfterDelay(pictureDelay);
                        }
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {
                        Toast.makeText(CameraActivity.this, "Allow permission to take photo", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                })
                .check();
    }




    void savePictureAfterDelay(int delay){
        pictureTimer = delay;
        updateTimerMessage();
        currentPictureID++;
        handler.postDelayed(makeDecrementTimerFunction(currentPictureID), 1000);

    }


    public void savePictureNow(){
        pictureURIs = new ArrayList<>();
        statusTextField.setText("Taking picture...");
        mCamera.takePicture(null, null, mPicture);
    }

    public void stopCamera() {
        if (mCamera!=null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }


    public void switchToNextCamera() {
        stopCamera();
        if (currentCameraID == Camera.CameraInfo.CAMERA_FACING_BACK){
            currentCameraID = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }else{
            currentCameraID = Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        initCamera(currentCameraID);

    }

    private void setDisplayOrientation() {
        // Now set the display orientation:
        mDisplayRotation = Util.getDisplayRotation(CameraActivity.this);
        mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation, currentCameraID);

        mCamera.setDisplayOrientation(mDisplayOrientation);

        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(mDisplayOrientation);
        }
    }

    //end


    private Camera.PictureCallback mBurstPicture = new Camera.PictureCallback() {
        int count = 4;
        String[] paths = new String[count];

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            File pictureFile = saveImage(data);
            String path = pictureFile.getAbsolutePath();
            count--;
            paths[count] = path;
            Toast.makeText(CameraActivity.this, "burst mode " + count + " left",
                    Toast.LENGTH_SHORT).show();
            if (count == 0) {
                Intent intent = new Intent(CameraActivity.this, ImagesActivity.class);
                intent.putExtra(ImagesActivity.EXTRA_IMAGE_PATHS, paths);
                startActivity(intent);
                count = 4;
                paths = new String[count];
            } else {
                mCamera.takePicture(null, null, mBurstPicture);
            }
        }
    };


    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            statusTextField.setText("");
            File pictureFile = saveImage(data);
            if (pictureFile == null) {
                return;
            }

            Toast.makeText(CameraActivity.this, "Saved photo to " + pictureFile.getPath(), Toast.LENGTH_LONG).show();

            //Update the log with time stamp
            Intent intent = new Intent(CameraActivity.this, ImageActivity.class);
            intent.putExtra(EXTRA_IMAGE_PATH, pictureFile.getAbsolutePath());
            startActivity(intent);
        }
    };

    private File saveImage(byte[] data) {
        File pictureFile = getOutputMediaFile();
        if (pictureFile == null){
            Log.d(TAG, "Error creating media file, check storage permissions");
            return null;
        }

        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            Bitmap rotatedBitmap = rotateImage(bitmap, 90, currentCameraID);
            FileOutputStream fos = new FileOutputStream(pictureFile);
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.close();
            return pictureFile;
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
        return null;
    }

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
            savePicture();
        } else if (v.getId() == R.id.DelayButton){
            cycleDelay();
        } else if (v.getId() == R.id.changeCamBtn){
            switchToNextCamera();
        } else if (v.getId() == R.id.flash_button) {
            enableFlash = !enableFlash;
            Camera.Parameters parameters = mCamera.getParameters();
            if (enableFlash) {
                parameters.setFlashMode(FLASH_MODE_ON);
            } else {
                parameters.setFlashMode(FLASH_MODE_OFF);
            }
            mCamera.setParameters(parameters);
            updateFlashButton();
        } else if (v.getId() == R.id.burst_shot) {
            mCamera.takePicture(null, null, mBurstPicture);
        } else if (v.getId() == R.id.disconnect_bt) {
            mService.disconnect();
            mService.stopForeground(true);
            mService.stopSelf();
            finish();
        }
    }


    private static Bitmap rotateImage(Bitmap source, float angle, int currentCameraID) {
        Matrix matrix = new Matrix();
      //  Camera.CameraInfo info = new Camera.CameraInfo();


        if (currentCameraID == Camera.CameraInfo.CAMERA_FACING_FRONT){
            float[] mirrorY = { -1, 0, 0, 0, 1, 0, 0, 0, 1};
            Matrix matrixMirrorY = new Matrix();
            matrixMirrorY.setValues(mirrorY);
            matrix.postConcat(matrixMirrorY);
        //    matrix.preRotate(270);
        }

        matrix.postRotate(angle);



        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    private class SimpleOrientationEventListener extends OrientationEventListener {

        public SimpleOrientationEventListener(Context context) {
            super(context, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = Util.roundOrientation(orientation, mOrientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mOrientation
                    + Util.getDisplayRotation(CameraActivity.this);
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                mFaceView.setOrientation(mOrientationCompensation);
            }
        }
    }


}
