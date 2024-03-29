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

package com.zht.qrcodescanner.camera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;


import com.google.zxing.PlanarYUVLuminanceSource;
import com.zht.qrcodescanner.camera.open.OpenCamera;
import com.zht.qrcodescanner.camera.open.OpenCameraInterface;
import com.zht.qrcodescanner.config.CameraConfigurationUtils;

import java.io.IOException;


/**
 * This object wraps the Camera service object and expects to be the only one talking to it. The
 * implementation encapsulates the steps needed to take preview-sized images, which are used for
 * both preview and decoding.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
@SuppressWarnings("deprecation") // camera APIs
public final class CameraManager {

    private static final String TAG = CameraManager.class.getSimpleName();

    private static final int MIN_FRAME_WIDTH = 240;//最小宽度
    private static final int MIN_FRAME_HEIGHT = 240;//最小高度
    private static final int MAX_FRAME_WIDTH = 1920; //1200 = 5/8 * 1920
    private static final int MAX_FRAME_HEIGHT = 1080; // 675= 5/8 * 1080

    private View mScanRange;

    private final Context context;
    private final CameraConfigurationManager configManager;
    private OpenCamera camera;
    private AutoFocusManager autoFocusManager;
    private Rect framingRect;
    private Rect framingRectInPreview;
    private boolean initialized;
    private boolean previewing;
    private int requestedCameraId = OpenCameraInterface.NO_REQUESTED_CAMERA;
    private int requestedFramingRectWidth;
    private int requestedFramingRectHeight;
    /**
     * Preview frames are delivered here, which we pass on to the registered handler. Make sure to
     * clear the handler so it will only receive one message.
     */
    private final PreviewCallback previewCallback;

    public CameraManager(Context context) {
        this.context = context;
        this.configManager = new CameraConfigurationManager(context);
        previewCallback = new PreviewCallback(configManager);
    }

    /**
     * Opens the camera driver and initializes the hardware parameters.
     *
     * @param holder The surface object which the camera will draw preview frames into.
     * @throws IOException Indicates the camera driver failed to open.
     */
    public synchronized void openDriver(SurfaceHolder holder) throws IOException {
        OpenCamera theCamera = camera;
        if (theCamera == null) {
            theCamera = OpenCameraInterface.open(requestedCameraId);
            if (theCamera == null) {
                throw new IOException("Camera.open() failed to return object from driver");
            }
            camera = theCamera;
        }

        if (!initialized) {
            initialized = true;
            configManager.initFromCameraParameters(theCamera);
            if (requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0) {
                setManualFramingRect(requestedFramingRectWidth, requestedFramingRectHeight);
                requestedFramingRectWidth = 0;
                requestedFramingRectHeight = 0;
            }
        }

        Camera cameraObject = theCamera.getCamera();
        Camera.Parameters parameters = cameraObject.getParameters();
        String parametersFlattened = parameters == null ? null : parameters.flatten(); // Save these, temporarily
        try {
            configManager.setDesiredCameraParameters(theCamera, false);
        } catch (RuntimeException re) {
            // Driver failed
            Log.w(TAG, "Camera rejected parameters. Setting only minimal safe-mode parameters");
            Log.i(TAG, "Resetting to saved camera params: " + parametersFlattened);
            // Reset:
            if (parametersFlattened != null) {
                parameters = cameraObject.getParameters();
                parameters.unflatten(parametersFlattened);
                try {
                    cameraObject.setParameters(parameters);
                    configManager.setDesiredCameraParameters(theCamera, true);
                } catch (RuntimeException re2) {
                    // Well, darn. Give up
                    Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
                }
            }
        }
        cameraObject.setPreviewDisplay(holder);

    }

    public synchronized boolean isOpen() {
        return camera != null;
    }

    /**
     * Closes the camera driver if still in use.
     */
    public synchronized void closeDriver() {
        if (camera != null) {
            camera.getCamera().release();
            camera = null;
            // Make sure to clear these each time we close the camera, so that any scanning rect
            // requested by intent is forgotten.
            framingRect = null;
            framingRectInPreview = null;
        }
    }

    /**
     * Asks the camera hardware to begin drawing preview frames to the screen.
     */
    public synchronized void startPreview() {
        OpenCamera theCamera = camera;
        if (theCamera != null && !previewing) {
            theCamera.getCamera().startPreview();
            previewing = true;
            autoFocusManager = new AutoFocusManager(context, theCamera.getCamera());
        }
    }

    /**
     * Tells the camera to stop drawing preview frames.
     */
    public synchronized void stopPreview() {
        if (autoFocusManager != null) {
            autoFocusManager.stop();
            autoFocusManager = null;
        }
        if (camera != null && previewing) {
            camera.getCamera().stopPreview();
            previewCallback.setHandler(null, 0);
            previewing = false;
        }
    }

    /**
     * @param newSetting if {@code true}, light should be turned on if currently off. And vice versa.
     */
    public synchronized void setTorch(boolean newSetting) {
        OpenCamera theCamera = camera;
        if (theCamera != null && newSetting != configManager.getTorchState(theCamera.getCamera())) {
            boolean wasAutoFocusManager = autoFocusManager != null;
            if (wasAutoFocusManager) {
                autoFocusManager.stop();
                autoFocusManager = null;
            }
            configManager.setTorch(theCamera.getCamera(), newSetting);
            if (wasAutoFocusManager) {
                autoFocusManager = new AutoFocusManager(context, theCamera.getCamera());
                autoFocusManager.start();
            }
        }
    }

    /**
     * A single preview frame will be returned to the handler supplied. The data will arrive as byte[]
     * in the message.obj field, with width and height encoded as message.arg1 and message.arg2,
     * respectively.
     *
     * @param handler The handler to send the message to.
     * @param message The what field of the message to be sent.
     */
    public synchronized void requestPreviewFrame(Handler handler, int message) {
        OpenCamera theCamera = camera;
        if (theCamera != null && previewing) {
            previewCallback.setHandler(handler, message);
            theCamera.getCamera().setOneShotPreviewCallback(previewCallback);
        }
    }

    /**
     * Calculates the framing rect which the UI should draw to show the user where to place the
     * barcode. This target helps with alignment as well as forces the user to hold the device
     * far enough away to ensure the image will be in focus.
     *
     * @return The rectangle to draw on screen in window coordinates.
     */
    /**
     * 获取扫码范围，中间方框的大小
     */
    public synchronized Rect getFramingRect() {
        if (framingRect == null) {
            if (camera == null) {
                return null;
            }

            if (mScanRange != null) {

                int[] location = new int[2];
                mScanRange.getLocationOnScreen(location);
                int x = location[0];
                int y = location[1];

                if (x != 0 && y != 0) {
                    int width = mScanRange.getWidth();
                    int height = mScanRange.getHeight();
                    framingRect = new Rect(
                            x,
                            y,
                            x + width,
                            y + height);
                    return framingRect;
                }
            }


            Point screenResolution = configManager.getScreenResolution();
            if (screenResolution == null) {
                // Called early, before init even finished
                return null;
            }

            int width = findDesiredDimensionInWidthRange(
                    screenResolution.x,
                    MIN_FRAME_WIDTH,
                    MAX_FRAME_WIDTH);
            /*int height = findDesiredDimensionInHeightRange(
                    screenResolution.y,
                    MIN_FRAME_HEIGHT,
                    MAX_FRAME_HEIGHT);*/
            int height = width;
            int topOffset = findDesiredDimensionInHeightRange(
                    screenResolution.y,
                    MIN_FRAME_HEIGHT,
                    MAX_FRAME_HEIGHT);

            //确保是正方形的框，zxing最坑的地方了，不看源码还真不知道影响
//            int min = Math.min(width, height);
//            width = min;
//            height = min;
            int leftOffset = (screenResolution.x - width) / 2;
            framingRect = new Rect(
                    leftOffset,
                    topOffset,
                    leftOffset + width,
                    topOffset + height);
            Log.d(TAG, "Calculated framing rect: " + framingRect);
        }
        return framingRect;
    }

    private static int findDesiredDimensionInRange(int resolution, int hardMin, int hardMax) {
        int dim = 5 * resolution / 8; // Target 5/8 of each dimension
        if (dim < hardMin) {
            return hardMin;
        }
        return Math.min(dim, hardMax);
    }

    /**
     * 指定宽度范围
     */
    private static int findDesiredDimensionInWidthRange(int resolution, int hardMin, int hardMax) {
        int dim = 258 * resolution / 375; // Target 5/8 of each dimension
        if (dim < hardMin) {
            return hardMin;
        }
        return Math.min(dim, hardMax);
    }

    /**
     * 指定高度范围
     */
    private static int findDesiredDimensionInHeightRange(int resolution, int hardMin, int hardMax) {
        int dim = 164 * resolution / 668; // Target 5/8 of each dimension
        if (dim < hardMin) {
            return hardMin;
        }
        return Math.min(dim, hardMax);
    }


    /**
     * Like {@link #getFramingRect} but coordinates are in terms of the preview frame,
     * not UI / screen.
     *
     * @return {@link Rect} expressing barcode scan area in terms of the preview size
     */
    public synchronized Rect getFramingRectInPreview() {
        if (framingRectInPreview == null) {
            Rect framingRect = getFramingRect();
            if (framingRect == null) {
                return null;
            }
            Rect rect = new Rect(framingRect);
            Point cameraResolution = configManager.getCameraResolution();
            Point screenResolution = configManager.getScreenResolution();
            if (cameraResolution == null || screenResolution == null) {
                // Called early, before init even finished
                return null;
            }
            //此处源代码有bug
            //相机的旋转角度和手机屏幕的旋转角度是不一致的
            //导致 cameraResolution的xy和screenResolution的xy有出入
            //在此进行修复
            boolean isCameraAspectRatio = cameraResolution.x < cameraResolution.y;
            boolean isScreenAspectRatio = screenResolution.x < screenResolution.y;
            if (isCameraAspectRatio == isScreenAspectRatio) {
                rect.left = framingRect.left * cameraResolution.x / screenResolution.x;
                rect.right = framingRect.right * cameraResolution.x / screenResolution.x;
                rect.top = framingRect.top * cameraResolution.y / screenResolution.y;
                rect.bottom = framingRect.bottom * cameraResolution.y / screenResolution.y;
            } else {
                rect.left = framingRect.top * cameraResolution.x / screenResolution.y;
                rect.right = framingRect.bottom * cameraResolution.x / screenResolution.y;
                rect.top = framingRect.left * cameraResolution.y / screenResolution.x;
                rect.bottom = framingRect.right * cameraResolution.y / screenResolution.x;
            }
            framingRectInPreview = rect;
        }
        return framingRectInPreview;
    }


    /**
     * Allows third party apps to specify the camera ID, rather than determine
     * it automatically based on available cameras and their orientation.
     *
     * @param cameraId camera ID of the camera to use. A negative value means "no preference".
     */
    public synchronized void setManualCameraId(int cameraId) {
        requestedCameraId = cameraId;
    }

    /**
     * Allows third party apps to specify the scanning rectangle dimensions, rather than determine
     * them automatically based on screen resolution.
     *
     * @param width  The width in pixels to scan.
     * @param height The height in pixels to scan.
     */
    public synchronized void setManualFramingRect(int width, int height) {
        if (initialized) {
            Point screenResolution = configManager.getScreenResolution();
            if (width > screenResolution.x) {
                width = screenResolution.x;
            }
            if (height > screenResolution.y) {
                height = screenResolution.y;
            }
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
            Log.d(TAG, "Calculated manual framing rect: " + framingRect);
            framingRectInPreview = null;
        } else {
            requestedFramingRectWidth = width;
            requestedFramingRectHeight = height;
        }
    }

    /**
     * A factory method to build the appropriate LuminanceSource object based on the format
     * of the preview buffers, as described by Camera.Parameters.
     *
     * @param data   A preview frame.
     * @param width  The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data,
                                                         int width,
                                                         int height) {
        //获取解码范围的Rect
        Rect rect = getFramingRectInPreview();
        if (rect == null) {
            return null;
        }
        // Go ahead and assume it's YUV rather than die.
        return new PlanarYUVLuminanceSource(
                data,//待解码数据
                width,//数据的宽度
                height,//数据的高度
                rect.left, rect.top, rect.width(), rect.height(),//解码范围
                false);
    }

//    public void setZoom(double targetZoomRatio) {
//        Camera.Parameters parameters = camera.getCamera().getParameters();
//        CameraConfigurationUtils.setZoom(
//                parameters,
//                targetZoomRatio);
//
//        camera.getCamera().setParameters(parameters);
//
//    }

    /**
     * 处理手势放大/缩小功能
     *
     * @param isZoomIn
     */
    public void handleZoom(boolean isZoomIn) {
        if (camera == null || camera.getCamera() == null) {
            Log.i(TAG, "camera is null");
            return;
        }
        Camera.Parameters params = camera.getCamera().getParameters();
        if (params.isZoomSupported()) {
            int maxZoom = params.getMaxZoom();
            int zoom = params.getZoom();
            if (isZoomIn && zoom < maxZoom) {
                zoom++;
            } else if (zoom > 0) {
                zoom--;
            }
            params.setZoom(zoom);
            camera.getCamera().setParameters(params);
        } else {
            Log.i(TAG, "zoom not supported");
        }
    }


    public void setScanRangeRect(View scanRange) {
        if (scanRange == null) {
            return;
        }
        mScanRange = scanRange;

//        int[] location = new int[2];
//        mScanRange.getLocationOnScreen(location);
//        int x = location[0];
//        int y = location[1];
//        Log.e("test", "Screenx--->" + x + "  " + "Screeny--->" + y);
//        mScanRange.getLocationInWindow(location);
//        x = location[0];
//        y = location[1];
//        Log.e("test", "Window--->" + x + "  " + "Window--->" + y);

    }
}
