/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zht.zxingcode.QRcode;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPointCallback;
import com.zht.zxingcode.CaptureActivity;
import com.zht.zxingcode.QRcode.config.ConfigManager;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the heavy lifting of decoding the images.
 * <p>
 * 该线程完成了解码图像的所有繁重工作。
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
final class DecodeThread extends Thread {

    public static final String BARCODE_BITMAP = "barcode_bitmap";
    public static final String BARCODE_SCALED_FACTOR = "barcode_scaled_factor";

    private final CaptureActivity activity;
    private final Map<DecodeHintType, Object> hints;
    private Handler handler;
    private final CountDownLatch handlerInitLatch;

    public DecodeThread(CaptureActivity activity,
                        Collection<BarcodeFormat> decodeFormats,
                        Map<DecodeHintType, ?> baseHints,
                        String characterSet,
                        ResultPointCallback resultPointCallback) {

        this.activity = activity;
        handlerInitLatch = new CountDownLatch(1);

        hints = new EnumMap<>(DecodeHintType.class);
        if (baseHints != null) {
            hints.putAll(baseHints);
        }

        // The prefs can't change while the thread is running, so pick them up once here.
        // 当线程正在运行时，配置选项无法更改，因此请在此处进行一次选择。
        if (decodeFormats == null || decodeFormats.isEmpty()) {

            /**
             * Modified by ZhangHaitao at 2019/11/8  15:32.
             * 当前项目只需要解码 二维码，对于一维条形码不做处理。
             */
            decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
//      if (ConfigManager.getBoolean(ConfigManager.KEY_DECODE_1D_PRODUCT, true)) {
//        decodeFormats.addAll(DecodeFormatManager.PRODUCT_FORMATS);
//      }
//      if (ConfigManager.getBoolean(ConfigManager.KEY_DECODE_1D_INDUSTRIAL, true)) {
//        decodeFormats.addAll(DecodeFormatManager.INDUSTRIAL_FORMATS);
//      }
            if (ConfigManager.getBoolean(ConfigManager.KEY_DECODE_QR, true)) {
                decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
            }
            if (ConfigManager.getBoolean(ConfigManager.KEY_DECODE_DATA_MATRIX, true)) {
                decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS);
            }
//      if (ConfigManager.getBoolean(ConfigManager.KEY_DECODE_AZTEC, false)) {
//        decodeFormats.addAll(DecodeFormatManager.AZTEC_FORMATS);
//      }
//      if (ConfigManager.getBoolean(ConfigManager.KEY_DECODE_PDF417, false)) {
//        decodeFormats.addAll(DecodeFormatManager.PDF417_FORMATS);
//      }
        }
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);

        if (characterSet != null) {
            hints.put(DecodeHintType.CHARACTER_SET, characterSet);
        }
        hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
    }

    public Handler getHandler() {
        try {
            handlerInitLatch.await();
        } catch (InterruptedException ie) {
            // continue?
        }
        return handler;
    }

    @Override
    public void run() {
        Looper.prepare();
        //创建一个解码的Handler
        handler = new DecodeHandler(activity, hints);
        handlerInitLatch.countDown();
        Looper.loop();
    }

}
