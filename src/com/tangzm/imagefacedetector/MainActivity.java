package com.tangzm.imagefacedetector;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.os.Bundle;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

public class MainActivity extends Activity implements OnClickListener{

	static final String TAG="ImageFaceDetect";
	static final int GALLERY_INTENT_ID = 0x1515;

	private FaceModel model = null;
	private FaceAlignProc proc = null;
	private FaceView imgFrame = null;
	private Bitmap currPic = null;
	private Context appCntx = null;
	
	 private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
	        @Override
	        public void onManagerConnected(int status) {
	            switch (status) {
	                case LoaderCallbackInterface.SUCCESS:
	                {
	                    Log.i(TAG, "OpenCV loaded successfully");
	            		model = new FaceModel(appCntx, R.raw.model); //now the model is loaded synchronously	            		
	            		//Log.e(TAG, proc.getCurrentShape().dump());
	                } 
	                break;
	                default:
	                {
	                    super.onManagerConnected(status);
	                } break;
	            }
	        }
	    };
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		appCntx = this;
		
		imgFrame = (FaceView)findViewById(R.id.FaceImage);
		imgFrame.setClickable(true);
		imgFrame.setOnClickListener(this);
	}
	
	@Override
	protected void onResume(){
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_8, this, mLoaderCallback);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	@Override
	public void onClick(View arg0) {
		startGalleryForPic();
	}
	
	@Override
	protected void onActivityResult (int requestCode, int resultCode, Intent data){
		if (GALLERY_INTENT_ID == requestCode){
			try{
			    currPic = Media.getBitmap(getContentResolver(), data.getData());
			}
			catch(Exception e){
				e.printStackTrace();
			}
			imgFrame.setImageBitmap(currPic);
			
			if (null == proc){
				proc = new FaceAlignProc();
				proc.init(model);
				proc.setPicture(currPic);
				proc.initialPicAlign(appCntx, 418, 952, 611, 945);
				proc.search(true);
				imgFrame.addPlot(proc);
			}
		}
	}

	private void startGalleryForPic() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("image/*");
		startActivityForResult(intent, GALLERY_INTENT_ID);
	}
	
	private void makeInitialGuess(Bitmap bmp){
		Face[] faces = new Face[5];
		int num = 0;
		int width = bmp.getWidth();
		int height = bmp.getHeight();
		Bitmap processBmp = bmp.copy(Bitmap.Config.RGB_565, false);
		FaceDetector detector = new FaceDetector(width, height, 5);
		num = detector.findFaces(processBmp, faces);
		Face face = faces[0];
		float x = face.pose(0);
		float y = face.pose(1);
		float z = face.pose(2);
		Log.i(TAG, "face"+faces[0]+ " "+num);
	}
}
