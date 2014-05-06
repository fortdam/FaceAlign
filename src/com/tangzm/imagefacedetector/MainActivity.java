package com.tangzm.imagefacedetector;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Bitmap.Config;
import android.graphics.PointF;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.os.Bundle;
import android.os.Debug;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;

import com.tangzm.imagefacedetector.FaceAlignProc.Algorithm;
import com.tangzm.imagefacedetector.FaceAlignProc.Organ;
import com.tangzm.imagefacedetector.FaceAlignProc.Parameter;

public class MainActivity extends Activity implements OnClickListener{

	private static final String TAG="ImageFaceDetect";
	private static final int GALLERY_INTENT_ID = 0x1515;

	private FaceModel model = null;
	private FaceAlignProc proc = null;
	private FaceView imgFrame = null;
	private Bitmap currPic = null;
	private Context appCntx = null;
	
	
	private class DrawProc implements FaceView.Plotable {
		
		public DrawProc(FaceAlignProc proc) {
			mProc = proc;
		}
		
		public void plot(Canvas canvas){
			mProc.drawTestInfo(canvas);
		}
		
		private FaceAlignProc mProc;
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
		model = new FaceModel(appCntx, R.raw.model);
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
			    
			    if (currPic.getWidth() > 1080 ) { //only for DiabloX yet
			    	currPic = Bitmap.createScaledBitmap(currPic, 1080, 1920, false);
			    }
			}
			catch(Exception e){
				e.printStackTrace();
			}
			imgFrame.setImageBitmap(currPic);			

			try {
				FuncTracer.startProc("Fit");
				if (null == proc) {
					proc = new FaceAlignProc();
					proc.init(appCntx, model);
				}
				
				proc.searchInImage(appCntx, currPic, Algorithm.ASM);
				
				FuncTracer.endProc("Fit");
				imgFrame.addPlot(new DrawProc(proc));
			}
			catch(Exception e){
				FuncTracer.procException(e);
				e.printStackTrace();
			}
			
		}
	}

	private void startGalleryForPic() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("image/*");
		startActivityForResult(intent, GALLERY_INTENT_ID);
	}
}
