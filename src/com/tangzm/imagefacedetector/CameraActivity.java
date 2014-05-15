package com.tangzm.imagefacedetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.tangzm.facedetect.FaceAlignProc;
import com.tangzm.facedetect.FaceAlignProc.Algorithm;
import com.tangzm.imagefacedetector.CameraFaceTrackFSM.Event;

public class CameraActivity extends Activity 
implements Camera.PreviewCallback, Camera.FaceDetectionListener,  CameraFaceTrackFSM.CameraFaceView{

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
                
        mPlotView = (PlotView)findViewById(R.id.CameraPlotView);
        mFSM = new CameraFaceTrackFSM(this);
        
		mProc = new FaceAlignProc();
		mProc.init(this.getApplicationContext(), R.raw.model);

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
	
	private void saveBitmapFile(Bitmap image) {
		String path = Environment.getExternalStorageDirectory().toString();
		OutputStream fOut = null;
		File file = new File(path, "FaceCamTest"+".jpg");
		try {
		fOut = new FileOutputStream(file);

		image.compress(Bitmap.CompressFormat.JPEG, 85, fOut);
		fOut.flush();
		fOut.close();
		}
		catch (Exception e){
			e.printStackTrace();
		}

	}
	
	private Bitmap bufferToBitmap(final byte[] data) {
		Camera.Parameters param = mCamera.getParameters();
				
		Bitmap image = Bitmap.createBitmap(param.getPreviewSize().width, param.getPreviewSize().height, Bitmap.Config.RGB_565);
		Buffer buffer = ByteBuffer.wrap(data);
		image.copyPixelsFromBuffer(buffer);
		
		Matrix matrix = new Matrix();
		matrix.postScale(-1, 1);
		matrix.preRotate(-90);
		
		Bitmap rotateImage = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);	
		
		//saveBitmapFile(rotateImage);
		
		return rotateImage;
	}
	
	public void onPreviewFrame(final byte[] data, Camera camera) {
		Log.i(TAG, "frame comes");
		
		if (mFSM.STATE_HARD_CHECK == mFSM.getCurrentState() ||
				mFSM.STATE_SOFT_CHECK == mFSM.getCurrentState()) {
			try {
				mProc.searchEyesInImage(bufferToBitmap(data), new FaceAlignProc.CallbackWithEyePts() {
					@Override
					public void finish(float[] eyePts) {
						if (null == eyePts){
							mFSM.sendEvent(Event.FACE_NOT_DETECTED);
						}
						else {
							leftEyeX = eyePts[0];
							leftEyeY = eyePts[1];
							rightEyeX = eyePts[2];
							rightEyeY = eyePts[3];
							mFSM.sendEvent(Event.FACE_DETECTED);
						}
						
					}
				});
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
		else if (mFSM.STATE_HARD_FIT == mFSM.getCurrentState()) {
			//asm optimize
			
			final float plotScale = (float)(mPlotView.getWidth()) / (float)(camera.getParameters().getPreviewSize().height);  //90 degree rotated
			try {
				mProc.searchInImage(this.getApplicationContext(), bufferToBitmap(data), Algorithm.ASM_QUICK, new FaceAlignProc.Callback() {
					@Override
					public void finish(boolean status) {
						
						Log.i(TAG, "the result of hard fit is "+status);
						if (status){
							mPlotView.addPlot(new PlotView.Plotable() {
								@Override
								public void plot(Canvas canvas) {
									mProc.drawTestInfo(canvas, plotScale, 0, 0);	
								}
							});
							mPlotView.invalidate();
						}
						
						mFSM.sendEvent(Event.FIT_COMPLETE);
					}
				}, leftEyeX, leftEyeY, rightEyeX, rightEyeY);
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
		else if (mFSM.STATE_SOFT_FIT == mFSM.getCurrentState()) {
			//kde optimize
			//mFSM.notifyFit();
			//mCamera.addCallbackBuffer(data);
			
			
			
			final float plotScale = (float)(mPlotView.getWidth()) / (float)(camera.getParameters().getPreviewSize().height); //90 degree rotated
			try {
				mProc.optimizeInImage(this.getApplicationContext(), bufferToBitmap(data), Algorithm.ASM_QUICK, new FaceAlignProc.Callback() {
					@Override
					public void finish(boolean status) {
						Log.i(TAG, "The result of soft fit is "+status);
						if (status){
							mPlotView.addPlot(new PlotView.Plotable() {
								@Override
								public void plot(Canvas canvas) {
									
									Log.i(TAG, "Plotting is called!!!");
									mProc.drawTestInfo(canvas, plotScale, 0, 0);	
								}
							});
							mPlotView.invalidate();
						}
						
						mFSM.sendEvent(Event.FIT_COMPLETE);
					}
				});
			}
			catch (Exception e){
				e.printStackTrace();
			}			
		}
		
	}
	
	public void onFaceDetection(Face[] faces, Camera camera) {
		Log.i(TAG, "face detect!");
	}
	
	public void click(View view){
		mFSM.sendEvent(Event.CHECK_REQUIRED);
	}

	@Override
	public void startProc() {
		mPreview.flushBuffers();
	}

	@Override
	public void stopProc() {
		mPlotView.addPlot(null);
		mPlotView.invalidate();
	} 
	

	private Camera mCamera;
	private CameraPreview mPreview;
	private int mCameraID;
	
	private CameraFaceTrackFSM mFSM;
	
	private FaceAlignProc mProc;
	private PlotView mPlotView;
		
	private float leftEyeX;
	private float leftEyeY;
	private float rightEyeX;
	private float rightEyeY;
	
	private static final String TAG="CameraFaceFit";


}
