package org.tensorflow.lite.examples.detection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.tensorflow.lite.examples.detection.Adapter.MessageAdapter;
import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.model.Chat;
import org.tensorflow.lite.examples.detection.model.User;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class ChatWindow extends CameraActivity implements ImageReader.OnImageAvailableListener {
    TextView username;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final int PERMISSIONS_REQUEST = 1;

    Boolean use_camera_prediction = true, use_speech_to_text = false;
    EditText txt_message;

    FirebaseUser firebaseUser;
    DatabaseReference reference;

    ImageButton btn_send, btn_camera, btn_stt;
    EditText text_send;

    MessageAdapter messageAdapter;
    List<Chat> mChats;

    RecyclerView recyclerView;

    Intent intent;
    FrameLayout CameraContainer;


    private static final Logger LOGGER = new Logger();

    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 200;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "model.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labels.txt";
    private static final ChatWindow.DetectorMode MODE = ChatWindow.DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.8f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(200, 200);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Classifier detector;

    private HashMap<String, Integer> preprocessBuffer = new HashMap<String, Integer>();
    StringBuilder note = new StringBuilder();
    int recurCount = 0;
    int recurCountNonLetter = 0;
    String lastLetter, lastNonLetter;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private BorderedText borderedText;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(null);
        setContentView(R.layout.activity_chat_window);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        txt_message = findViewById(R.id.text_send);
        btn_stt = findViewById(R.id.btn_stt);
        btn_camera = findViewById(R.id.btn_camera);

        CameraContainer = findViewById(R.id.container);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(linearLayoutManager);

        intent = getIntent();
        String userid = intent.getStringExtra("userid");

        username = findViewById(R.id.username) ;
        btn_send = findViewById(R.id.btn_send);
        text_send = findViewById(R.id.text_send);

        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = text_send.getText().toString();
                if (!msg.equals("")){
                    sendMessage(firebaseUser.getUid(), userid, msg);
                }
                note.delete(0, note.length());
                text_send.setText("");
            }
        });

        btn_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (CameraContainer.getVisibility() == View.INVISIBLE){
                    CameraContainer.setVisibility(View.VISIBLE);
                    btn_camera.setBackgroundResource(R.drawable.ic_cam_off);
                    use_camera_prediction = true;
                    use_speech_to_text = false;
                } else {
                    CameraContainer.setVisibility(View.INVISIBLE);
                    btn_camera.setBackgroundResource(R.drawable.ic_cam_on);
                    use_camera_prediction = false;
                    use_speech_to_text = true;
                }
            }
        });


        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        reference = FirebaseDatabase.getInstance().getReference("users").child(userid);
        reference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                User user = dataSnapshot.getValue(User.class);
                username.setText(user.getName());
                readMessage(firebaseUser.getUid(), userid);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        btn_stt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (use_speech_to_text) {
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Please speak now");
                    try {
                        startActivityForResult(intent, 100);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(ChatWindow.this, "Sorry, this feature is not supported on your device", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(ChatWindow.this, "You can't use this feature while the camera is on", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        CameraContainer.setVisibility(View.INVISIBLE);
        ImageButton btn_camera = findViewById(R.id.btn_camera);
        btn_camera.setBackgroundResource(R.drawable.ic_cam_on);
        use_camera_prediction = false;
        use_speech_to_text = true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case 100:{
                if (resultCode == RESULT_OK && null != data){
                    ArrayList result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    text_send.append(result.get(0).toString());
                }
                break;
            }
        }
    }

    private void sendMessage(String sender, String receiver, String message){
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference();
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("senderId",sender);
        hashMap.put("receiverId",receiver);
        hashMap.put("text", message);
        hashMap.put("timestamp", System.currentTimeMillis());
        reference = reference.child("messages");
        String message_id = reference.push().getKey();
        reference.child(message_id).setValue(hashMap).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                HashMap<String, Object> hashMap = new HashMap<>();
                hashMap.put(message_id, 1);
                DatabaseReference reference1 = FirebaseDatabase.getInstance().getReference("user-messages");
                reference1.child(sender).updateChildren(hashMap);
                FirebaseDatabase.getInstance().getReference("user-messages").child(receiver).updateChildren(hashMap);
            }
        });
    }

    private void readMessage(final String myid, final String userid){
        Log.d("test", "readMessage is invoked");
        mChats = new ArrayList<>();
        messageAdapter = new MessageAdapter(ChatWindow.this, mChats);
        recyclerView.setAdapter(messageAdapter);

        FirebaseDatabase.getInstance().getReference("user-messages").child(myid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d("test","data changed! event invoked!");
                mChats.clear();
                DatabaseReference messagesRef = FirebaseDatabase.getInstance().getReference("messages");
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    DatabaseReference message = messagesRef.child(snapshot.getKey());
                    message.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            Log.d("test",String.valueOf(dataSnapshot.getChildrenCount()));
                            Chat chat = dataSnapshot.getValue(Chat.class);

                            if (chat.getReceiverId().equals(myid) && chat.getSenderId().equals(userid) ||
                                    chat.getReceiverId().equals(userid) && chat.getSenderId().equals(myid)){
                                Log.d("test", "chat added from " +firebaseUser.getUid() + "to " + chat.getReceiverId());
                                mChats.add(chat);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        messageAdapter = new MessageAdapter(ChatWindow.this, mChats);
                                        recyclerView.setAdapter(messageAdapter);
                                        recyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                                    }
                                });

                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = ThumbnailUtils.extractThumbnail(rgbFrameBitmap, cropSize, cropSize);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);
                                showPrediction(result.getTitle());
                            }
                        }
                        trackingOverlay.postInvalidate();

                        computingDetection = false;
                    }
                });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onClick(View view) {

    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (allPermissionsGranted(grantResults)) {
                setFragment();
            } else {
                requestPermission();
            }
        }
    }

    private static boolean allPermissionsGranted(final int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                        ChatWindow.this,
                        "Camera permission is required for this demo",
                        Toast.LENGTH_LONG)
                        .show();
            }
            requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
        }
    }

    protected void showPrediction(String pred){
        if (!pred.equals("del") && !pred.equals("space") && !pred.equals("nothing")) {
            if (pred.equals(lastLetter)) {
                recurCount++;
            } else {
                lastLetter = pred;
                recurCount = 0;
            }

            if (recurCount > 2) {
                appendText(lastLetter);
                recurCount = 0;
            }
        } else {
            if (pred.equals(lastNonLetter)){
                recurCountNonLetter++;
            } else {
                lastNonLetter = pred;
                recurCountNonLetter = 0;
            }

            if (recurCountNonLetter > 1){
                appendText(lastNonLetter);
                recurCountNonLetter = 0;
            }
        }

    }

    private void appendText(String text){
        if (use_camera_prediction) {
            if (text.equals("del")) {
                if (text.length() > 0) {
                    note.delete(0, note.length());
                    note.append(text_send.getText());
                    note.deleteCharAt(note.length()-1);
                    text_send.setText(note);
                }
            } else if (text.equals("space")) {
                text_send.append(" ");
            } else if (text.equals("nothing")) {
                //Do nothing
            } else {
                text_send.append(text);
            }
            //TODO Try to append letter instead of a while note.
            text_send.setSelection(text_send.getText().toString().length());
        }
    }
}
