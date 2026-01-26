package jaygoo.widget.wlv;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.lang.ref.WeakReference;

/**
 * 封装的SurfaceView，用于波形渲染线程管理（移植自说点啥同名实现）。
 */
public abstract class RenderView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "RenderView";

    private boolean isStartAnim = false;
    private static final Object surfaceLock = new Object();
    private RenderThread renderThread;

    protected abstract void doDrawBackground(Canvas canvas);

    protected abstract void onRender(Canvas canvas, long millisPassed);

    public RenderView(Context context) { this(context, null); }

    public RenderView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public RenderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getHolder().addCallback(this);
    }

    private static class RenderThread extends Thread {
        private static final long SLEEP_TIME = 16;
        private final WeakReference<RenderView> renderView;
        private boolean running = false;
        private boolean destroyed = false;
        private boolean isPause = false;

        public RenderThread(RenderView renderView) {
            super("RenderThread");
            this.renderView = new WeakReference<>(renderView);
        }

        private SurfaceHolder getSurfaceHolder() {
            RenderView rv = renderView.get();
            return rv != null ? rv.getHolder() : null;
        }

        private RenderView getRenderView() { return renderView.get(); }

        @Override
        public void run() {
            long startAt = System.currentTimeMillis();
            while (!destroyed) {
                synchronized (surfaceLock) {
                    while (isPause) {
                        try {
                            surfaceLock.wait();
                        } catch (InterruptedException e) {
                            // interruption is used to wake the render loop
                            isPause = false;
                        }
                    }
                    if (running) {
                        SurfaceHolder holder = getSurfaceHolder();
                        RenderView rv = getRenderView();
                        if (holder != null && rv != null) {
                            Canvas canvas = holder.lockCanvas();
                            if (canvas != null) {
                                rv.doDrawBackground(canvas);
                                if (rv.isStartAnim) {
                                    rv.render(canvas, System.currentTimeMillis() - startAt);
                                }
                                holder.unlockCanvasAndPost(canvas);
                            }
                        } else {
                            running = false;
                        }
                    }
                }
                SystemClock.sleep(SLEEP_TIME);
            }
        }

        public void setRun(boolean isRun) { this.running = isRun; }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        renderThread = new RenderThread(this);
        if (isStartAnim) startThread();
    }

    public void onResume() {
        synchronized (surfaceLock) {
            if (renderThread != null) {
                renderThread.isPause = false;
                surfaceLock.notifyAll();
            }
        }
    }

    public void onPause() {
        synchronized (surfaceLock) { if (renderThread != null) renderThread.isPause = true; }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        synchronized (surfaceLock) {
            if (renderThread != null) {
                renderThread.setRun(false);
                renderThread.destroyed = true;
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && isStartAnim) startAnim(); else startThread();
    }

    private void render(Canvas canvas, long millisPassed) { onRender(canvas, millisPassed); }

    public void startAnim() { isStartAnim = true; startThread(); }

    private void startThread() {
        if (renderThread != null && !renderThread.running) {
            renderThread.setRun(true);
            if (renderThread.getState() == Thread.State.NEW) {
                try {
                    renderThread.start();
                } catch (IllegalThreadStateException e) {
                    Log.w(TAG, "RenderThread start failed", e);
                }
            }
        }
    }

    public void stopAnim() {
        isStartAnim = false;
        if (renderThread != null && renderThread.running) {
            renderThread.setRun(false);
            renderThread.interrupt();
        }
    }

    public boolean isRunning() { return renderThread != null && renderThread.running; }

    public void release() {
        if (getHolder() != null && getHolder().getSurface() != null) {
            getHolder().getSurface().release();
            getHolder().removeCallback(this);
        }
    }
}
