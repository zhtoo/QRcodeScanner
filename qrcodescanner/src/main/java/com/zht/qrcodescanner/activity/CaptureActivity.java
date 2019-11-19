package com.zht.qrcodescanner.activity;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.zht.qrcodescanner.AmbientLightManager;
import com.zht.qrcodescanner.BeepManager;
import com.zht.qrcodescanner.CaptureHandler;
import com.zht.qrcodescanner.FinishListener;
import com.zht.qrcodescanner.InactivityTimer;
import com.zht.qrcodescanner.R;
import com.zht.qrcodescanner.ViewfinderView;
import com.zht.qrcodescanner.camera.CameraManager;

import java.io.IOException;
import java.util.Collection;

public class CaptureActivity extends Activity
        implements SurfaceHolder.Callback {

    private static final String TAG = "CaptureActivity";
    private boolean hasSurface;//是否持有Surface
    private CameraManager cameraManager;//这里zxing是使用自己的CameraManager类
    private CaptureHandler handler;

    //子类的视图
    private FrameLayout mContentLayout;

    private ViewfinderView viewfinderView;
    private Result lastResult;
    private Collection<BarcodeFormat> decodeFormats;
    private String characterSet;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;
    private AmbientLightManager ambientLightManager;

    public View mScanRange;
    public View mScanLaser;
    private ObjectAnimator mLaserAnimator;

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        //保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //设置透明状态栏
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        //调用父类的setContentView
        super.setContentView(R.layout.activity_capture);

        mContentLayout = findViewById(R.id.layout_content);

        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);
        ambientLightManager = new AmbientLightManager(this);

        View mStatusBarReplacement = findViewById(R.id.vStatusBarReplacement);
        LinearLayout.LayoutParams layoutParams =
                (LinearLayout.LayoutParams) mStatusBarReplacement.getLayoutParams();
        layoutParams.height = getStatusHeightIfNeed(mStatusBarReplacement.getContext());
        mStatusBarReplacement.setLayoutParams(layoutParams);
    }

    public void setScanRange(View scanRange) {
        this.mScanRange = scanRange;
    }

    public void setScanLaser(View scanLaser) {
        this.mScanLaser = scanLaser;
    }

    public void setScanLaserAnimator(ObjectAnimator laseranimator) {
        this.mLaserAnimator = laseranimator;
    }

    @Override
    public void setContentView(int layoutResID) {
        mContentLayout.removeAllViews();
        View.inflate(this, layoutResID, mContentLayout);
        onContentChanged();
    }

    @Override
    public void setContentView(View view) {
        mContentLayout.removeAllViews();
        mContentLayout.addView(view);
        onContentChanged();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        mContentLayout.removeAllViews();
        mContentLayout.addView(view, params);
        onContentChanged();
    }

    private void startAnim() {
        if (mLaserAnimator == null) {
            return;
        }
        mLaserAnimator.start();
//
//
//        float v = TypedValue.applyDimension(
//                TypedValue.COMPLEX_UNIT_DIP,
//                258F, mScanLaser.getResources().getDisplayMetrics());
//        mLaserAnimator = ObjectAnimator.ofFloat(mScanLaser,
//                "translationY", 0, v);
//        //设置延迟时间
//        mLaserAnimator.setDuration(2000);
//        //设置循环次数
//        mLaserAnimator.setRepeatCount(ValueAnimator.INFINITE);
//        mLaserAnimator.setRepeatMode(ValueAnimator.RESTART);
//        mLaserAnimator.start();
    }

    public static int getStatusBarHeight(Context context) {
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
        int height = resources.getDimensionPixelSize(resourceId);
        return height;
    }

    /**
     * 获取手机状态栏的高度
     *
     * @param context
     * @return
     */
    public static int getStatusHeightIfNeed(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return getStatusBarHeight(context);
        }
        return 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        onResumeHandlerCamera();
    }

    private void onResumeHandlerCamera() {
        cameraManager = new CameraManager(getApplication());
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);

        if (mScanRange != null) {
            cameraManager.setScanRangeRect(mScanRange);
        }

        viewfinderView.setCameraManager(cameraManager);
        startAnim();
        handler = null;
        resetStatusView();
        beepManager.updatePrefs();
        ambientLightManager.start(cameraManager);
        inactivityTimer.onResume();
        decodeFormats = null;
        characterSet = null;

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            //activity已暂停但未停止，所以surface仍然存在。
            // 因此不会调用surfaceCreated（），在此处只需初始化摄影机。
            initCamera(surfaceHolder);
        } else {
            //添加回调并等待surfaceCreated（）初始化相机。
            surfaceHolder.addCallback(this);
        }

    }

    private double startFingerSpacing;

    /**
     * 监听缩放手势
     *
     * @param event
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() == 2) {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
//                case MotionEvent.ACTION_POINTER_DOWN:
//                    startFingerSpacing = getFingerSpacing(event);
//                    break;
//                case MotionEvent.ACTION_MOVE:
//                    double targetZoomRatio =
//                            getFingerSpacing(event) / startFingerSpacing;
//                    cameraManager.setZoom( targetZoomRatio);
//                    break;
//                case MotionEvent.ACTION_UP:
//                    this.startFingerSpacing = 0D;
//                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    startFingerSpacing = getFingerSpacing(event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    double newFingerSpacing = getFingerSpacing(event);
                    if (newFingerSpacing > startFingerSpacing) {
                        cameraManager.handleZoom(true);//放大
                    } else if (newFingerSpacing < startFingerSpacing) {
                        cameraManager.handleZoom(false);//缩小
                    }
                    startFingerSpacing = newFingerSpacing;
                    break;
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private static double getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return Math.sqrt(x * x + y * y);
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        ambientLightManager.stop();
        if (mLaserAnimator != null) {
            mLaserAnimator.cancel();
        }
        beepManager.close();
        cameraManager.closeDriver();
        //historyManager = null; // Keep for onActivityResult
        if (!hasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mLaserAnimator != null) {
            mLaserAnimator.cancel();
        }
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    /**
     * 初始化相机
     *
     * @param surfaceHolder
     */
    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }

        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);

            // Creating the handler starts the preview, which can also throw a RuntimeException.
            if (handler == null) {
                handler = new CaptureHandler(this,
                        decodeFormats,
                        null,
                        characterSet,
                        cameraManager);
            }
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    private void displayFrameworkBugMessageAndExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage(getString(R.string.msg_camera_framework_bug));
        builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
        builder.setOnCancelListener(new FinishListener(this));
        builder.show();
    }

    /**
     * surface被创建
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "surfaceCreated: ");
        if (holder == null) {
            Log.e(TAG, "*** WARNING *** " + "surfaceCreated() gave us a null surface!");
        }
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    /**
     * surface发生改变
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // do nothing
    }

    /**
     * surface被销毁
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    /**
     * A valid barcode has been found, so give an indication of success and show the results.
     *
     * @param rawResult   The contents of the barcode.
     * @param scaleFactor amount by which thumbnail was scaled
     * @param barcode     A greyscale bitmap of the camera data which was decoded.
     */
    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        inactivityTimer.onActivity();
        lastResult = rawResult;

        String text = lastResult.getText();

        if (TextUtils.isEmpty(text)) {
            return;
        }
        if (mLaserAnimator != null) {
            mLaserAnimator.cancel();
        }
        //播放声音
        beepManager.playBeepSoundAndVibrate();
        //获取到结果
        handlerResult(text);
    }

    public void handlerResult(String resultText) {

    }

    /*public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }*/

    public void restartPreviewAfterDelay(long delayMS) {
        if (handler != null) {
            handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
        }
        resetStatusView();
    }

    private void resetStatusView() {
        viewfinderView.setVisibility(View.VISIBLE);
        if (mLaserAnimator != null) {
            mLaserAnimator.start();
        }
        lastResult = null;
    }

}
