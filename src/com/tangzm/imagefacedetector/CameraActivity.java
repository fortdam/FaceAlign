package com.tangzm.imagefacedetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Vector;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.tangzm.facedetect.FaceAlignProc;
import com.tangzm.facedetect.FuncTracer;
import com.tangzm.facedetect.FaceAlignProc.Algorithm;
import com.tangzm.facedetect.RawImage;
import com.tangzm.imagefacedetector.CameraFaceTrackFSM.Event;

public class CameraActivity extends Activity 
implements Camera.FaceDetectionListener,  CameraFaceTrackFSM.CameraFaceView{

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera);
		
		mCameraID = 1;
		
        // Create an instance of Camera
        mCamera = getCameraInstance();
        mCamera.setDisplayOrientation(90);

        // Create our Preview view and set it as the content of our activity.
        mBufferHandler = new BufferHandler();
        mPreview = new CameraPreview(this, mCamera, mBufferHandler, this);
                
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
		mCamera.startPreview();
	}
	
	@Override
	public void onPause() {
		mCamera.stopPreview();
		//mCamera.release();
		super.onPause();
	}
	
	public void onStop() {
		mCamera.release();
		super.onStop();
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
		FuncTracer.startFunc();
		Camera.Parameters param = mCamera.getParameters();
				
		//90 degree rotated
		FuncTracer.startProc("CopyBuffer");
		Bitmap image = Bitmap.createBitmap(mPreviewHeight, mPreviewWidth, Bitmap.Config.RGB_565);
		Buffer buffer = ByteBuffer.wrap(data);
		image.copyPixelsFromBuffer(buffer);
		FuncTracer.endProc("CopyBuffer");
		
		FuncTracer.startProc("Rotate");
		Matrix matrix = new Matrix();
		matrix.postScale(-1, 1);
		matrix.preRotate(-90);
		
		Bitmap rotateImage = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);	
		FuncTracer.endProc("Rotate");
		
		//saveBitmapFile(rotateImage);
		FuncTracer.endFunc();
		return rotateImage;
	}
	
	private RawImage bufferToRawImage(final byte[] data){
		RawImage image = new RawImage();
		
		image.mData = data;
		//90 rotate
		image.mWidth = mPreviewHeight;
		image.mHeight = mPreviewWidth;
		image.mRotate = 90;
		image.mMirror = true;
		
		return image;
	}
	
	class BufferHandler implements Camera.PreviewCallback{
						
		private Vector<byte[]> mBuffers = new Vector<byte[]>();
		
		public void deliverFrameIfNeeded() {
			if (false == mProc.isRunning() && mBuffers.size()>0) {
				
				final byte[] currData = mBuffers.get(0);
				
				processFrame(currData, new Runnable() {

					public void run() {
						for (int i=0; i<mBuffers.size(); i++) {
							if (mBuffers.get(i) == currData){
								mCamera.addCallbackBuffer(mBuffers.remove(i));
								break;
							}
						}

						deliverFrameIfNeeded();
					}
					
				});
			}
		}
		

		@Override
		public void onPreviewFrame(final byte[] data, Camera camera) {
			mBuffers.add(data);
			deliverFrameIfNeeded();		
		}
		
		public void obseleteBuffer() {
			int startIndex = 0;
			
			if (mProc.isRunning()) {
				//buffer 0 is being used now, don't free it now.
				startIndex += 1;
			}
			while(mBuffers.size() > startIndex) {
				mCamera.addCallbackBuffer(mBuffers.remove(startIndex));
			}
		}
	};
	
	public void processFrame(final byte[] data, final Runnable bufferConsumed) {
		Log.i(TAG, "frame comes, current state is "+mFSM.getCurrentState());
	
		if (mFSM.STATE_HARD_CHECK == mFSM.getCurrentState() ||
				mFSM.STATE_SOFT_CHECK == mFSM.getCurrentState()) {
			try {
				if (mPreviewWidth < 0.1){
					Camera.Size size = mCamera.getParameters().getPreviewSize();
					
					//90 degree rotated
					mPreviewHeight = size.width;
					mPreviewWidth = size.height; 
				}
				
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
						bufferConsumed.run();
					}
				});
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
		else if (mFSM.STATE_HARD_FIT == mFSM.getCurrentState()) {
			//asm optimize
			
			final float plotScale = (float)(mPlotView.getWidth()) / mPreviewWidth;  //90 degree rotated
			try {
				mProc.searchInImage(bufferToBitmap(data), Algorithm.ASM_QUICK, new FaceAlignProc.Callback() {
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
						Log.i(TAG, "one frame is processed, take next...");
						bufferConsumed.run();
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
			
			final float plotScale = (float)(mPlotView.getWidth()) / mPreviewWidth; 
			
			try {
				mProc.optimizeInImage(bufferToRawImage(data), Algorithm.ASM_QUICK, new FaceAlignProc.Callback() {
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
						bufferConsumed.run();
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
		mBufferHandler.obseleteBuffer();
	}

	@Override
	public void stopProc() {
		mPlotView.addPlot(null);
		mPlotView.invalidate();
	} 
	

	private Camera mCamera;
	private CameraPreview mPreview;
	private int mCameraID;
	
	private int mPreviewWidth = 0;
	private int mPreviewHeight = 0;
	
	private BufferHandler mBufferHandler;
	
	private CameraFaceTrackFSM mFSM;
	
	private FaceAlignProc mProc;
	private PlotView mPlotView;
		
	private float leftEyeX;
	private float leftEyeY;
	private float rightEyeX;
	private float rightEyeY;
	
	
	private static final String TAG="CameraActivity";


}
