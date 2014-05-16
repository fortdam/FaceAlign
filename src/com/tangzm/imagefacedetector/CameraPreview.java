package com.tangzm.imagefacedetector;

import java.io.IOException;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
	private String TAG="CameraPreview";
	
    private SurfaceHolder mHolder;
    private Camera mCamera;
    
    private byte[] mPreviewDataBuffer = null;
    private byte[] mPreviewDataBuffer2 = null;
    
    private Camera.PreviewCallback mPreviewCallback = null;
    private Camera.FaceDetectionListener mFaceDetectListener = null;
    
    public CameraPreview(Context context, Camera camera, Camera.PreviewCallback callback, Camera.FaceDetectionListener faceListener) {
        super(context);
        mCamera = camera;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        mPreviewCallback = callback;
        mFaceDetectListener = faceListener;
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
        	Camera.Parameters params = mCamera.getParameters();
            
        	params.setPreviewFormat(ImageFormat.RGB_565);
        	int width = params.getPreviewSize().width;
        	int height = params.getPreviewSize().height;
        	
        	
        	mCamera.setParameters(params);
        	      
        	mPreviewDataBuffer = new byte[width*height*ImageFormat.getBitsPerPixel(ImageFormat.RGB_565)/8];
    		mPreviewDataBuffer2 = new byte[width*height*ImageFormat.getBitsPerPixel(ImageFormat.RGB_565)/8];
        	mCamera.addCallbackBuffer(mPreviewDataBuffer);
        	mCamera.addCallbackBuffer(mPreviewDataBuffer2);
            
            mCamera.setPreviewDisplay(holder);
            
            mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);            
            //mCamera.setFaceDetectionListener(mFaceDetectListener);
            
            mCamera.startPreview();         

        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void flushBuffers(){
    	mCamera.addCallbackBuffer(mPreviewDataBuffer);
    	mCamera.addCallbackBuffer(mPreviewDataBuffer2);
    }
    
    public void surfaceDestroyed(SurfaceHolder holder) {
    	mCamera.stopPreview();
    	mCamera.release();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    	
        if (mHolder.getSurface() == null){
          // preview surface does not exist
          return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
          // ignore: tried to stop a non-existent preview
        }

        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
            if (mPreviewDataBuffer == null){
            	
            }
        	mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);

        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }
}
