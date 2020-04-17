package org.tensorflow.lite.examples.detection;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.TestLooperManager;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.model.Note;
import org.tensorflow.lite.examples.detection.model.User;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class NoteTakingActivity extends CameraActivity implements ImageReader.OnImageAvailableListener {

    private static final Logger LOGGER = new Logger();

    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 200;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "model.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labels.txt";
    private static final NoteTakingActivity.DetectorMode MODE = NoteTakingActivity.DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.85f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(200, 200);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Classifier detector;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private HashMap<String, Integer> preprocessBuffer = new HashMap<String, Integer>();
    StringBuilder note = new StringBuilder();
    int recurCount = 0;
    int recurCountNonLetter = 0;
    String lastLetter, lastNonLetter;

    private long timestamp = 0;
    private boolean saved = false;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private BorderedText borderedText;
    private EditText noteEditText, noteTitleEditText;

    private String currentNote = "", noteTitle="", currentNoteId="", noteBeforeSave="", titleBeforeSave="";

    private TextToSpeech textToSpeech;

    TextView username;

    boolean use_camera_prediction = true, use_speech_to_text = false;

    FirebaseUser firebaseUser;
    DatabaseReference reference;
    ImageButton btn_camera, btn_notelist, btn_delete, btn_add_new;
    FloatingActionButton btn_save;

    FrameLayout CameraContainer;



    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_taking_activity);

        noteEditText = findViewById(R.id.textView);
        noteTitleEditText = findViewById(R.id.note_title);

        Intent intent = getIntent();
        if (intent.getExtras() != null){
            noteTitle = intent.getStringExtra("title");
            currentNote = intent.getStringExtra("text");
            currentNoteId = intent.getStringExtra("noteId");
            noteTitleEditText.setText(noteTitle);
            noteEditText.setText(currentNote);
        } else {
            noteTitle ="";
            currentNote="";
            currentNoteId="";
        }

        noteEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cameraPredictionDisable();
            }
        });

        noteEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                saved = false;
            }
        });

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("");

        CameraContainer = findViewById(R.id.container);

        btn_save = findViewById(R.id.btn_save);
        btn_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (shouldSave()) {
                    saveCurrentNote();
                } else {
                    Toast.makeText(NoteTakingActivity.this, "Note Title Missing", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btn_add_new = findViewById(R.id.btn_add_new);
        btn_add_new.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (shouldSave()) {
                    Intent intent = new Intent(NoteTakingActivity.this, NoteTakingActivity.class);
                    startActivity(intent);
                }
            }
        });

        btn_delete = findViewById(R.id.btn_delete);
        btn_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (shouldDelete()) {
                    deleteCurrentNote();
                    Intent intent = new Intent(NoteTakingActivity.this, NoteTakingActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(NoteTakingActivity.this, "Note is blank!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btn_camera = findViewById(R.id.btn_camera);
        btn_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (CameraContainer.getVisibility() == View.INVISIBLE){
                    CameraContainer.setVisibility(View.VISIBLE);
                    btn_camera.setBackgroundResource(R.drawable.ic_cam_off);
                    use_camera_prediction = true;
                    use_speech_to_text = false;
                } else {
                    cameraPredictionDisable();
                }
            }
        });

        btn_notelist = findViewById(R.id.note_list);
        btn_notelist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(NoteTakingActivity.this, NoteListActivity.class);
                startActivity(intent);
            }
        });

        username = findViewById(R.id.username);

        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        reference = FirebaseDatabase.getInstance().getReference("users").child(firebaseUser.getUid());

        reference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                User user = dataSnapshot.getValue(User.class);
                username.setText(user.getName());

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        textToSpeech=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.UK);
                }
            }
        });

        findViewById(R.id.btn_tts).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String toSpeak = noteEditText.getText().toString();
                Toast.makeText(getApplicationContext(), toSpeak,Toast.LENGTH_SHORT).show();
                textToSpeech.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null); //TODO TTS DOESN'T WORK AFTER STT
            }
        });

        findViewById(R.id.btn_stt).setOnClickListener(new View.OnClickListener() {
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
                        Toast.makeText(NoteTakingActivity.this, "Sorry, this feature is not supported on your device", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(NoteTakingActivity.this, "You can't use this feature while the camera is on", Toast.LENGTH_SHORT).show();
                }
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        preprocessBuffer.put("A",0);
        preprocessBuffer.put("B",0);
        preprocessBuffer.put("C",0);
        preprocessBuffer.put("D",0);
        preprocessBuffer.put("E",0);
        preprocessBuffer.put("F",0);
        preprocessBuffer.put("G",0);
        preprocessBuffer.put("H",0);
        preprocessBuffer.put("I",0);
        preprocessBuffer.put("J",0);
        preprocessBuffer.put("K",0);
        preprocessBuffer.put("L",0);
        preprocessBuffer.put("M",0);
        preprocessBuffer.put("N",0);
        preprocessBuffer.put("O",0);
        preprocessBuffer.put("P",0);
        preprocessBuffer.put("Q",0);
        preprocessBuffer.put("R",0);
        preprocessBuffer.put("S",0);
        preprocessBuffer.put("T",0);
        preprocessBuffer.put("U",0);
        preprocessBuffer.put("V",0);
        preprocessBuffer.put("W",0);
        preprocessBuffer.put("X",0);
        preprocessBuffer.put("Y",0);
        preprocessBuffer.put("Z",0);
        preprocessBuffer.put("del",0);
        preprocessBuffer.put("nothing",0);
        preprocessBuffer.put("space",0);


    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        cameraPredictionDisable();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case 100:{
                if (resultCode == RESULT_OK && null != data){
                    ArrayList result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    noteEditText.append(result.get(0).toString());
                }
                break;
            }
        }
    }

    private void cameraPredictionDisable(){
        CameraContainer.setVisibility(View.INVISIBLE);
        btn_camera.setBackgroundResource(R.drawable.ic_cam_on);
        use_camera_prediction = false;
        use_speech_to_text = true;
        note.delete(0 , note.length());
    }

    private void deleteCurrentNote(){
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference();
        DatabaseReference noteRef = reference.child("notes/"+currentNoteId);
        noteRef.setValue(null);
        DatabaseReference userNoteRef = reference.child("user-notes/"+FirebaseAuth.getInstance().getCurrentUser().getUid()+'/'+currentNoteId);
        userNoteRef.setValue(null);
        Toast.makeText( NoteTakingActivity.this, "Note deleted!", Toast.LENGTH_SHORT).show();
    }

    private void saveCurrentNote(){
        if (!noteTitleEditText.getText().equals("")) {
            //TODO Implement Note Saving to Firebase
            DatabaseReference reference = FirebaseDatabase.getInstance().getReference();
            reference = reference.child("notes");
            if (currentNoteId == "") {
                currentNoteId = reference.push().getKey();
            }
            HashMap<String, Object> hashMap = new HashMap<>();
            hashMap.put("id", currentNoteId);
            hashMap.put("ownerId", FirebaseAuth.getInstance().getCurrentUser().getUid());
            hashMap.put("text", noteEditText.getText().toString());
            hashMap.put("timestamp", System.currentTimeMillis());
            hashMap.put("title", noteTitleEditText.getText().toString());
            reference.child(currentNoteId).setValue(hashMap).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    HashMap<String, Object> hashMap = new HashMap<>();
                    hashMap.put(currentNoteId, 1);
                    DatabaseReference reference1 = FirebaseDatabase.getInstance().getReference("user-notes");
                    reference1.child(FirebaseAuth.getInstance().getCurrentUser().getUid()).updateChildren(hashMap);
                    Toast.makeText(NoteTakingActivity.this, "Note saved!", Toast.LENGTH_SHORT).show();
                    noteBeforeSave="";
                    noteBeforeSave+=currentNote;
                    titleBeforeSave="";
                    titleBeforeSave+=noteTitle;
                }
            });
        } else {
            Toast.makeText(NoteTakingActivity.this, "Title is missing", Toast.LENGTH_SHORT).show();
        }
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
        int dimension = getSquareCropDimensionForBitmap(rgbFrameBitmap);
        croppedBitmap = ThumbnailUtils.extractThumbnail(rgbFrameBitmap, cropSize, cropSize);
        //croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = findViewById(R.id.tracking_overlay);
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
                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
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

    public int getSquareCropDimensionForBitmap(Bitmap bitmap)
    {
        //use the smallest dimension of the image to crop to
        return Math.min(bitmap.getWidth(), bitmap.getHeight());
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
        TF_OD_API
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
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

            if (recurCountNonLetter > 2){
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
                    note.append(noteEditText.getText());
                    note.deleteCharAt(note.length()-1);
                    noteEditText.setText(note);
                }
            } else if (text.equals("space")) {
                noteEditText.append(" ");
            } else if (text.equals("nothing")) {
                //Do nothing
                return;
            } else {
                noteEditText.append(text);
            }
            noteEditText.setSelection(noteEditText.getText().toString().length());
        }
    }

    @Override
    public synchronized void onPause() {
        if(textToSpeech !=null){
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onPause();
    }

    private boolean shouldSave(){
        return !noteTitleEditText.getText().toString().equals("");
    }

    private boolean shouldDelete() {
        //Should delete when something is written
        return !noteTitleEditText.getText().toString().equals("") || !noteEditText.getText().toString().equals("");
    }

}
