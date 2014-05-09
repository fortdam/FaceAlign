package com.tangzm.imagefacedetector;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import com.tangzm.facedetect.FaceAlignProc;

public class ImageActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_image);
		
		mAppCntx = this;
		mPlotView = (PlotView)findViewById(R.id.ImagePlotView);
		
		mProc = new FaceAlignProc();
		mProc.init(mAppCntx, R.raw.model);
		
		Intent incomeIntent = getIntent();
		Uri bitmap = incomeIntent.getExtras().getParcelable("uri");
				
		try {
			mCurrImage = Media.getBitmap(getContentResolver(), bitmap);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onResume() {
		super.onRestart();
		final ImageView image = (ImageView)findViewById(R.id.BaseImageView);
		image.setImageBitmap(mCurrImage);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_image, menu);
		return true;
	}
	
	private void search(FaceAlignProc.Algorithm type) {
		
		int imgWidth = mCurrImage.getWidth();
		int imgHeight = mCurrImage.getHeight();
		
		//image view is same size as plot view
		int viewWidth = mPlotView.getWidth();
		int viewHeight = mPlotView.getHeight();
		
		float scaleX = (float)viewWidth/(float)imgWidth;
		float scaleY = (float)viewHeight/(float)imgHeight;
		
		final float scale = scaleX<scaleY?scaleX:scaleY;
		final float translateY = (viewHeight-imgHeight*scale) / 2.0f;
		final float translateX = (viewWidth-imgWidth*scale) / 2.0f;
		
		Log.i("TANGZM", viewWidth+" "+viewHeight+" "+imgWidth+" "+imgHeight);
		Log.i("TANGZM", "scale, scaleX, scaleY "+scale+" "+scaleX+" "+scaleY+" "+translateX+" "+translateY);

		
		try{
			mProc.searchInImage(mAppCntx, mCurrImage, type, new FaceAlignProc.Callback() {
				
				@Override
				public void finish(boolean status) {
					if(status){
						mPlotView.addPlot(new PlotView.Plotable() {
							@Override
							public void plot(Canvas canvas) {
								mProc.drawTestInfo(canvas, scale, translateX, translateY);	
							}
						});
						mPlotView.invalidate();
					}
					
				}
			});
		}
		catch (Exception e){
			e.printStackTrace();
		}
		

	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	        case R.id.menu_asm:
	            search(FaceAlignProc.Algorithm.ASM);
	            return true;
	        case R.id.menu_cqf:
	            search(FaceAlignProc.Algorithm.CQF);
	            return true;
	        case R.id.menu_kde:
	            search(FaceAlignProc.Algorithm.KDE);
	            return true;	        	
	        case R.id.menu_fast:
	            search(FaceAlignProc.Algorithm.QUICK);
	            return true;	        
	        case R.id.menu_clean:
	        	mPlotView.addPlot(null);
		        mPlotView.invalidate();
		        return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}

	private FaceAlignProc mProc = null;
	private PlotView mPlotView = null;
	private Bitmap mCurrImage = null;
	private Context mAppCntx = null;
	
}
