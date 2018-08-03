package com.norsemensolutions.ocrrecognition;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextDetector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    public final static String TAG = MainActivity.class.getCanonicalName();

    private static final String EXTRA_FILENAME=
            "com.norsemensolutions.ocrrecognition.EXTRA_FILENAME";
    private static final String FILENAME="OCRRecognitionDemo.jpeg";
    private static final int CONTENT_REQUEST=1337;
    private static final int CAMERA_REQUEST_CODE = 100;
    private static final String AUTHORITY=
            BuildConfig.APPLICATION_ID+".provider";
    private static final String PHOTOS="photos";
    private File output=null;
    private boolean appCameraReady = false;
    private String memberIdValue = "";

    @BindView(R.id.capture_image_button) Button captureImageButton;
    @BindView(R.id.process_image_button) Button processImageButton;
    @BindView(R.id.member_view) ConstraintLayout memberCardView;
    @BindView(R.id.member_card_view) ImageView memberCardImageView;
    @BindView(R.id.member_id_text) TextView memberIdText;

    private void appendMemberIdText(String textValue) {
        memberIdValue += textValue;
    }

    @OnClick(R.id.capture_image_button)
    public void captureButtonTap() {
        if (appCameraReady) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            Uri outputUri = FileProvider.getUriForFile(this, AUTHORITY, output);

            intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                ClipData clip = ClipData.newUri(getContentResolver(), "A photo", outputUri);

                intent.setClipData(clip);

                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } else {
                List<ResolveInfo> resInfoList =
                        getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;

                    grantUriPermission(packageName, outputUri,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
            }

            try {
                startActivityForResult(intent, CONTENT_REQUEST);
            }
            catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.msg_no_camera, Toast.LENGTH_LONG).show();

                finish();
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == CONTENT_REQUEST) {
            if (resultCode == RESULT_OK) {
                Intent intent = new Intent(Intent.ACTION_VIEW);

                Uri outputUri = FileProvider.getUriForFile(this, AUTHORITY, output);

                intent.setDataAndType(outputUri, "image/jpeg");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                showMemberCard(outputUri);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        FirebaseApp.initializeApp(this);

        if (savedInstanceState==null) {
            output=new File(new File(getFilesDir(), PHOTOS), FILENAME);

            if (output.exists()) {
                output.delete();
            }
            else {
                output.getParentFile().mkdirs();
            }
        }
        else {
            output = (File) savedInstanceState.getSerializable(EXTRA_FILENAME);

            showMemberCard(Uri.fromFile(output));
        }


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                new MaterialDialog.Builder(this)
                        .title(R.string.camera_permission_title)
                        .content(R.string.camera_permission_explanation)
                        .positiveText(R.string.yes)
                        .negativeText(R.string.no)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(MaterialDialog dialog, DialogAction which) {
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
                            }
                        })
                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(MaterialDialog dialog, DialogAction which) {
                                captureImageButton.setEnabled(false);
                            }
                        })
                        .show();
            }
            else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
            }
        }
        else {
            appCameraReady = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case CAMERA_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    appCameraReady = true;
                }
                else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    captureImageButton.setEnabled(false);
                }

                return;
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(EXTRA_FILENAME, output);
    }

    @OnClick(R.id.process_image_button)
    public void processImageTap() {
        String memberId = "";

        try {
            Uri fileUri = Uri.fromFile(output);

            InputStream inputStream = getContentResolver().openInputStream(fileUri);

            Bitmap memberCardBitmap = BitmapFactory.decodeStream(inputStream);

            FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(memberCardBitmap);

            FirebaseVisionTextDetector detector = FirebaseVision.getInstance()
                    .getVisionTextDetector();

            Task<FirebaseVisionText> result =
                    detector.detectInImage(firebaseVisionImage)
                            .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                                @Override
                                public void onSuccess(FirebaseVisionText firebaseVisionText) {
                                    for (FirebaseVisionText.Block block: firebaseVisionText.getBlocks()) {
                                        Rect boundingBox = block.getBoundingBox();
                                        Point[] cornerPoints = block.getCornerPoints();
                                        String text = block.getText();

                                        if (block.getText().contains("dentification")) {
                                            boolean nextLine = false;

                                            for (FirebaseVisionText.Line line : block.getLines()) {
                                                for (FirebaseVisionText.Element element : line.getElements()) {
                                                    if (nextLine) {
                                                        appendMemberIdText(element.getText());

                                                        nextLine = false;
                                                        break;
                                                    }
                                                    else {
                                                        if (element.getText().contains("#")) {
                                                            nextLine = true;
                                                        } else {
                                                            nextLine = false;
                                                        }
                                                    }

                                                    Log.d(TAG, "Element text: [" + element.getText() + "]");
                                                }
                                            }
                                        }
                                    }

                                    if (!TextUtils.isEmpty(memberIdValue)) {
                                        memberIdText.setText(memberIdValue);
                                    }
                                    else {
                                        memberIdText.setText("Member Id Not Found");
                                    }
                                }
                            })
                            .addOnFailureListener(
                                    new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Toast.makeText(MainActivity.this, "Image Process Error", Toast.LENGTH_LONG).show();
                                        }
                                    });

        }
        catch (FileNotFoundException fileNotFoundException) {
            Toast.makeText(this, R.string.msg_no_member_card, Toast.LENGTH_LONG).show();
        }
    }

    private void showMemberCard(Uri fileUri) {
        captureImageButton.setVisibility(View.INVISIBLE);
        memberCardView.setVisibility(View.VISIBLE);

        try {
            InputStream inputStream = getContentResolver().openInputStream(fileUri);

            Bitmap memberCardBitmap = BitmapFactory.decodeStream(inputStream);

            memberCardImageView.setImageBitmap(memberCardBitmap);
        }
        catch (FileNotFoundException fileNotFoundException) {
            Toast.makeText(this, R.string.msg_no_member_card, Toast.LENGTH_LONG).show();
        }
    }
}