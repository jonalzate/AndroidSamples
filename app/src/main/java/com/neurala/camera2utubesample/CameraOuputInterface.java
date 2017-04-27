package com.neurala.camera2utubesample;

import android.graphics.Bitmap;

/**
 * Created by jalzate on 4/27/17.
 * Interface used to send bitmaps from CameraPreview to the MainActivity
 */

public interface CameraOuputInterface
{
    /**
     * a YUV frame is received from camera
     *
     * @param data the raw data of the image
     * @param width the width of the image
     * @param height the height of the image
     */
    void YUVReceived(byte[] data, int width, int height);

    /**
     * A bitmap has been decoded and is ready for processing by the tracker
     *
     * @param bitmap the new bitmap that is ready for processing     *
     */
    void bitmapReceived(Bitmap bitmap);

}
