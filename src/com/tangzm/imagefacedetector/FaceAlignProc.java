package com.tangzm.imagefacedetector;

import org.opencv.core.Mat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

public class FaceAlignProc {
	public FaceAlignProc(Bitmap bmp){
		picture = bmp;
		
	}
	
	public void init(FaceModel mod){
		model = mod;
		
		params = new double[model.numEVectors + 4];
		params[0] = 1; //scale*cos(a)
		params[1] = 0; //scale*sin(a)
		params[2] = 0; //translateX
		params[3] = 0; //translateY
		for (int i=0; i<model.numEVectors; i++){
			params[4+i] = 0;
		}
	}
	
	public void drawPath(Canvas canvas, Paint paint){
		
	}
	
	private Bitmap picture;
	private FaceModel model;
	
	private double[] params;
}
