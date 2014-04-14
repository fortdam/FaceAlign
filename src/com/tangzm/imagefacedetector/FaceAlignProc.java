package com.tangzm.imagefacedetector;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Range;
import org.opencv.core.Scalar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;


public class FaceAlignProc implements Plotable{
	
	public void init(FaceModel mod){
		model = mod;
		
		cvParams = new Mat(model.numEVectors+4, 1, CvType.CV_32F, new Scalar(0));
		cvParams.put(0, 0, 1.0);
		//cvParams.put(4, 0, 50);
		
		params = new float[model.numEVectors + 4];		
	}
	
	public void setPicture(Bitmap pic) {
		origPic = pic;
	}
	
	public void initialPicAlign(Context ctx, float leftX, float leftY, float rightX, int rightY){
		int leftEyeIndex = model.pathModel.paths[model.pathModel.paths.length-2][0];
		int rightEyeIndex = model.pathModel.paths[model.pathModel.paths.length-1][0];
		
		float meanLeftX = (float)((model.shapeModel.cvData.meanShape.get(leftEyeIndex*2, 0))[0]);
		float meanLeftY = (float)((model.shapeModel.cvData.meanShape.get(leftEyeIndex*2+1, 0))[0]);
		float meanRightX = (float)((model.shapeModel.cvData.meanShape.get(rightEyeIndex*2, 0))[0]);
		float meanRightY = (float)((model.shapeModel.cvData.meanShape.get(rightEyeIndex*2+1, 0))[0]);	
		
		float meanAngle = (float)(Math.atan((meanRightY-meanLeftY)/(meanRightX-meanLeftX)));
		float currAngle = (float)(Math.atan((rightY-leftY)/(rightX-leftX)));
		
		float diffAngle = currAngle - meanAngle;
		
        float meanDist = (float)(Math.hypot(meanRightX-meanLeftX, meanRightY-meanLeftY));
        float currDist = (float)(Math.hypot(rightX-leftX, rightY-leftY));
        
        float scale = meanDist/currDist;
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
        
        //Create the float image
        RenderScript rs = RenderScript.create(ctx);
        ScriptC_im2float script = new ScriptC_im2float(rs);
        Allocation inAlloc = Allocation.createFromBitmap(rs, procPic);
        Type.Builder tb = new Type.Builder(rs, Element.U8(rs));
        tb.setX(procPic.getWidth()).setY(procPic.getHeight());
        picGrayScale = new byte[procPic.getWidth()*procPic.getHeight()];
        Allocation outAlloc = Allocation.createTyped(rs, tb.create());
        script.forEach_root(inAlloc, outAlloc);
        outAlloc.copyTo(picGrayScale);	
        
        byte[] patches = cropPatches();
        mFilter = new Filter2D(ctx, 
        				model.patchModel.weightsList, 
        				model.patchModel.biasList, 
        				patches, 
        				model.numPts, 
        				model.patchModel.sampleWidth, 
        				model.patchModel.sampleHeight, 
        				(model.patchModel.sampleWidth+SEARCH_WIN_W-1),
        				(model.patchModel.sampleHeight+SEARCH_WIN_H-1));	
	}
	
	public void search(boolean last){
		mFilter.process();
		
		float[] response = mFilter.gerResponseImages();
		
		if (!last){
			mFilter.setPatches(cropPatches());
		}
	}
	
	public void plot(Canvas canvas, Paint paint){
		Mat curr = getCurrentShape();
		
		CvShape s = model.shapeModel.cvData;
		PathModel path = model.pathModel;
		
		for (int i=0; i<path.paths.length; i++){
			for (int j=1; j<path.paths[i].length; j++){
				float startX = (float)((curr.get(path.paths[i][j-1]*2, 0))[0]);
				float startY = (float)((curr.get(path.paths[i][j-1]*2+1, 0))[0]);
				float endX = (float)((curr.get(path.paths[i][j]*2, 0))[0]);
				float endY = (float)((curr.get(path.paths[i][j]*2+1, 0))[0]);
				canvas.drawLine(startX/procScale, (float)(startY/procScale), (float)(endX/procScale), (float)(endY/procScale), paint);
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
				float a = (float)(A.row(i).dot(B.col(j).t()));
				result.put(i, j, a);
			}
		}
		return result;
	}
	
	private Mat addM(Mat A, Mat B){
		Mat result = new Mat(A, Range.all(), Range.all());
		
		for (int i=0; i<A.height(); i++){
			for (int j=0; j<A.width(); j++){
				float[] val = {1.0f};
				float x,y;
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
		float[] val = new float[1];
		float a,b;
		
		cvParams.get(0, 0, val);
        a = val[0];		
		cvParams.get(1, 0, val);
		b = val[0];
		
		Mat result = Mat.zeros(model.numPts*2, model.numPts*2, CvType.CV_32F);
		
		for (int i=0; i<model.numPts; i++){
			result.put(i*2, i*2, a);
			result.put(i*2+1, i*2+1, a);	

			result.put(i*2, i*2+1, -b);
			result.put(i*2+1, i*2, b);
		}
		
		return result;
	}
	
	private Mat getTranslateM(){
		float[] val = new float[1];
		float x,y;
		
		cvParams.get(2, 0, val);
		x = val[0];
		cvParams.get(3, 0, val);
		y = val[0];
		
		Mat result = Mat.zeros(model.numPts*2, 1, CvType.CV_32F);
		for (int i=0; i<model.numPts; i++){
			result.put(i*2, 0, x);
			result.put(i*2+1, 0, y);
		}
		
		return result;
	}
	
	private byte[] cropPatches(){
		Mat currShape = getCurrentShape();
		int filterW = model.patchModel.sampleWidth;
		int filterH = model.patchModel.sampleHeight;
		int patchW = filterW+SEARCH_WIN_W-1;
		int patchH = filterH+SEARCH_WIN_H-1;
		int shiftW = -(patchW-1)/2;
		int shiftH = -(patchH-1)/2;
		byte[] ret = new byte[model.numPts*patchW*patchH];
		
		for (int i=0; i<model.numPts; i++){
			for (int j=0; j<patchH; j++){
				for (int k=0; k<patchW; k++){
					int posX = (int)((currShape.get(i*2, 0))[0]) + shiftW;
					int posY = (int)((currShape.get(i*2+1, 0))[0]) + shiftH;
					
					if (posX<0 || posY<0 || posX>procPic.getWidth() || posY>procPic.getHeight()){ //out of image
						ret[i*patchW*patchH+j*patchW+k] = 0; //set as black
					}
					else {
						ret[i*patchW*patchH+j*patchW+k] = picGrayScale[posY*procPic.getWidth() + posX];
					}
				}
			}
		}
		
		return ret;
	}
	
	private Bitmap origPic;
	private Bitmap procPic;
	private byte[] picGrayScale;
	private float procScale;
	private FaceModel model;
	
	private float[] params;
	private Mat cvParams;
	
	private Filter2D mFilter;
	
	private static final int SEARCH_WIN_W = 11;
	private static final int SEARCH_WIN_H = 11;
}
