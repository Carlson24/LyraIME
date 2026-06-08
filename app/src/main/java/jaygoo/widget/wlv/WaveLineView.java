package jaygoo.widget.wlv;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;

import com.osfans.trime.R;

import java.util.ArrayList;
import java.util.List;

/**
 * WaveLineView（移植自说点啥/小企鹅），绘制音量驱动的波形动画。
 */
public class WaveLineView extends RenderView {

    private static final String TAG = "WaveLineView";

    private static final int DEFAULT_SAMPLING_SIZE = 64;
    private static final float DEFAULT_OFFSET_SPEED = 250F;
    private static final int DEFAULT_SENSIBILITY = 5;

    private int samplingSize;
    private float offsetSpeed;
    private float volume = 0;
    private int targetVolume = 50;
    private float perVolume;
    private int sensibility;
    private int backGroundColor = Color.WHITE;
    private int lineColor;
    private int thickLineWidth;
    private int fineLineWidth;

    private final Paint paint = new Paint();
    { paint.setDither(true); paint.setAntiAlias(true); }

    private final List<Path> paths = new ArrayList<>();
    { for (int i = 0; i < 4; i++) paths.add(new Path()); }

    private final float[] pathFuncs = { 0.6f, 0.35f, 0.1f, -0.1f };

    private float[] samplingX;
    private float[] mapX;
    private int width, height;
    private int centerHeight;
    private float amplitude;
    private final SparseArray<Double> recessionFuncs = new SparseArray<>();
    private boolean isPrepareLineAnimEnd = false;
    private int lineAnimX = 0;
    private float prepareAlpha = 0f;
    private boolean isOpenPrepareAnim = false;
    private boolean isTransparentMode = false;

