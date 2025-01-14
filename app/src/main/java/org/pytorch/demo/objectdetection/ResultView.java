package org.pytorch.demo.objectdetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.MotionEvent;
import android.view.GestureDetector;

import java.util.ArrayList;

public class ResultView extends View {

    private final static int TEXT_X = 40;
    private final static int TEXT_Y = 35;
    private final static int TEXT_WIDTH = 260;
    private final static int TEXT_HEIGHT = 50;
    private GestureDetector gestureDetector;
    private static final long DOUBLE_TAP_TIME_DELTA = 300; //milliseconds
    private long lastTapTime = 0;
    private boolean isWaitingForDoubleTap = false;


    private Paint touchPaint = new Paint();

    private Paint mPaintRectangle;
    private Paint mPaintText;
    public ArrayList<Result> mResults;
    private float touchX = -1, touchY = -1;

    public ResultView(Context context) {
        super(context);
    }

    public ResultView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaintRectangle = new Paint();
        mPaintRectangle.setColor(Color.YELLOW);
        mPaintText = new Paint();

        touchPaint.setColor(Color.RED);
        touchPaint.setStyle(Paint.Style.STROKE);
        touchPaint.setStrokeWidth(10f);


        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });


    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mResults == null) return;
        for (Result result : mResults) {
            mPaintRectangle.setStrokeWidth(5);
            mPaintRectangle.setStyle(Paint.Style.STROKE);
            canvas.drawRect(result.rect, mPaintRectangle);

            Path mPath = new Path();
            RectF mRectF = new RectF(result.rect.left, result.rect.top, result.rect.left + TEXT_WIDTH, result.rect.top + TEXT_HEIGHT);
            mPath.addRect(mRectF, Path.Direction.CW);
            mPaintText.setColor(Color.MAGENTA);
            canvas.drawPath(mPath, mPaintText);

            mPaintText.setColor(Color.WHITE);
            mPaintText.setStrokeWidth(0);
            mPaintText.setStyle(Paint.Style.FILL);
            mPaintText.setTextSize(32);
            canvas.drawText(String.format("%s %.2f", PrePostProcessor.mClasses[result.classIndex], result.score), result.rect.left + TEXT_X, result.rect.top + TEXT_Y, mPaintText);
        }

    }

    public void setResults(ArrayList<Result> results) {
        mResults = results;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchX = event.getX();
            touchY = event.getY();
            invalidate();

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTapTime < DOUBLE_TAP_TIME_DELTA) {
                // 더블 탭 감지
                ((ObjectDetectionActivity) getContext()).speakDetails();
            } else {
                // 싱글 탭
                ((ObjectDetectionActivity) getContext()).speakLabels();
            }
            lastTapTime = currentTime;

        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            touchX = -1;
            touchY = -1;
            invalidate();
        }

        return true;
    }
}