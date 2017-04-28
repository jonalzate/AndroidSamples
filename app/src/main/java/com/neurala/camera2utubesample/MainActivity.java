package com.neurala.camera2utubesample;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.neurala.neurecognizer.Recognizer;
import com.neurala.neurecognizer.RecognizerInterface;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements RecognizerInterface,
                                                                CameraOuputInterface {
    // log tag
    private static final String TAG = MainActivity.class.getSimpleName();
    // UI elements declarations
    private TextView mTextObject1;
    private TextView mTextObject2;
    private TextView mTextConfidence;

    // Neurala Recognizer
    public Recognizer mRecognizer;

    // request code for camera permission
    private static final int REQUEST_CAMERA_PERMISSION_CODE = 0;
    // Texture View to display the video stream from camera
    private TextureView mTextureView;
    // Surface Texture listener to get notified about surface states
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // When surface becomes available set up and connect the camera
            setUpCamera(width, height);
            transformPreview(width, height);
            connectCamera();

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // surface size changed, do whatever to take care of any changes

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    // Handler thread to handle work (camera video preview) in the background
    private HandlerThread mBackgroundHandlerThread;
    // Handler to to get updates from background thread
    private Handler mBackgroundHandler;

    private ImageButton mRecordButton;
    private Button mRecognizerButton;
    private Button mMenuButton;
    private boolean mIsRecording = false;

    // Screen orientations array
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    // to save class names from classifier
    private String[] mClassNames;
    private ArrayList<String> mClassNamesArray;
    // arrays to save the text view for object and confidence
    private ArrayList<TextView> mTextObjectArray;
    private ArrayList<TextView> mTextConfidenceArray;
    private ArrayList<String> mConfidenceNamesArray;
    private ArrayList<Integer> mConfidenceWeightArray;

    // Camera Output interface methods
    @Override
    public void YUVReceived(byte[] data, int width, int height) {

    }

    @Override
    public void bitmapReceived(Bitmap bitmap) {

    }

    // Recognizer interface methods
    @Override
    public void receivedTopRecognizerPrediction(String s, double v) {

    }

    @Override
    public void receivedRecognizerPredictions(HashMap<String, Double> hashMap) {

    }

    @Override
    public void recognizerStateUpdate(ERecognizerState eRecognizerState) {

    }

    private static class CompareSizeByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() /
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    // camera manager to get camera ID and characteristics
    private CameraManager mCameraManager;
    // camera device
    private CameraDevice mCameraDevice;
    // Image reader to get single frames
    private ImageReader mImageReader;
    // camera device state call back - notifies any updates on the state of the camera device
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            // camera was opened. Saved it as camera device and start the preview
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            // camera disconnected, clean up
            camera.close();
            mCameraDevice = null;

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            //clean up
            camera.close();
            mCameraDevice = null;
        }
    };

    // camera ID
    private String mCameraID;
    // preview size
    private Size mPreviewSize;
    // builds a capture request for camera device
    private CaptureRequest.Builder mCaptureRequestBuilder;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initialize UI elements
        mTextureView = (TextureView) findViewById(R.id.textureView);
        mRecordButton = (ImageButton) findViewById(R.id.videoOnlineButton);
        mRecognizerButton = (Button) findViewById(R.id.cameraButton);
        mMenuButton = (Button) findViewById(R.id.menuButton);
        mTextObject1 = (TextView) findViewById(R.id.textObject1);
        mTextObject2 = (TextView) findViewById(R.id.textObject2);
        mTextConfidence = (TextView) findViewById(R.id.textConfidence);

        // initialize storing arrays
        mClassNamesArray = new ArrayList<>();
        mTextConfidenceArray = new ArrayList<>(Arrays.asList(mTextConfidence));
        mTextObjectArray = new ArrayList<>(Arrays.asList(mTextObject1, mTextObject2));
        mConfidenceNamesArray = new ArrayList<>();
        mConfidenceWeightArray = new ArrayList<>();

        // initialize camera manager
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        // initialize recognizer once video is visible on screen
        //initializeRecognizer();

        // set click listener for button
        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsRecording)
                {
                    mIsRecording = false;
                    mRecordButton.setImageResource(R.mipmap.btn_video_online);

                } else {
                    mIsRecording = true;
                    mRecordButton.setImageResource(R.mipmap.btn_video_recording);

                    // stop recognizer from learning
//                    mRecognizer.stopLearning();
                }
            }
        });

        // set recognizer button listener
        mRecognizerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!(mRecognizer.recognizerState == ERecognizerState.eLearningObject)){
                    // stop learning
//                    mRecognizer.stopLearning();

                    mRecognizerButton.setText(R.string.recognizeLabel);
                } else {
                    Log.d(TAG, "Stop Learning");

                    mClassNames = mRecognizer.classNames();
//                    mRecognizer.stopRecognizing();

                    mRecognizerButton.setText(R.string.stopRecognizeLabel);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // start background thread processing
        startBackgroundThread();

        // check texture view is available and setup camera if available
        // or set surface texture listener if not available
        if (mTextureView.isAvailable()) {
            setUpCamera(mTextureView.getWidth(), mTextureView.getHeight());
            transformPreview(mTextureView.getWidth(), mTextureView.getHeight());
            connectCamera();

        } else {
            // set the listener if the textureview
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

    }

    // Image reader implementation. Used to pass frames to the recognizer
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader)
                {
                    Image mImage = null;
                    int imageFormat = reader.getImageFormat();

                    try {
                        mImage = reader.acquireLatestImage();


                        if(imageFormat != ImageFormat.YUV_420_888)
                        {
                            // not YUV format, notify user
                            Log.d(TAG, "Wrong Image format, not YUV_420");
                        } else {
                            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
                            byte [] bytes = new byte[byteBuffer.remaining()];
                            // mRecognizer.pushFrame(bytes, reader.getWidth(), reader.getHeight());
                        }

                    } finally {
                        if(mImage != null) {
                            mImage.close();

                        }

                    }

                }
            };


    // Receives the result of the camera permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // free up the camera
        closeCamera();

        // stop the background thread from processing
        stopBackgroundThread();

    }

    // set the visibility of some objects in the window (status bar) and set
    // full screen for this activity
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        View decorView = getWindow().getDecorView();

        if (hasFocus) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    // sets up the camera and gets camera id, using the width and the height from
    // the texture view
    private void setUpCamera(int width, int height) {
        // surround with try and catch to get any camera exceptions that may happen in the process
        try {
            // get camera id from the cameras available on device
            for (String cameraId : mCameraManager.getCameraIdList()) {
                Log.d(TAG, "camera ID: " + cameraId);
                // get camera characteristics for each id found
                CameraCharacteristics mCameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                // check for the front or back facing camera.
                // using the back facing camera in this sample
                if (mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                // set camera Id for use in the activity
                mCameraID = cameraId;
                Log.d(TAG, "camera ID after lens facing check: " + mCameraID);

                // get the stream configuration map from the camera characteristics supported by the device
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

                if (swapRotation) {
                    // device rotated, adjust width and height accordingly
                    rotatedWidth = height;
                    rotatedHeight = width;
                }



                // set up ImageReader to pass frames to the recognizer
                Size largestImageSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new Comparator<Size>() {
                            @Override
                            public int compare(Size lhs, Size rhs) {
                                return Long.signum(lhs.getWidth() * lhs.getHeight() -
                                        rhs.getWidth() * rhs.getHeight());
                            }
                        });

                mImageReader = ImageReader.newInstance(largestImageSize.getWidth(),
                        largestImageSize.getHeight(),
                        ImageFormat.YUV_420_888, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

                // set the preview size
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedWidth, rotatedHeight);
            }
            return;


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // connect to camera
    private void connectCamera()
    {

        // use try-catch to get notified of any camera exception
        try {
            // check android sdk version to see what camera permission to show to user
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // check that camera permission from manifest was granted
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                    // open camera if permission was granted
                    mCameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mBackgroundHandler);
                } else {
                    // alert user that permission is needed to run app
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        Toast.makeText(this, "Access to camera is required. Please enable it", Toast.LENGTH_SHORT).show();
                    }
                    // request camera permission
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_CODE);
                }

            } else {
                // sdk version is lower, permission should have been granted, open camera
                mCameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mBackgroundHandler);
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // start the video preview using capture request builder
    private void startPreview() {
        // get the surface texture from the camera Texture View  and set buffer size
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        // set preview surface using the surface texture
        Surface previewSurface = new Surface(surfaceTexture);

        try {

            // create the capture request from camera device
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // add the preview surface as target display
            mCaptureRequestBuilder.addTarget(previewSurface);
            // add the preview surface as a target to the ImageReader
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            // create a capture session from camera device and use a capture session call back
            // to notify state of request
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                // configure capture session
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        // set a repeating request for this session
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

    // used to close camera when exiting the activity
    private void closeCamera() {
        // check camera device is null
        if (mCameraDevice != null) {
            // close camera device and set it to null
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    // starts background handler thread to process video input stream
    private void startBackgroundThread() {
        // initialize handler thread with name and start the thread
        mBackgroundHandlerThread = new HandlerThread("Camera2Handler");
        mBackgroundHandlerThread.start();
        // initialize the handler and hook it to the background thread
        // to be able to get/pass updates
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    // stops the background thread  by quitting safely and setting thread to null
    private void stopBackgroundThread() {
        // quit thread
        mBackgroundHandlerThread.quitSafely();
        try {
            // waits for thread to die and set background thread and handler to null
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // converts sensor rotation to device rotation to account for device's rotation
    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
        // get the sensor orientation from camera characteristics
        int sensorOrientaion = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        return (sensorOrientaion + deviceOrientation + 360) % 360;
    }

    // chooses the optimal size from the supported sizes by device included
    // in the stream configuration map
    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        // sizes array list
        List<Size> bigEnough = new ArrayList<>();

        // iterate through input sizes supported by device
        for (Size option : choices) {
            // check the aspect ratio of the screen
            if (option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height) {

                // add value to list
                bigEnough.add(option);
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }
    }

    // method to adjust preview after device is rotated
    private void transformPreview(int width, int height) {
        if (mPreviewSize == null || mTextureView == null) {
            return;
        }

        Matrix matrix = new Matrix();
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        RectF textureRectF = new RectF(0, 0, width, height);
        RectF previewRectF = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = textureRectF.centerX();
        float centerY = textureRectF.centerY();

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            previewRectF.offset(centerX - previewRectF.centerX(), centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) width / mPreviewSize.getWidth(),
                    (float) height / mPreviewSize.getHeight());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }

        mTextureView.setTransform(matrix);
    }

    // Neurala Recognizer initialization. the recognizer accepts video frames in two formats
    // YUV420sp (aka NV21, Android default format) and Bitmaps. the use of YUV is recommended
    // for faster performance
    public void initializeRecognizer()
    {
        // initialize recognizer
        mRecognizer = new Recognizer(getApplicationContext());
        if (mRecognizer.loadClassifications(getApplicationContext())) {
            Log.e(TAG, "Classifications loaded successfully");
        } else {
            Log.e(TAG, "No classifications found, skipped");
        }

        // set listener to recognizer
        mRecognizer.addListener(this);

        // get saved class neames from classifier into array of strings
        mClassNames = mRecognizer.classNames();
        for (int i = 0; i < mClassNames.length; i++) {
            Log.e(TAG, "class " + i + ": " + mClassNames[i]);

            // add all saved class names to an arraylist
            mClassNamesArray.addAll(Arrays.asList(mClassNames));

        }


    }
}
