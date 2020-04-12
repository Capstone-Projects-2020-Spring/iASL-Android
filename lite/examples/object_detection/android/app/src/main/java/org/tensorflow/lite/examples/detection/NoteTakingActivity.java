package org.tensorflow.lite.examples.detection;

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
import android.speech.tts.TextToSpeech;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

import java.io.IOException;
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
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.8f;
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

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;
    private EditText textView;

    private String currentNote = "";

    private TextToSpeech textToSpeech;

    FirebaseUser firebaseUser;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_taking_activity);

        textView = findViewById(R.id.textView);

        textToSpeech=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.UK);
                }
            }
        });

        findViewById(R.id.tts).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String toSpeak = textView.getText().toString();
                Toast.makeText(getApplicationContext(), toSpeak,Toast.LENGTH_SHORT).show();
                textToSpeech.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
            }
        });

        findViewById(R.id.saveButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                String[] msgArr = textView.getText().toString().split("\n", 2);
                if(!msgArr[0].equals("")){
                    sendNote(firebaseUser.getUid(), msgArr[0], msgArr[1]);
                }
                textView.setText("");
            }
        });

        findViewById(R.id.notesButton).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                startActivity(new Intent(NoteTakingActivity.this, NoteBrowsingActivity.class));
            }
        });

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(view -> finish());

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

        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();

    }

    private void sendNote(String owner, String title, String message){

        DatabaseReference reference = FirebaseDatabase.getInstance().getReference();
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("ownerId", owner);
        hashMap.put("title", title);
        hashMap.put("text", message);
        hashMap.put("timestamp", System.currentTimeMillis());
        reference = reference.child("notes");
        String note_id = reference.push().getKey();
        hashMap.put("id", note_id);

        reference.child(note_id).setValue(hashMap).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                HashMap<String, Object> hashMap = new HashMap<>();
                hashMap.put(note_id, 1);
                DatabaseReference reference1 = FirebaseDatabase.getInstance().getReference("user-notes");
                reference1.child(owner).updateChildren(hashMap);
            }
        });
    }

    private void readNote(final String myid, final String userid){
        Log.d("test", "readNote is invoked");

    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

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

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
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

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                    }
                                });
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

    protected void showPrediction(String pred){
        if (!pred.equals("del") && !pred.equals("space") && !pred.equals("nothing")) {
            if (pred.equals(lastLetter)) {
                recurCount++;
            } else {
                lastLetter = pred;
                recurCount = 0;
            }

            if (recurCount > 3) {
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
        if (note.length() > 0 && text.equals("del")){
            note.deleteCharAt(note.length()-1);
        } else if (text.equals("space")){
            note.append(" ");
        } else if (text.equals("nothing")){
            //Do nothing
        } else {
            note.append(text);
        }

        textView.setText(note.toString());
    }

    @Override
    public synchronized void onPause() {
        if(textToSpeech !=null){
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onPause();
    }
}
