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
		mModel = mod;
		
		mParams = new Mat(mModel.numEVectors+4, 1, CvType.CV_32F, new Scalar(0));
		mParams.put(0, 0, 1.0);
		//cvParams.put(4, 0, 50);
	}
	
	public void setPicture(Bitmap pic) {
		mImgOrig = pic;
	}
	
	public void initialPicAlign(Context ctx, float leftX, float leftY, float rightX, int rightY){
		int leftEyeIndex = mModel.pathModel.paths[mModel.pathModel.paths.length-2][0];
		int rightEyeIndex = mModel.pathModel.paths[mModel.pathModel.paths.length-1][0];
		
		float meanLeftX = (float)((mModel.shapeModel.cvData.meanShape.get(leftEyeIndex*2, 0))[0]);
		float meanLeftY = (float)((mModel.shapeModel.cvData.meanShape.get(leftEyeIndex*2+1, 0))[0]);
		float meanRightX = (float)((mModel.shapeModel.cvData.meanShape.get(rightEyeIndex*2, 0))[0]);
		float meanRightY = (float)((mModel.shapeModel.cvData.meanShape.get(rightEyeIndex*2+1, 0))[0]);	
		
		float meanAngle = (float)(Math.atan((meanRightY-meanLeftY)/(meanRightX-meanLeftX)));
		float currAngle = (float)(Math.atan((rightY-leftY)/(rightX-leftX)));
		
		float diffAngle = currAngle - meanAngle;
		
        float meanDist = (float)(Math.hypot(meanRightX-meanLeftX, meanRightY-meanLeftY));
        float currDist = (float)(Math.hypot(rightX-leftX, rightY-leftY));
        
        float scale = meanDist/currDist;
        mScaleFactor = scale;
        
        mImgProc = Bitmap.createScaledBitmap(mImgOrig, (int)(mImgOrig.getWidth()*scale), (int)(mImgOrig.getHeight()*scale), true);
        
        leftX *= scale;
        leftY *= scale;
        rightX *= scale;
        rightY *= scale;
        
        mParams.put(0, 0, Math.cos(diffAngle));
        mParams.put(1, 0, Math.sin(diffAngle));
        mParams.put(2, 0, (rightX+leftX)/2-(meanRightX+meanLeftX)/2);
        mParams.put(3, 0, (rightY+leftY)/2-(meanRightY+meanLeftY)/2);
        
        //Create the float image
        RenderScript rs = RenderScript.create(ctx);
        ScriptC_im2float script = new ScriptC_im2float(rs);
        Allocation inAlloc = Allocation.createFromBitmap(rs, mImgProc);
        Type.Builder tb = new Type.Builder(rs, Element.U8(rs));
        tb.setX(mImgProc.getWidth()).setY(mImgProc.getHeight());
        mImgGrayScale = new byte[mImgProc.getWidth()*mImgProc.getHeight()];
        Allocation outAlloc = Allocation.createTyped(rs, tb.create());
        script.forEach_root(inAlloc, outAlloc);
        outAlloc.copyTo(mImgGrayScale);	
        
        byte[] patches = cropPatches();
        mFilter = new Filter2D(ctx, 
        		mModel.patchModel.weightsList, 
        		mModel.patchModel.biasList, 
        				patches, 
        				mModel.numPts, 
        				mModel.patchModel.sampleWidth, 
        				mModel.patchModel.sampleHeight, 
        				(mModel.patchModel.sampleWidth+SEARCH_WIN_W-1),
        				(mModel.patchModel.sampleHeight+SEARCH_WIN_H-1));
        			
	}
	
	public void search(boolean last){
		
		mFilter.process();
		
		mResponses = mFilter.gerResponseImages();
		
		if (!last){
			mFilter.setPatches(cropPatches());
		}
	}
	
	public void plot(Canvas canvas, Paint paint){
		Mat curr = getCurrentShape();
		
		CvShape s = mModel.shapeModel.cvData;
		PathModel path = mModel.pathModel;
		
		if (mPlotContour){
			for (int i=0; i<path.paths.length; i++){
				for (int j=1; j<path.paths[i].length; j++){
					float startX = (float)((curr.get(path.paths[i][j-1]*2, 0))[0]);
					float startY = (float)((curr.get(path.paths[i][j-1]*2+1, 0))[0]);
					float endX = (float)((curr.get(path.paths[i][j]*2, 0))[0]);
					float endY = (float)((curr.get(path.paths[i][j]*2+1, 0))[0]);
					canvas.drawLine(startX/mScaleFactor, (float)(startY/mScaleFactor), (float)(endX/mScaleFactor), (float)(endY/mScaleFactor), paint);
				}
			}
		}
		else if (mPlotPatch){
			Paint pt = new Paint();
			byte[] patches = cropPatches();
			
			int offsetX = -(SEARCH_WIN_W+mModel.patchModel.sampleWidth-1)/2;
			int offsetY = -(SEARCH_WIN_H+mModel.patchModel.sampleHeight-1)/2;
			
			for (int i=0; i<mModel.numPts; i++){
				float centX = (float)((curr.get(i*2, 0))[0]);
				float centY = (float)((curr.get(i*2+1, 0))[0]);
				
				for (int j=0; j<SEARCH_WIN_H+mModel.patchModel.sampleHeight-1; j++){
					for (int k=0; k<(SEARCH_WIN_W+mModel.patchModel.sampleWidth-1); k++){
						int color = (patches[i*(SEARCH_WIN_W+mModel.patchModel.sampleWidth-1)*(SEARCH_WIN_H+mModel.patchModel.sampleHeight-1)+
						                    (SEARCH_WIN_W+mModel.patchModel.sampleWidth-1)*j+k])&0xFF;
						
						float left = (centX + k + offsetX)/mScaleFactor;
						float top = (centY + j + offsetY)/mScaleFactor;
						float right = (centX + k + offsetX + 1)/mScaleFactor;
						float bottom = (centY + j + offsetY + 1)/mScaleFactor;
								
						pt.setARGB(0xff, color, color, color);
						canvas.drawRect(left, top, right, bottom, pt);
					}
				}
			}
		}
		else if (mPlotResponse && mResponses!=null) {
			Paint pt = new Paint();
			
			int offsetX = -(SEARCH_WIN_W-1)/2;
			int offsetY = -(SEARCH_WIN_H-1)/2;
			
			for (int i=53; i<54; i++){
				float centX = (float)((curr.get(i*2, 0))[0]);
				float centY = (float)((curr.get(i*2+1, 0))[0]);
				
				for (int j=0; j<SEARCH_WIN_H; j++){
					for (int k=0; k<SEARCH_WIN_W; k++){
						int color = (int)((255*mResponses[i*SEARCH_WIN_W*SEARCH_WIN_H+j*SEARCH_WIN_W+k]));
						
						float left = (centX + k + offsetX)/mScaleFactor;
						float top = (centY + j + offsetY)/mScaleFactor;
						float right = (centX + k + offsetX + 1)/mScaleFactor;
						float bottom = (centY + j + offsetY + 1)/mScaleFactor;
						
						pt.setARGB(0xff, color, color, color);
						canvas.drawRect(left, top, right, bottom, pt);
					}
				}
			}
		}
		
		if (mPlotPts){
			Paint pt = new Paint();
			pt.setColor(0xFFFF0000);

			for (int i=0; i<mModel.numPts; i++){
				float centX = (float)((curr.get(i*2, 0))[0]);
				float centY = (float)((curr.get(i*2+1, 0))[0]);
				canvas.drawText(""+i, centX/mScaleFactor, centY/mScaleFactor, pt);
			}
		}
	}
	
	public Mat getCurrentShape(){
		Mat translate = getTranslateM();
		Mat sr = getScaleRotateM();
		Mat deviation = mParams.rowRange(4, mModel.numEVectors+4);
		CvShape s = mModel.shapeModel.cvData;
		
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
		Mat result = A.clone();
		
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
		
		mParams.get(0, 0, val);
        a = val[0];		
        mParams.get(1, 0, val);
		b = val[0];
		
		Mat result = Mat.zeros(mModel.numPts*2, mModel.numPts*2, CvType.CV_32F);
		
		for (int i=0; i<mModel.numPts; i++){
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
		
		mParams.get(2, 0, val);
		x = val[0];
		mParams.get(3, 0, val);
		y = val[0];
		
		Mat result = Mat.zeros(mModel.numPts*2, 1, CvType.CV_32F);
		for (int i=0; i<mModel.numPts; i++){
			result.put(i*2, 0, x);
			result.put(i*2+1, 0, y);
		}
		
		return result;
	}
	
	private byte[] cropPatches(){
		Mat currShape = getCurrentShape();
		
		int filterW = mModel.patchModel.sampleWidth;
		int filterH = mModel.patchModel.sampleHeight;
		int patchW = filterW+SEARCH_WIN_W-1;
		int patchH = filterH+SEARCH_WIN_H-1;
		int shiftW = -(patchW-1)/2;
		int shiftH = -(patchH-1)/2;
		byte[] ret = new byte[mModel.numPts*patchW*patchH];
		
		for (int i=0; i<mModel.numPts; i++){
			for (int j=0; j<patchH; j++){
				for (int k=0; k<patchW; k++){
					int posX = (int)((currShape.get(i*2, 0))[0]) + shiftW + k;
					int posY = (int)((currShape.get(i*2+1, 0))[0]) + shiftH + j;
					
					if (posX<0 || posY<0 || posX>mImgProc.getWidth() || posY>mImgProc.getHeight()){ //out of image
						ret[i*patchW*patchH+j*patchW+k] = 0; //set as black
					}
					else {						
						ret[i*patchW*patchH+j*patchW+k] = (mImgGrayScale[posY*mImgProc.getWidth() + posX]);
					}
				}
			}
		}
		return ret;
	}
	
	private Bitmap mImgOrig;
	private Bitmap mImgProc;
	private byte[] mImgGrayScale;
	private float mScaleFactor;
	private FaceModel mModel;
	
	private Mat mParams;
	
	private float[] mResponses;
	
	private Filter2D mFilter;
	
	private boolean mPlotContour = false;
	private boolean mPlotPts = true;
	private boolean mPlotPatch = false;
	private boolean mPlotResponse = true; //For test
	
	private static final int SEARCH_WIN_W = 11;
	private static final int SEARCH_WIN_H = 11;
}
