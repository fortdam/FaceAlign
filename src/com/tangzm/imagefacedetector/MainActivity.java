package com.tangzm.imagefacedetector;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.provider.MediaStore.Images.Media;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;

import com.tangzm.facedetect.FaceAlignProc;

public class MainActivity extends Activity implements OnClickListener{

	private static final String TAG="ImageFaceDetect";
	private static final int GALLERY_INTENT_ID = 0x1515;

	private FaceAlignProc proc = null;
	private PlotView imgFrame = null;
	private Bitmap currPic = null;
	private Context appCntx = null;
	
	
	private class DrawProc implements PlotView.Plotable {
		
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
		
		imgFrame = (PlotView)findViewById(R.id.FaceImage);
		imgFrame.setClickable(true);
		imgFrame.setOnClickListener(this);
	}
	
	@Override
	protected void onResume(){
		super.onResume();
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
			//imgFrame.setImageBitmap(currPic);			

			try {
				if (null == proc) {
					proc = new FaceAlignProc();
					proc.init(appCntx, R.raw.model);
				}
				
				proc.searchInImage(appCntx, currPic, FaceAlignProc.Algorithm.ASM, new FaceAlignProc.Callback() {
					public void finish(boolean status) {
						if (true == status){
							imgFrame.addPlot(new DrawProc(proc));
						}
					}
				});
				
				//imgFrame.addPlot(new DrawProc(proc));
			}
			catch(Exception e){
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
