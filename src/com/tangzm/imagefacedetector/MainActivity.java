package com.tangzm.imagefacedetector;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.os.Bundle;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;

import com.tangzm.imagefacedetector.FaceAlignProc.Algorithm;

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
				try {
				proc = new FaceAlignProc();
				proc.init(appCntx, model);
				float[] eyes = makeInitialGuess(currPic);
				//For temp now
				if (true) {
					proc.searchInImage(appCntx, currPic, (int)eyes[0],  (int)eyes[1], (int)eyes[2], (int)eyes[3]);
				}
				else {
				String temp = data.getData().toString();
				if (temp.contains("127")){
					proc.searchInImage(appCntx, currPic, 418, 952, 611, 945); //TZM old
				}				
				else if (temp.contains("128")){
					proc.searchInImage(appCntx, currPic, 388, 946, 636, 956); //TZM old 50
				}
				else if (temp.contains("828")){
					proc.searchInImage(appCntx, currPic, 365, 840, 689, 844); //Frederic
				}
				else if (temp.contains("833")){
					proc.searchInImage(appCntx, currPic, 444, 1076, 672, 1060); //TZM stretch mouth
				}
				else if (temp.contains("834")){
					proc.searchInImage(appCntx, currPic, 438, 968, 744, 948);//TZM small eyes
				}
				else if (temp.contains("838")){
					proc.searchInImage(appCntx, currPic, 1016, 1152, 1288, 1136);//km serious
				}
				else if (temp.contains("836")){
					proc.searchInImage(appCntx, currPic, 902, 908, 1191, 946);//km happy
				}
				else {
					proc.searchInImage(appCntx, currPic, 418, 952, 611, 945); //TZM old
				}
				//proc.initialPicAlign(appCntx, 418, 952, 611, 945); //TZM old
				//proc.initialPicAlign(appCntx, 444, 1076, 672, 1060); //TZM stretch mouth
				//proc.initialPicAlign(appCntx, 438, 968, 744, 948);//TZM small eyes
				//proc.initialPicAlign(appCntx, 365, 840, 689, 844); //Frederic
				//proc.initialPicAlign(appCntx, 1016, 1152, 1288, 1136);//km serious
				//proc.initialPicAlign(appCntx, 902, 908, 1191, 946);//km happy
				}
				for (int t=0; t<3; t++){
					proc.optimize(Algorithm.ASM);
				}
				imgFrame.addPlot(proc);
				}
				catch(Exception e){
					e.printStackTrace();
				}
			}
		}
	}

	private void startGalleryForPic() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("image/*");
		startActivityForResult(intent, GALLERY_INTENT_ID);
	}
	
	private float[] makeInitialGuess(Bitmap bmp){
		float[] ret = new float[4];
		Face[] faces = new Face[1];
		int num = 0;
		int width = bmp.getWidth();
		int height = bmp.getHeight();
		Bitmap processBmp = bmp.copy(Bitmap.Config.RGB_565, false);
		FaceDetector detector = new FaceDetector(width, height, 1);
		num = detector.findFaces(processBmp, faces);
		
		Face face = faces[0];
		
		PointF mid = new PointF();
		face.getMidPoint(mid);
		
		ret[0] = mid.x - face.eyesDistance()/2;
		ret[1] = mid.y;
		ret[2] = mid.x + face.eyesDistance()/2;
		ret[3] = mid.y;
		Log.i(TAG, "face"+faces[0]+ " "+num);
		return ret;

	}
}
