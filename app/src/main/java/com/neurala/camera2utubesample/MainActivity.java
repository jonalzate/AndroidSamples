package com.neurala.camera2utubesample;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION_CODE = 0;
    private TextureView mTextureView;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height)
        {
            setUpCamera(width, height);
            connectCamera();

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private static class CompareSizeByArea implements Comparator<Size>{
        @Override
        public int compare(Size lhs, Size rhs){
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() /
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    // camera device
    private CameraDevice mCameraDevide;
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera)
        {
            mCameraDevide = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera)
        {
            // clean up
            camera.close();
            mCameraDevide = null;

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error)
        {
            //clean up
            camera.close();
            mCameraDevide = null;
        }
    };

    // camera ID
    private String mCameraID;
    private Size mPreviewSize;
    private CaptureRequest.Builder mCaptureRequestBuilder;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextureView = (TextureView)findViewById(R.id.textureView);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        // start background thread processing
        startBackgroundThread();

        if (mTextureView.isAvailable())
        {
            setUpCamera(mTextureView.getWidth(), mTextureView.getHeight());
            connectCamera();

        } else {
            // set the listener if the textureview is not available
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION_CODE){
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        // free up the camera
        closeCamera();

        // stop the background thread from processing
        stopBackgroundThread();

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);

        View decorView = getWindow().getDecorView();

        if(hasFocus){
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    // gets camera id
    private void setUpCamera(int width, int height){
        CameraManager mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            for (String cameraId : mCameraManager.getCameraIdList())
            {
                CameraCharacteristics mCameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                if(CameraCharacteristics.LENS_FACING_FRONT == mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING)){
                    continue;
                }

                mCameraID = cameraId;

                StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // get device orientation from rotation
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                // convert sensor rotation to device rotation using our method
                int totalRotation = sensorToDeviceRotation(mCameraCharacteristics, deviceOrientation);
                // flag to check when need to change rotation values
                boolean swapRotation = totalRotation == 90 || totalRotation == 270;
                // set current width and height to rotated width and height
                int rotatedWidth = width;
                int rotatedHeight = height;

                if(swapRotation){
                    // device rotated, adjust width and height accordingly
                    rotatedWidth = height;
                    rotatedHeight = width;
                }

                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                return;

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // connect to camera
    private void connectCamera(){
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // check android sdk version
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            {
                // request per
                if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED){
                    cameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mBackgroundHandler);
                } else {
                    if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                        Toast.makeText(this, "Access to camera is required. Please enable it", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_CODE);
                }

            } else {
                cameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mBackgroundHandler);
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Capture request buidler
    private void startPreview(){
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder = mCameraDevide.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            mCameraDevide.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(getApplicationContext(), "Unable to set up camera preview", Toast.LENGTH_SHORT).show();

                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera(){
        if(mCameraDevide != null)
        {
            mCameraDevide.close();
            mCameraDevide = null;
        }
    }

    private void startBackgroundThread(){
        mBackgroundHandlerThread = new HandlerThread("Camera2Handler");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread(){
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // converts sensor rotation to device rotation
    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation){
        int sensorOrientaion = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        deviceOrientation = ORIENTATIONS.get(deviceOrientation);

        return(sensorOrientaion + deviceOrientation + 360) % 360;
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height){
        List<Size> bigEnough = new ArrayList<Size>();

        for (Size option : choices){
            // check the aspect ration
            if(option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height){

                // add value to list
                bigEnough.add(option);
            }
        }

        if(bigEnough.size() > 0){
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }
    }
}
