package com.nordicsemi.nrfUARTv2;

import android.Manifest;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.telephony.SmsManager;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import akiniyalocts.imgurapiexample.imgurmodel.ImageResponse;
import akiniyalocts.imgurapiexample.imgurmodel.Upload;
import akiniyalocts.imgurapiexample.services.UploadService;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class ImageActivity extends BLEActivity implements View.OnClickListener {
    static final String EXTRA_IMAGE_PATH = "image_path";
    private static final String PREF_PHONE_NUMBER = "pref_phone_number";
    private static final String SMS_TEMPLATE = "Case reported\nImage found here\n%s";

    private String imagePath;
    private String imgurLink;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_image);
        imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);

        ImageView image = findViewById(R.id.image);
        image.setImageBitmap(bitmap);
        findViewById(R.id.btn_send).setOnClickListener(this);
        findViewById(R.id.btn_delete).setOnClickListener(this);
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
            if (new File(imagePath).delete()) {
                Toast.makeText(this, R.string.deleted_image, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, R.string.delete_image_fail, Toast.LENGTH_SHORT).show();
                finish();
            }
        }

    }

    private String getPhoneNumber(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getString(PREF_PHONE_NUMBER, null);
    }

    private void setPhoneNumber(String phoneNumber) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit()
                .putString(PREF_PHONE_NUMBER, phoneNumber)
                .apply();
    }

    private void askPhoneNumber() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set phone number");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setPhoneNumber(input.getText().toString());
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_send:
                Dexter.withActivity(this)
                        .withPermissions(Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS)
                        .withListener(new MultiplePermissionsListener() {
                            @Override
                            public void onPermissionsChecked(MultiplePermissionsReport report) {
                                handleSendButton();
                            }

                            @Override
                            public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                                token.continuePermissionRequest();
                            }
                        }).check();
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

    private void doSend() {
        try {
            String msg = String.format(Locale.ENGLISH, SMS_TEMPLATE, imgurLink) ;
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(getPhoneNumber(), null, msg, null, null);
            Toast.makeText(getApplicationContext(), "Message Sent",
                    Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(getApplicationContext(),
                    ex.getMessage(),
                    Toast.LENGTH_LONG).show();
            ex.printStackTrace();
        }
    }

    private void uploadImageAndSend() {
        if (imgurLink != null) {
            doSend();
            return;
        }
        Upload upload = new Upload();
        upload.title = String.format(Locale.ENGLISH, "Image %d", new Random().nextInt(1000));
        upload.image = new File(imagePath);
        new UploadService(this).Execute(upload, new retrofit.Callback<ImageResponse>() {
            @Override
            public void success(ImageResponse imageResponse, Response response) {
                imgurLink = imageResponse.data.link;
                doSend();
            }

            @Override
            public void failure(RetrofitError error) {
                Toast.makeText(ImageActivity.this, "Failed to upload image", Toast.LENGTH_LONG).show();
            }
        });
    }


    private void handleSendButton() {
        String phoneNumber = getPhoneNumber();
        if (phoneNumber != null) {
            new AlertDialog.Builder(this)
                    .setMessage(String.format(Locale.ENGLISH, "Sending image to %s", phoneNumber))
                    .setPositiveButton("Send", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            uploadImageAndSend();
                        }
                    })
                    .setNeutralButton("Change number", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            askPhoneNumber();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            askPhoneNumber();
        }
    }
}