    public WaveLineView(Context context) { this(context, null); }
    public WaveLineView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public WaveLineView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initAttr(attrs);
    }

    private void initAttr(AttributeSet attrs) {
        TypedArray t = getContext().obtainStyledAttributes(attrs, R.styleable.WaveLineView);
        backGroundColor = t.getColor(R.styleable.WaveLineView_wlvBackgroundColor, Color.WHITE);
        samplingSize = t.getInt(R.styleable.WaveLineView_wlvSamplingSize, DEFAULT_SAMPLING_SIZE);
        lineColor = t.getColor(R.styleable.WaveLineView_wlvLineColor, Color.parseColor("#2ED184"));
        thickLineWidth = (int) t.getDimension(R.styleable.WaveLineView_wlvThickLineWidth, 6);
        fineLineWidth = (int) t.getDimension(R.styleable.WaveLineView_wlvFineLineWidth, 2);
        offsetSpeed = t.getFloat(R.styleable.WaveLineView_wlvMoveSpeed, DEFAULT_OFFSET_SPEED);
        sensibility = t.getInt(R.styleable.WaveLineView_wlvSensibility, DEFAULT_SENSIBILITY);
        isTransparentMode = backGroundColor == Color.TRANSPARENT;
        t.recycle();

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(lineColor);
        paint.setStrokeWidth(thickLineWidth);

        setZOrderOnTop(true);
        if (getHolder() != null) {
            getHolder().setFormat(PixelFormat.TRANSLUCENT);
        }
    }

    @Override
    protected void doDrawBackground(Canvas canvas) {
        if (isTransparentMode) {
            canvas.drawColor(backGroundColor, PorterDuff.Mode.CLEAR);
        } else {
            canvas.drawColor(backGroundColor);
        }
    }

    private boolean isParametersNull() { return samplingX == null || mapX == null; }

    @Override
    protected void onRender(Canvas canvas, long millisPassed) {
        float offset = millisPassed / offsetSpeed;

        if (isParametersNull()) initDraw(canvas);

        if (lineAnim(canvas)) {
            resetPaths();
            softerChangeVolume();

            float curY;
            for (int i = 0; i <= samplingSize; i++) {
                if (isParametersNull()) {
                    initDraw(canvas);
                    if (isParametersNull()) return;
                }
                float x = samplingX[i];
                curY = (float) (amplitude * calcValue(mapX[i], offset));
                for (int n = 0; n < paths.size(); n++) {
                    float realY = curY * pathFuncs[n] * volume * 0.01f;
                    paths.get(n).lineTo(x, centerHeight + realY);
                }
            }

            for (int i = 0; i < paths.size(); i++) {
                paths.get(i).moveTo(width, centerHeight);
            }

            for (int n = 0; n < paths.size(); n++) {
                if (n == 0) {
                    paint.setStrokeWidth(thickLineWidth);
                    paint.setAlpha((int) (255 * alphaInAnim()));
                } else {
                    paint.setStrokeWidth(fineLineWidth);
                    paint.setAlpha((int) (100 * alphaInAnim()));
                }
                canvas.drawPath(paths.get(n), paint);
            }
        }
    }

    private void checkVolumeValue() {
        if (targetVolume > 100) targetVolume = 100;
        if (targetVolume < 0) targetVolume = 0;
    }

    private void checkSensibilityValue() {
        if (sensibility > 10) sensibility = 10;
        if (sensibility < 1) sensibility = 1;
    }

    private void softerChangeVolume() {
        if (volume < targetVolume - perVolume) {
            volume += perVolume;
        } else if (volume > targetVolume + perVolume) {
            if (volume < perVolume * 2) {
                volume = perVolume * 2;
            } else {
                volume -= perVolume;
            }
        } else {
            volume = targetVolume;
        }
    }

    private float alphaInAnim() {
        if (!isOpenPrepareAnim) return 1;
        if (prepareAlpha < 1f) {
            prepareAlpha += 0.02f;
        } else {
            prepareAlpha = 1;
        }
        return prepareAlpha;
    }

    private boolean lineAnim(Canvas canvas) {
        if (isPrepareLineAnimEnd || !isOpenPrepareAnim) return true;
        paths.get(0).moveTo(0, centerHeight);
        paths.get(1).moveTo(width, centerHeight);

        for (int i = 1; i <= samplingSize; i++) {
            float x = 1f * i * lineAnimX / samplingSize;
            paths.get(0).lineTo(x, centerHeight);
            paths.get(1).lineTo(width - x, centerHeight);
        }

        paths.get(0).moveTo(width / 2f, centerHeight);
        paths.get(1).moveTo(width / 2f, centerHeight);

        lineAnimX += width / 60;
        canvas.drawPath(paths.get(0), paint);
        canvas.drawPath(paths.get(1), paint);

        if (lineAnimX > width / 2) {
            isPrepareLineAnimEnd = true;
            return true;
        }
        return false;
    }

    private void resetPaths() {
        for (int i = 0; i < paths.size(); i++) {
            paths.get(i).rewind();
            paths.get(i).moveTo(0, centerHeight);
        }
    }

    private void initParameters() {
        lineAnimX = 0;
        prepareAlpha = 0f;
        isPrepareLineAnimEnd = false;
        samplingX = null;
    }

    @Override
    public void startAnim() {
        initParameters();
        super.startAnim();
    }

    @Override
    public void stopAnim() {
        super.stopAnim();
        clearDraw();
    }

    public void clearDraw() {
        Canvas canvas = null;
        try {
            canvas = getHolder().lockCanvas(null);
            if (canvas == null) return;
            canvas.drawColor(backGroundColor);
            resetPaths();
            for (int i = 0; i < paths.size(); i++) {
                canvas.drawPath(paths.get(i), paint);
            }
        } catch (Exception e) {
            Log.w(TAG, "clearDraw failed", e);
        } finally {
            if (canvas != null) {
                getHolder().unlockCanvasAndPost(canvas);
            }
        }
    }

    private void initDraw(Canvas canvas) {
        width = canvas.getWidth();
        height = canvas.getHeight();

        if (width == 0 || height == 0 || samplingSize == 0) return;

        centerHeight = height >> 1;
        amplitude = height / 3.0f;
        perVolume = sensibility * 0.35f;

        samplingX = new float[samplingSize + 1];
        mapX = new float[samplingSize + 1];
        float gap = width / (float) samplingSize;
        float x;
        for (int i = 0; i <= samplingSize; i++) {
            x = i * gap;
            samplingX[i] = x;
            mapX[i] = (x / (float) width) * 4 - 2;
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(lineColor);
        paint.setStrokeWidth(thickLineWidth);
    }

    private double calcValue(float mapX, float offset) {
        int keyX = (int) (mapX * 1000);
        offset %= 2;
        double sinFunc = Math.sin(Math.PI * mapX - offset * Math.PI);
        double recessionFunc;
        if (recessionFuncs.indexOfKey(keyX) >= 0) {
            recessionFunc = recessionFuncs.get(keyX);
        } else {
            recessionFunc = 4 / (4 + Math.pow(mapX, 4));
            recessionFuncs.put(keyX, recessionFunc);
        }
        return sinFunc * recessionFunc;
    }

    public void setMoveSpeed(float moveSpeed) { this.offsetSpeed = moveSpeed; }

    public void setVolume(int volume) {
        if (Math.abs(targetVolume - volume) > perVolume) {
            this.targetVolume = volume;
            checkVolumeValue();
        }
    }

    public void setBackGroundColor(int backGroundColor) {
        this.backGroundColor = backGroundColor;
        this.isTransparentMode = (backGroundColor == Color.TRANSPARENT);
    }

    public void setLineColor(int lineColor) { this.lineColor = lineColor; }

    public void setSensibility(int sensibility) {
        this.sensibility = sensibility;
        checkSensibilityValue();
    }
}
