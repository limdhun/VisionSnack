package org.pytorch.demo.objectdetection;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.AudioAttributes;
import android.media.Image;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.GestureDetector;
import android.view.ViewStub;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.camera.core.ImageProxy;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;

public class ObjectDetectionActivity extends AbstractCameraXActivity<ObjectDetectionActivity.AnalysisResult>
        implements TextToSpeech.OnInitListener {

    private Module mModule = null;
    private ResultView mResultView;
    private TextToSpeech mTTS;
    private ArrayList<String> mDetailList;
    private GestureDetector gestureDetector;


    static class AnalysisResult {
        private final ArrayList<Result> mResults;

        public AnalysisResult(ArrayList<Result> results) {
            mResults = results;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTTS = new TextToSpeech(this, this);
        loadDetailList();
    }

    private void loadDetailList() {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open("detail.txt")));
            mDetailList = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                mDetailList.add(line);
                Log.d("Detail", line); // 내용 확인을 위해 로그 출력
            }
            br.close();
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading detail.txt", e);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            Locale locale = Locale.getDefault(); // 기기 언어 설정 가져오기
            int result = mTTS.setLanguage(locale);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported");
                // 추가 조치 필요 (예: 사용자에게 알림 표시 또는 앱 종료)
            } else {
                // TTS 음성 속도 및 톤 조절
                mTTS.setSpeechRate(1.0f); // 음성 속도 (0.0 ~ 2.0 범위)
                mTTS.setPitch(1.0f); // 음성 톤 (0.0 ~ 2.0 범위)

                // TTS 음성 스트림 설정
                mTTS.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
            }
        } else {
            Log.e("TTS", "Initialization failed");
            // 추가 조치 필요 (예: 사용자에게 알림 표시 또는 앱 종료)
        }
    }

    @Override
    protected int getContentViewLayoutId() {
        return R.layout.activity_object_detection;
    }

    @Override
    protected TextureView getCameraPreviewTextureView() {
        mResultView = findViewById(R.id.resultView);
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                speakDetails();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                speakLabels();
                return true;
            }
        });

        mResultView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });

        return ((ViewStub) findViewById(R.id.object_detection_texture_view_stub))
                .inflate()
                .findViewById(R.id.object_detection_texture_view);
    }

    public void speakLabels() {
        StringBuilder labelBuilder = new StringBuilder();
        for (Result result : mResultView.mResults) {
            labelBuilder.append(PrePostProcessor.mClasses[result.classIndex])
                    .append(", ");
        }
        if (labelBuilder.length() > 0) {
            String labels = labelBuilder.toString();
            Log.d("TTS", "Speaking labels: " + labels);
            mTTS.speak(labels, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            Log.d("TTS", "No objects found");
            mTTS.speak("아무 제품도 없어요.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    public void speakDetails() {
        StringBuilder detailBuilder = new StringBuilder();
        for (Result result : mResultView.mResults) {
            if (result.classIndex < mDetailList.size()) {
                detailBuilder.append(mDetailList.get(result.classIndex))
                        .append(", "); // 쉼표와 공백 추가
            }
        }
        if (detailBuilder.length() > 0) {
            mTTS.speak(detailBuilder.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            mTTS.speak("아무 제품도 없어요.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void applyToUiAnalyzeImageResult(AnalysisResult result) {
        mResultView.setResults(result.mResults);
        mResultView.invalidate();
    }

    private Bitmap imgToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    @Override
    @WorkerThread
    @Nullable
    protected AnalysisResult analyzeImage(ImageProxy image, int rotationDegrees) {
        try {
            if (mModule == null) {
                mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), "best.torchscript.ptl"));
            }
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            return null;
        }
        Bitmap bitmap = imgToBitmap(image.getImage());
        Matrix matrix = new Matrix();
        matrix.postRotate(90.0f);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);

        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
        final Tensor outputTensor = outputTuple[0].toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();

        float imgScaleX = (float)bitmap.getWidth() / PrePostProcessor.mInputWidth;
        float imgScaleY = (float)bitmap.getHeight() / PrePostProcessor.mInputHeight;
        float ivScaleX = (float)mResultView.getWidth() / bitmap.getWidth();
        float ivScaleY = (float)mResultView.getHeight() / bitmap.getHeight();

        final ArrayList<Result> results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0, 0);
        return new AnalysisResult(results);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
        }
    }
}
