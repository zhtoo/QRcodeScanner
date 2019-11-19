package com.zht.zxingcode;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

import com.zht.qrcodescanner.FinishListener;
import com.zht.qrcodescanner.activity.CaptureActivity;

public class MainActivity extends CaptureActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.qrcode_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        //设置扫描范围的View，不设置将使用默认的扫描范围（屏幕的宽度的8/15）
        setScanRange(findViewById(R.id.qrcode_scan_range));
        //设置扫描镭射线View
        setScanLaser(findViewById(R.id.qrcode_scan_laser));
        findViewById(R.id.qrcode_scan_range).post(new Runnable() {
            @Override
            public void run() {
                if (mScanLaser == null || mScanRange == null) {
                    return;
                }
                float v = mScanRange.getHeight();
                ObjectAnimator mLaserAnimator = ObjectAnimator
                        .ofFloat(mScanLaser,
                                "translationY", 0, v);
                //设置延迟时间
                mLaserAnimator.setDuration(2000);
                //设置循环次数
                mLaserAnimator.setRepeatCount(ValueAnimator.INFINITE);
                mLaserAnimator.setRepeatMode(ValueAnimator.RESTART);
                mLaserAnimator.start();
                //设置扫描镭射线动画
                setScanLaserAnimator(mLaserAnimator);
            }
        });
    }

    /**
     * 处理扫描后的结果，这里必须要自己处理
     * @param resultText
     */
    @Override
    public void handlerResult(String resultText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(com.zht.qrcodescanner.R.string.app_name));
        builder.setMessage(resultText);
        builder.setPositiveButton(com.zht.qrcodescanner.R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //重新开始扫描
                restartPreviewAfterDelay(0L);
            }
        });
        builder.setOnCancelListener(new FinishListener(this));
        builder.show();
}
}
