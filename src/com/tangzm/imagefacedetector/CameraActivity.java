package com.tangzm.imagefacedetector;

import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class CameraActivity extends Activity 
implements Camera.PreviewCallback, Camera.FaceDetectionListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera);
		
		mCameraID = 1;
		
        // Create an instance of Camera
        mCamera = getCameraInstance();
        mCamera.setDisplayOrientation(90);

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera, this, this);
                
        FrameLayout preview = (FrameLayout) findViewById(R.id.CameraPreview);
        
        preview.addView(mPreview);
                
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_camera, menu);
		return true;
	}
	
	@Override
	public void onResume() {
		super.onResume();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		mCamera.release();
	}
	
	public Camera getCameraInstance(){
	    Camera c = null;
	    try {
	        c = Camera.open(mCameraID); // attempt to get a Camera instance
	    }
	    catch (Exception e){
	        // Camera is not available (in use or does not exist)
	    }
	    return c; // returns null if camera is unavailable
	}
	
	public void onPreviewFrame(byte[] data, Camera camera) {
		Log.i("tangzmm", "frame comes");
		
	}
	
	public void onFaceDetection(Face[] faces, Camera camera) {
		Log.i("tnagzmm", "face detect!");
	}
	
	public void click(View view){
		Log.i("tangzmm", "click "+mCamera.getParameters().getMaxNumDetectedFaces());

		try {
		mCamera.setFaceDetectionListener(new Camera.FaceDetectionListener() {
			
			@Override
			public void onFaceDetection(Face[] faces, Camera camera) {
				// TODO Auto-generated method stub
				Log.i("tangzmm", "face detected!");
			}
		});
		mCamera.startFaceDetection();
		}
		catch (Exception e) {
			Log.i("tangzmm", "error on start face detection");
		}
	}
	
	private Camera mCamera;
	private CameraPreview mPreview;
	private int mCameraID;
}
