package com.tangzm.imagefacedetector;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;


public class FaceAlignProc implements Plotable{
	
	public void init(Context ctx, FaceModel model){
		mModel = model;
		
		mCurrentParams = new Mat(mModel.numEVectors+4, 1, CvType.CV_32F, new Scalar(0));
		mCurrentParams.put(0, 0, 1.0);
		
        mFilter = new Filter2D(ctx, 
        		mModel.patchModel.weightsList, 
        		mModel.patchModel.biasList, 
        		null, 
        		mModel.numPts, 
        		mModel.patchModel.sampleWidth, 
        		mModel.patchModel.sampleHeight, 
        		(mModel.patchModel.sampleWidth+SEARCH_WIN_W-1),
        		(mModel.patchModel.sampleHeight+SEARCH_WIN_H-1));
	}
	
	public void searchInImage(Context ctx, Bitmap image, float leftX, float leftY, float rightX, int rightY) throws Exception{
		if (null == mModel){
			throw new Exception("Not initialized");
		}
				
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
        
        Bitmap imgProcess = Bitmap.createScaledBitmap(image, (int)(image.getWidth()*scale), (int)(image.getHeight()*scale), true);
        
        leftX *= scale;
        leftY *= scale;
        rightX *= scale;
        rightY *= scale;
        
        mCurrentParams.put(0, 0, Math.cos(diffAngle));
        mCurrentParams.put(1, 0, Math.sin(diffAngle));
        mCurrentParams.put(2, 0, (rightX+leftX)/2-(meanRightX+meanLeftX)/2);
        mCurrentParams.put(3, 0, (rightY+leftY)/2-(meanRightY+meanLeftY)/2);
        
        mImageW = imgProcess.getWidth();
        mImageH = imgProcess.getHeight();
        
        //Create the float image
        RenderScript rs = RenderScript.create(ctx);
        ScriptC_im2float script = new ScriptC_im2float(rs);
        Allocation inAlloc = Allocation.createFromBitmap(rs, imgProcess);
        Type.Builder tb = new Type.Builder(rs, Element.U8(rs));
        tb.setX(mImageW).setY(mImageH);
        mImgGrayScaled = new byte[mImageW*mImageH];
        Allocation outAlloc = Allocation.createTyped(rs, tb.create());
        script.forEach_root(inAlloc, outAlloc);
        outAlloc.copyTo(mImgGrayScaled);		
        
        mCurrentPositions = getCurrentShape();
        mOriginalPositions = mCurrentPositions.clone();
	}
	
	private Mat getShape(final Mat params){
		Mat translate = getTranslateM(params);
		Mat sr = getScaleRotateM(params);
		Mat deviation = params.rowRange(4, mModel.numEVectors+4);
		CvShape s = mModel.shapeModel.cvData;
		
		return addM(mulM(sr, addM(s.meanShape, mulM(s.eigenVectors, deviation))), translate);		
	}
	
	private Mat getCurrentShape(){
		return getShape(mCurrentParams);
	}
	
	private Mat regularizeParams(Mat params){
		for (int i=0; i<mModel.numEVectors; i++){
			double value = params.get(i+4, 0)[0];
			double constrain = mModel.shapeModel.eigenConstraints[i];
			
			if (value > constrain){
				params.put(i+4, 0, constrain);
			}
			else if (value < -constrain){
				params.put(i+4, 0, -constrain);
			}
		}
		return params;
	}
	
	private void updateCurrent(Mat newPositions){
		Mat jacob = createJacobian(mCurrentParams);
		Mat transJacob = jacob.t();
		
		Mat deltaParams = mulM(mulM((mulM(transJacob, jacob)).inv(Core.DECOMP_SVD), transJacob), subM(newPositions, mCurrentPositions));
		
		mCurrentParams = regularizeParams(addM(deltaParams, mCurrentParams));
		mCurrentPositions = getShape(mCurrentParams);
	}
	
