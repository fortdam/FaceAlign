package com.tangzm.imagefacedetector;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Range;
import org.opencv.core.Scalar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;


public class FaceAlignProc implements Plotable{
	
	public void init(FaceModel mod){
		model = mod;
		
		cvParams = new Mat(model.numEVectors+4, 1, CvType.CV_64F, new Scalar(0));
		cvParams.put(0, 0, 1.0);
		//cvParams.put(4, 0, 50);
		
		params = new double[model.numEVectors + 4];		
	}
	
	public void setPicture(Bitmap pic) {
		origPic = pic;
	}
	
	public void initialPicAlign(double leftX, double leftY, double rightX, int rightY){
		int leftEyeIndex = model.pathModel.paths[model.pathModel.paths.length-2][0];
		int rightEyeIndex = model.pathModel.paths[model.pathModel.paths.length-1][0];
		
		double meanLeftX = (model.shapeModel.cvData.meanShape.get(leftEyeIndex*2, 0))[0];
		double meanLeftY = (model.shapeModel.cvData.meanShape.get(leftEyeIndex*2+1, 0))[0];
		double meanRightX = (model.shapeModel.cvData.meanShape.get(rightEyeIndex*2, 0))[0];
		double meanRightY = (model.shapeModel.cvData.meanShape.get(rightEyeIndex*2+1, 0))[0];	
		
		double meanAngle = Math.atan((meanRightY-meanLeftY)/(meanRightX-meanLeftX));
		double currAngle = Math.atan((rightY-leftY)/(rightX-leftX));
		
		double diffAngle = currAngle - meanAngle;
		
        double meanDist = Math.hypot(meanRightX-meanLeftX, meanRightY-meanLeftY);
        double currDist = Math.hypot(rightX-leftX, rightY-leftY);
        
        double scale = meanDist/currDist;
        procScale = scale;
        
        procPic = Bitmap.createScaledBitmap(origPic, (int)(origPic.getWidth()*scale), (int)(origPic.getHeight()*scale), true);
        
        leftX *= scale;
        leftY *= scale;
        rightX *= scale;
        rightY *= scale;
        
        cvParams.put(0, 0, Math.cos(diffAngle));
        cvParams.put(1, 0, Math.sin(diffAngle));
        cvParams.put(2, 0, (rightX+leftX)/2-(meanRightX+meanLeftX)/2);
        cvParams.put(3, 0, (rightY+leftY)/2-(meanRightY+meanLeftY)/2);
	}
	
	public void plot(Canvas canvas, Paint paint){
		Mat curr = getCurrentShape();
		
		CvShape s = model.shapeModel.cvData;
		PathModel path = model.pathModel;
		
		for (int i=0; i<path.paths.length; i++){
			for (int j=1; j<path.paths[i].length; j++){
				double startX = (curr.get(path.paths[i][j-1]*2, 0))[0];
				double startY = (curr.get(path.paths[i][j-1]*2+1, 0))[0];
				double endX = (curr.get(path.paths[i][j]*2, 0))[0];
				double endY = (curr.get(path.paths[i][j]*2+1, 0))[0];
				canvas.drawLine((float)(startX/procScale), (float)(startY/procScale), (float)(endX/procScale), (float)(endY/procScale), paint);
			}
		}
		
	}
	
	public Mat getCurrentShape(){
		Mat translate = getTranslateM();
		Mat sr = getScaleRotateM();
		Mat deviation = cvParams.rowRange(4, model.numEVectors+4);
		CvShape s = model.shapeModel.cvData;
		
		return addM(addM(s.meanShape, mulM(s.eigenVectors, deviation)), translate);		
	}
	
	private Mat mulM(Mat A, Mat B){
		int h = A.height();
		int w = B.width();
		int type = A.type();
		
		Mat result = new Mat(h, w, type);
		
		for (int i=0; i<h; i++){
			for (int j=0; j<w; j++){
				double a = A.row(i).dot(B.col(j).t());
				result.put(i, j, a);
			}
		}
		return result;
	}
	
	private Mat addM(Mat A, Mat B){
		Mat result = new Mat(A, Range.all(), Range.all());
		
		for (int i=0; i<A.height(); i++){
			for (int j=0; j<A.width(); j++){
				double[] val = {1.0};
				double x,y;
				A.get(i, j, val);
				x = val[0];
				B.get(i, j, val);
				y = val[0];
				result.put(i, j, x+y);
			}
		}
		
		return result;
	}
	
	private Mat getScaleRotateM(){
		double[] val = new double[1];
		double a,b;
		
		cvParams.get(0, 0, val);
        a = val[0];		
		cvParams.get(1, 0, val);
		b = val[0];
		
		Mat result = Mat.zeros(model.numPts*2, model.numPts*2, CvType.CV_64F);
		
		for (int i=0; i<model.numPts; i++){
			result.put(i*2, i*2, a);
			result.put(i*2+1, i*2+1, a);
			result.put(i*2, i*2+1, -b);
			result.put(i*2+1, i*2, b);
		}
		
		return result;
	}
	
	private Mat getTranslateM(){
		double[] val = new double[1];
		double x,y;
		
		cvParams.get(2, 0, val);
		x = val[0];
		cvParams.get(3, 0, val);
		y = val[0];
		
		Mat result = Mat.zeros(model.numPts*2, 1, CvType.CV_64F);
		for (int i=0; i<model.numPts; i++){
			result.put(i*2, 0, x);
			result.put(i*2+1, 0, y);
		}
		
		return result;
	}
	
	private Bitmap origPic;
	private Bitmap procPic;
	private double procScale;
	private FaceModel model;
	
	private double[] params;
	private Mat cvParams;
}
