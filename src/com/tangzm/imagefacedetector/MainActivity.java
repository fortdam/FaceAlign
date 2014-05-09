package com.tangzm.imagefacedetector;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;

public class MainActivity extends Activity{

	private static final String TAG="ImageFaceDetect";
	private static final int GALLERY_INTENT_ID = 0x1515;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}
	
	@Override
	protected void onResume(){
		super.onResume();
	}
	
	public void onClickImage(View view){
		startGalleryForPic();
	}
	
	public void onClickCamera(View view){
		startActivity(new Intent(this, CameraActivity.class));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}


	@Override
	protected void onActivityResult (int requestCode, int resultCode, Intent data){		
		if (GALLERY_INTENT_ID == requestCode){
			Intent intent = new Intent(this, ImageActivity.class);
			intent.putExtra("uri", data.getData());
			startActivity(intent);
		}
	}

	private void startGalleryForPic() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("image/*");
		startActivityForResult(intent, GALLERY_INTENT_ID);
	}
}