	private Mat doMeanShift(final float[] responseImg, final Mat currPositions, final int variance){
		Mat newPositions = new Mat(mModel.numPts*2, 1, CvType.CV_32F, new Scalar(0));
		
		float[] gaussKernel = new float[SEARCH_WIN_W*SEARCH_WIN_H];
		
		for (int i=0; i<mModel.numPts; i++){
			
			float startX = (float)(mCropPositions.get(i*2, 0)[0] - (SEARCH_WIN_W-1)/2);
			float startY = (float)(mCropPositions.get(i*2+1, 0)[0] - (SEARCH_WIN_H-1)/2);		
			float dXBase = (float)(mCropPositions.get(i*2, 0)[0] - currPositions.get(i*2, 0)[0] - (SEARCH_WIN_W-1)/2);
			float dYBase = (float)(mCropPositions.get(i*2+1, 0)[0] - currPositions.get(i*2+1, 0)[0] - (SEARCH_WIN_H-1)/2);
			
			int respImgOffset = i*SEARCH_WIN_W*SEARCH_WIN_H;
			
			float denominator = 0;
			float numeratorX = 0;
			float numeratorY = 0;
			
			for (int j=0; j<SEARCH_WIN_H; j++){
				for (int k=0; k<SEARCH_WIN_W; k++){
					float dX = dXBase+k;
					float dY = dYBase+j;


					gaussKernel[j*SEARCH_WIN_W + k] = (float)(Math.exp(-0.5*(dX*dX+dY*dY)/variance)) * 
							responseImg[respImgOffset+j*SEARCH_WIN_W+k];					
					
					denominator += gaussKernel[j*SEARCH_WIN_W + k];
				}
			}
			
			for (int j=0; j<SEARCH_WIN_H; j++){
				for (int k=0; k<SEARCH_WIN_W; k++){
					numeratorX += (startX+k)*gaussKernel[j*SEARCH_WIN_W + k];
					numeratorY += (startY+j)*gaussKernel[j*SEARCH_WIN_W + k];
				}
			}

			newPositions.put(i*2, 0, numeratorX/denominator);
			newPositions.put(i*2+1, 0, numeratorY/denominator);
		}
		
		return newPositions;
	}
	
	private void searchMeanShift(float[] responseImg){
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 10));
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 5));
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 1));
	}
	
	private Mat doChoosePeak(final float[] responseImg){
		Mat newPositions = new Mat(mModel.numPts*2, 1, CvType.CV_32F, new Scalar(0));
				
		for (int i=0; i<mModel.numPts; i++){
			float peak = 0;
			int peakX = 0;
			int peakY = 0;
			int respImgOffset = i*SEARCH_WIN_W*SEARCH_WIN_H;
			
			for (int j=0; j<SEARCH_WIN_W; j++){
				for (int k=0; k<SEARCH_WIN_H; k++){
					if (responseImg[respImgOffset+k*SEARCH_WIN_W+j]>peak){
						peak = responseImg[respImgOffset+k*SEARCH_WIN_W+j];
						peakX = j;
						peakY = k;
					}
				}
			}

			newPositions.put(i*2, 0, (float)(mCropPositions.get(i*2, 0)[0] - (SEARCH_WIN_W-1)/2) + peakX);
			newPositions.put(i*2+1, 0, (float)(mCropPositions.get(i*2+1, 0)[0] - (SEARCH_WIN_H-1)/2) + peakY);
		}
		
		return newPositions;
	}
	
	private void searchPeak(float[] responseImg){
		updateCurrent(doChoosePeak(responseImg));
	}
	
	public void optimize(Algorithm type) throws Exception{
		//Get Filter response
		mFilter.setPatches(cropPatches());
		
		//Optimize algorithm
		if (Algorithm.ASM == type){
			mFilter.process(false);
			searchPeak(mFilter.gerResponseImages());
		}
		else if (Algorithm.MEAN_SHIFT == type){
			mFilter.process(true);
			searchMeanShift(mFilter.gerResponseImages());
		}
		else {
			throw new Exception("Unsupported algorithm");
		}
		
	}
	

	
	private Mat mulM(final Mat A, final Mat B){
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
	
	private Mat addM(final Mat A, final Mat B){
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
	
	private Mat subM(final Mat A, final Mat B){
		Mat result = A.clone();
		
		for (int i=0; i<A.height(); i++){
			for (int j=0; j<A.width(); j++){
				result.put(i, j, A.get(i, j)[0] - B.get(i, j)[0]);
			}
		}
		
		return result;
	}
	
	private Mat getScaleRotateM(final Mat params){
		return getScaleRotateM(params, 1, 0);
	}
	
	private Mat getScaleRotateM(final float scale, final float theta){
		return getScaleRotateM(null, scale, theta);
	}
	
	private Mat getScaleRotateM(final Mat params, final float scale, final float theta){		
        float scaleFactor = scale;
        float rotateAngle = theta;
        
        if (params!=null){
    		float a = (float)(params.get(0, 0)[0]);	
            float b = (float)(params.get(1, 0)[0]);
            
            scaleFactor *= Math.sqrt(a*a + b*b);
            
            if (Math.abs(a) > 0.001){
            	rotateAngle += Math.atan(b/a);
            }
        }
		
        double v1 = scaleFactor * Math.cos(rotateAngle);
        double v2 = scaleFactor * Math.sin(rotateAngle);
        
		Mat result = Mat.zeros(mModel.numPts*2, mModel.numPts*2, CvType.CV_32F);
		
		for (int i=0; i<mModel.numPts; i++){
			result.put(i*2, i*2, v1);
			result.put(i*2+1, i*2+1, v1);	

			result.put(i*2, i*2+1, -v2);
			result.put(i*2+1, i*2, v2);
		}
		
		return result;
	}

	private Mat getTranslateM(final Mat params){
		return getTranslateM(params, 0, 0);
	}
	
	private Mat getTranslateM(final float offsetX, final float offsetY){
		return getTranslateM(null, offsetX, offsetY);
	}
	
	private Mat getTranslateM(final Mat params, final float offsetX, final float offsetY){
		float x =  offsetX;
		float y =  offsetY;
		
		if (params != null){
			x += (float)(params.get(2, 0)[0]);
			y += (float)(params.get(3, 0)[0]);
		}
		
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
		
		mCropPositions = currShape;
		
		for (int i=0; i<mModel.numPts; i++){
			for (int j=0; j<patchH; j++){
				for (int k=0; k<patchW; k++){
					int posX = (int)((currShape.get(i*2, 0))[0]) + shiftW + k;
					int posY = (int)((currShape.get(i*2+1, 0))[0]) + shiftH + j;
					
					if (posX<0 || posY<0 || posX>mImageW || posY>mImageH){ //out of image
						ret[i*patchW*patchH+j*patchW+k] = 0; //set as black
					}
					else {						
						ret[i*patchW*patchH+j*patchW+k] = (mImgGrayScaled[(int)(posY*mImageW) + posX]);
					}
				}
			}
		}
		return ret;
	}
	
	private Mat createJacobian (final Mat params){
		Mat jac = new Mat(mModel.numPts*2, mCurrentParams.rows(), CvType.CV_32F);
		double j0,j1;
		
		Mat meanShape = mModel.shapeModel.cvData.meanShape;
		Mat eigenVectors = mModel.shapeModel.cvData.eigenVectors;
		
		for (int i=0; i<mModel.numPts; i++){
			//1
			j0 = meanShape.get(i*2, 0)[0];
			j1 = meanShape.get(i*2+1, 0)[0];
			
			for (int j=0; j<eigenVectors.cols(); j++){
				j0 += params.get(j+4, 0)[0]*eigenVectors.get(i*2, j)[0];
				j1 += params.get(j+4, 0)[0]*eigenVectors.get(i*2+1, j)[0];
			}
			jac.put(i*2, 0, j0);
			jac.put(i*2+1, 0, j1);
			
			//2
			j0 = meanShape.get(i*2+1, 0)[0];
			j1 = meanShape.get(i*2, 0)[0];	
			
			for (int j=0; j<eigenVectors.cols(); j++){
				j0 += params.get(j+4, 0)[0]*eigenVectors.get(i*2+1, j)[0];
				j1 += params.get(j+4, 0)[0]*eigenVectors.get(i*2, j)[0];
			}
			jac.put(i*2, 1, -j0);
			jac.put(i*2+1, 1, j1);
			
			//3
			jac.put(i*2, 2, 1);
			jac.put(i*2+1, 2, 0);
			
			//4
			jac.put(i*2, 3, 0);
			jac.put(i*2+1, 3, 1);
			
			for (int j=0; j<eigenVectors.cols(); j++){
				j0 = params.get(0, 0)[0]*eigenVectors.get(i*2, j)[0] - params.get(1, 0)[0]*eigenVectors.get(i*2+1, j)[0];
				j1 = params.get(0, 0)[0]*eigenVectors.get(i*2+1, j)[0] + params.get(1, 0)[0]*eigenVectors.get(i*2, j)[0];
				jac.put(i*2, 4+j, j0);
				jac.put(i*2+1, 4+j, j1);
			}
		}
		
		return jac;
	}
	
	//For test only
	private Mat makeTestDate(Mat params) {
		Mat ret = new Mat(mModel.numPts*2, 1, CvType.CV_32F);
		
		Mat translate;
		Mat sr;
		Mat deviation = params.rowRange(4, mModel.numEVectors+4).clone();
		
		//Test translate
		if (true){
			translate = getTranslateM(params, 60,0);
		}
		else {
			translate = getTranslateM(params);
		}
		
		//Test rotation
		if (false){
			sr = getScaleRotateM(params, 1.2f, (float)(Math.PI/6));		
		}
		else {
			sr = getScaleRotateM(params);
		}
		
		//Test eigen vectors
		if (true) {
			//Check spca modeling at http://auduno.github.io/clmtrackr/examples/modelviewer_spca.html 
			
			// eigen values  |  3*sqrt(value)   |   usage
			//________________________________________________________________________
			//0: 637.328     |  75.735          |           pose turn left<- ->pose turn right
			//1: 106.592     |  30.972          |             pose turn up<- ->pose turn down
			//2: 30.445      |  16.551          |     surprise/happy/laugh<- ->Angry
			//3: 17.036      |  12.381          |                  yelling<- ->keep silent
			//4: 8.493       |  8.742           |  big eye-dist(oval face)<- ->small eye-dist (diamond face)
			//5: 8.296       |  8.64            |             narrow mouth<- ->wide mouth
			//6: 7.969       |  8.466           |              close mouth<- ->open mouth(talking)
			//7: 6.810       |  7.827           |      skew face(to right)<- ->skew face(to left)
			//8: 6.625       |  7.719           |                wide face<- ->narrow face
			//9: 5.248       |  6.870           |               small eyes<- ->big eyes
			//10: 5.211      |  6.846           |             right eye up<- ->left eye up
			//11: 4.895      |  6.636           |    similar as 4?
			//12: 4.783      |  6.561           |             long eyebrow<- ->short eyebrow
			//13: 4.586      |  6.423           |    combination of 10 and 9    
			//14: 3.976      |  5.979           |                feel pity<- ->smile/feel happy
			//15: 3.905      |  5.928           |              narrow chin<- ->wide chin
			//16: 3.124      |  5.301           |             chin to left<- ->chin to right
			//17: 2.831      |  5.046           |          big brow center<- ->small brow center(long eye and brow)
			//18: 2.7        |  4.929           |        high face contour<- ->low face contour fit
			//19: 2.345      |  4.593           |      nose 2 l(mouth 2 r)<- ->nose to right(mouth to left)
			
			
			float[] values = {
					0f,   //0
					0f,   //1
					0f,   //2
					0f,   //3
					0f,   //4
					0f,   //5
					0f,   //6
					0f,   //7
					0f,   //8
					0f,   //9
					0f,   //10
					0f,   //11
					0f,   //12
					0f,   //13
					0f,   //14
					0f,   //15
					0f,   //16
					0f,   //17
					0f,   //18
					0f,   //19
					};
			
			for (int i=0; i<values.length; i++){
				if (Math.abs(values[i]) > 0.0001){
				    deviation.put(i, 0, deviation.get(i, 0)[0] + values[i]);
				}
			}
		}
		else{
			
		}
		
		CvShape s = mModel.shapeModel.cvData;
		
		return addM(mulM(sr, addM(s.meanShape, mulM(s.eigenVectors, deviation))), translate);		
	}
	
	public void plot(Canvas canvas, Paint paint){		
		CvShape s = mModel.shapeModel.cvData;
		PathModel path = mModel.pathModel;
		

		if (test_PlotPatch){
			Paint pt = new Paint();
			byte[] patches = cropPatches();
			
			int offsetX = -(SEARCH_WIN_W+mModel.patchModel.sampleWidth-1)/2;
			int offsetY = -(SEARCH_WIN_H+mModel.patchModel.sampleHeight-1)/2;
			
			for (int i=0; i<mModel.numPts; i++){
				float centX = (float)((mCropPositions.get(i*2, 0))[0]);
				float centY = (float)((mCropPositions.get(i*2+1, 0))[0]);
				
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
		else if (test_PlotResponse) {
			Paint pt = new Paint();
			float[] responses = mFilter.gerResponseImages();
			
			int offsetX = -(SEARCH_WIN_W-1)/2;
			int offsetY = -(SEARCH_WIN_H-1)/2;
			
			for (int i=7; i<8; i++){
				float centX = (float)((mCropPositions.get(i*2, 0))[0]);
				float centY = (float)((mCropPositions.get(i*2+1, 0))[0]);
				
				for (int j=0; j<SEARCH_WIN_H; j++){
					for (int k=0; k<SEARCH_WIN_W; k++){
						int color = (int)((255*responses[i*SEARCH_WIN_W*SEARCH_WIN_H+j*SEARCH_WIN_W+k]));
						
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
		
		if (test_PlotContour){
			for (int i=0; i<path.paths.length; i++){
				Paint pt = new Paint();
				pt.setStrokeWidth(2);
				
				if (test_PlotOriginal){
					pt.setColor(0xFFFF0000);

					for (int j=1; j<path.paths[i].length; j++){
						float startX = (float)((mOriginalPositions.get(path.paths[i][j-1]*2, 0))[0]);
						float startY = (float)((mOriginalPositions.get(path.paths[i][j-1]*2+1, 0))[0]);
						float endX = (float)((mOriginalPositions.get(path.paths[i][j]*2, 0))[0]);
						float endY = (float)((mOriginalPositions.get(path.paths[i][j]*2+1, 0))[0]);
						canvas.drawLine(startX/mScaleFactor, (float)(startY/mScaleFactor), (float)(endX/mScaleFactor), (float)(endY/mScaleFactor), pt);
					}				
				}
				
				pt.setColor(0xFF00FF00);
				
				for (int j=1; j<path.paths[i].length; j++){
					float startX = (float)((mCurrentPositions.get(path.paths[i][j-1]*2, 0))[0]);
					float startY = (float)((mCurrentPositions.get(path.paths[i][j-1]*2+1, 0))[0]);
					float endX = (float)((mCurrentPositions.get(path.paths[i][j]*2, 0))[0]);
					float endY = (float)((mCurrentPositions.get(path.paths[i][j]*2+1, 0))[0]);
					canvas.drawLine(startX/mScaleFactor, (float)(startY/mScaleFactor), (float)(endX/mScaleFactor), (float)(endY/mScaleFactor), pt);
				}
				
				
			}
		}
		else if (test_PlotPts){
			Paint pt = new Paint();
			
			if (test_PlotOriginal){
				pt.setColor(0xFFFF0000);

				for (int i=0; i<mModel.numPts; i++){
					float centX = (float)((mOriginalPositions.get(i*2, 0))[0]);
					float centY = (float)((mOriginalPositions.get(i*2+1, 0))[0]);
					canvas.drawText(""+i, centX/mScaleFactor, centY/mScaleFactor, pt);
				}
			}
			
			pt.setColor(0xFF00FF00);

			for (int i=0; i<mModel.numPts; i++){
				float centX = (float)((mCurrentPositions.get(i*2, 0))[0]);
				float centY = (float)((mCurrentPositions.get(i*2+1, 0))[0]);
				canvas.drawText(""+i, centX/mScaleFactor, centY/mScaleFactor, pt);
			}
			

		}
		
		if (test_PlotParams){
			//Check spca modeling at http://auduno.github.io/clmtrackr/examples/modelviewer_spca.html 
			
			// eigen values  |  3*sqrt(value)   |   usage
			//________________________________________________________________________
			//0: 637.328     |  75.735          |           pose turn left<- ->pose turn right
			//1: 106.592     |  30.972          |             pose turn up<- ->pose turn down
			//2: 30.445      |  16.551          |     surprise/happy/laugh<- ->Angry
			//3: 17.036      |  12.381          |                  yelling<- ->keep silent
			//4: 8.493       |  8.742           |  big eye-dist(oval face)<- ->small eye-dist (diamond face)
			//5: 8.296       |  8.64            |             narrow mouth<- ->wide mouth
			//6: 7.969       |  8.466           |              close mouth<- ->open mouth(talking)
			//7: 6.810       |  7.827           |      skew face(to right)<- ->skew face(to left)
			//8: 6.625       |  7.719           |                wide face<- ->narrow face
			//9: 5.248       |  6.870           |               small eyes<- ->big eyes
			//10: 5.211      |  6.846           |             right eye up<- ->left eye up
			//11: 4.895      |  6.636           |    similar as 4?
			//12: 4.783      |  6.561           |             long eyebrow<- ->short eyebrow
			//13: 4.586      |  6.423           |    combination of 10 and 9    
			//14: 3.976      |  5.979           |                feel pity<- ->smile/feel happy
			//15: 3.905      |  5.928           |              narrow chin<- ->wide chin
			//16: 3.124      |  5.301           |             chin to left<- ->chin to right
			//17: 2.831      |  5.046           |          big brow center<- ->small brow center(long eye and brow)
			//18: 2.7        |  4.929           |        high face contour<- ->low face contour fit
			//19: 2.345      |  4.593           |      nose 2 l(mouth 2 r)<- ->nose to right(mouth to left)
			
			
			Paint pt = new Paint();
			int font = 30;
			pt.setColor(0xFF00FF00);
			pt.setTextSize(font);
			String text = new String();
			
			String[] paramLabels = {
					"left/right: ",
					"up/down: ",
					"laugth/angry: ",
					"yelling/silient: ",
					"oval/diamond face: ",
					"narrow/wide mouth: ",
					"close/open mouth: ",
					"skew to right/left: ",
					"wide/narrow face: ",
					"small/big eyes: ",
					"right/left eye up: ",
					"oval/diamond face??: ",
					"long/short eyebrow: ",
					"xxx: ",
					"feel pity/happy: ",
					"narrow/wide chin: ",
					"chin to left/right: ",
					"big/small brow center: ",
					"high/low face contour: ",
					"nose to left/right against mouth: "
			};
			
			float[] paramsLimit = {
					75.735f,
					30.972f,
					16.551f,
					12.381f,
					8.742f,
					8.64f,
					8.466f,
					7.827f,
					7.719f, 
					6.870f,
					6.846f,
					6.636f,
					6.561f,
					6.423f,
					5.979f, 
					5.928f,
					5.301f,
					5.046f, 
					4.929f, 
					4.593f 
			};
			
			for (int i=0; i<paramsLimit.length; i++){
				int value = (int)((mCurrentParams.get(i+4, 0)[0] * 50)/paramsLimit[i]) + 50;
				
				canvas.drawText(paramLabels[i] + value + "/100", 0, (i+1)*(font+5), pt);		
			}
		}
	}
	
	public enum Algorithm {
		ASM,
		CQF, //Not implemented
		MEAN_SHIFT 
	};
	
	private static final int SEARCH_WIN_W = 11;
	private static final int SEARCH_WIN_H = 11;
	
	//Image Data
	private int mImageW;
	private int mImageH;
	private byte[] mImgGrayScaled;
	private float mScaleFactor;
	
	private FaceModel mModel;
	
	//Fit data / result
	private Mat mOriginalPositions; //The positions for initial guess
	private Mat mCropPositions;   //The positions for patch cropped
	private Mat mCurrentPositions;  //The positions for current calculated
	private Mat mCurrentParams;
		
	private Filter2D mFilter;
	
	//For test / plotting
	private boolean test_PlotContour = true;
	private boolean test_PlotPts = false;
	
	private boolean test_PlotOriginal = false;
	
	private boolean test_PlotParams = true;
	
	private boolean test_PlotPatch = false;
	private boolean test_PlotResponse = false;
	//For test / plotting
}

