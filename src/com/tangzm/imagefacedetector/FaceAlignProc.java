package com.tangzm.imagefacedetector;

import org.ejml.simple.SimpleMatrix;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;


public class FaceAlignProc implements Plotable{
	
	public void init(Context ctx, FaceModel model){
		mModel = model;
		
        mFilter = new Filter2D(
        		ctx, 
        		mModel.patchModel.weightsList, 
        		mModel.patchModel.biasList, 
        		null, 
        		mModel.numPts, 
        		mModel.patchModel.sampleWidth, 
        		mModel.patchModel.sampleHeight, 
        		(mModel.patchModel.sampleWidth+SEARCH_WIN_W-1),
        		(mModel.patchModel.sampleHeight+SEARCH_WIN_H-1));
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
		return ret;
	}
	
	public void searchInImage(Context ctx, Bitmap image) throws Exception{
		float[] eyePositions = makeInitialGuess(image);
		
		if (null == eyePositions){
			throw new Exception("Can not find face");
		}
		
		searchInImage(ctx, image, eyePositions[0], eyePositions[1], eyePositions[2], eyePositions[3]);
	}
	
	public void searchInImage(Context ctx, Bitmap image, float leftX, float leftY, float rightX, float rightY) throws Exception{
		if (null == mModel){
			throw new Exception("Not initialized");
		}
				
		int leftEyeIndex = mModel.pathModel.paths[mModel.pathModel.paths.length-2][0];
		int rightEyeIndex = mModel.pathModel.paths[mModel.pathModel.paths.length-1][0];
		
		double meanLeftX = mModel.shapeModel.mMeanShape.get(leftEyeIndex*2);
        double meanLeftY = mModel.shapeModel.mMeanShape.get(leftEyeIndex*2+1);
        double meanRightX = mModel.shapeModel.mMeanShape.get(rightEyeIndex*2);
        double meanRightY = mModel.shapeModel.mMeanShape.get(rightEyeIndex*2+1);
		
		double meanAngle = Math.atan((meanRightY-meanLeftY)/(meanRightX-meanLeftX));
		double currAngle = Math.atan((rightY-leftY)/(rightX-leftX));
		
		double diffAngle = currAngle - meanAngle;
		
        double meanDist = Math.hypot(meanRightX-meanLeftX, meanRightY-meanLeftY);
        double currDist = Math.hypot(rightX-leftX, rightY-leftY);
        
        double scale = meanDist/currDist;
        mScaleFactor = scale;
        
        Bitmap imgProcess = Bitmap.createScaledBitmap(image, (int)(image.getWidth()*scale), (int)(image.getHeight()*scale), true);
        
        leftX *= scale;
        leftY *= scale;
        rightX *= scale;
        rightY *= scale;
        
		mCurrentParams = new SimpleMatrix(mModel.numEVectors+4, 1);
		
		mCurrentParams.set(0, Math.cos(diffAngle)); //Alpha * cos(theta)
		mCurrentParams.set(1, Math.sin(diffAngle)); //Alpha * sin(theta)
		mCurrentParams.set(2, (rightX+leftX)/2-(meanRightX+meanLeftX)/2); //translate X
		mCurrentParams.set(3, (rightY+leftY)/2-(meanRightY+meanLeftY)/2);  //translate Y
        
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
        mOriginalPositions = new SimpleMatrix(mCurrentPositions);
	}
	
	private SimpleMatrix getShape(final SimpleMatrix params){
		SimpleMatrix translate = getTranslateM(params);
		SimpleMatrix sr = getScaleRotateM(params);
		SimpleMatrix deviation = params.extractMatrix(4, mModel.numEVectors+4, 0, 1);
		
		SimpleMatrix evec = mModel.shapeModel.mEigenVectors;
		SimpleMatrix mean = mModel.shapeModel.mMeanShape;
		
		return sr.mult(mean.plus(evec.mult(deviation))).plus(translate);
	}
	
	private SimpleMatrix getCurrentShape(){
		return getShape(mCurrentParams);
	}
	
	private SimpleMatrix regularizeParams(SimpleMatrix params){
		for (int i=0; i<mModel.numEVectors; i++){
			double value = params.get(i+4);
			double constrain = mModel.shapeModel.mEigenConstraints.get(i);
			
			if (value > constrain){
				params.set(i+4, constrain);
			}
			else if (value < -constrain){
				params.set(i+4, -constrain);
			}
		}
		return params;
	}
	
	private void updateCurrent(SimpleMatrix newPositions){
		SimpleMatrix jacob = createJacobian(mCurrentParams);
		SimpleMatrix transJacob = jacob.transpose();
		
		SimpleMatrix deltaParams = transJacob.mult(jacob).pseudoInverse().mult(transJacob).mult(newPositions.minus(mCurrentPositions));

		mCurrentParams = regularizeParams(deltaParams.plus(mCurrentParams));
		mCurrentPositions = getShape(mCurrentParams);
	}
	
	private SimpleMatrix doMeanShift(final float[] responseImg, final SimpleMatrix currPositions, final int variance){
		SimpleMatrix newPositions = new SimpleMatrix(mModel.numPts*2, 1);
		
		float[] gaussKernel = new float[SEARCH_WIN_W*SEARCH_WIN_H];
		
		for (int i=0; i<mModel.numPts; i++){
			
			double startX = mCropPositions.get(i*2) - (SEARCH_WIN_W-1)/2;
			double startY = mCropPositions.get(i*2+1)- (SEARCH_WIN_H-1)/2;		
			double dXBase = mCropPositions.get(i*2) - currPositions.get(i*2) - (SEARCH_WIN_W-1)/2;
			double dYBase = mCropPositions.get(i*2+1) - currPositions.get(i*2+1) - (SEARCH_WIN_H-1)/2;
			
			int respImgOffset = i*SEARCH_WIN_W*SEARCH_WIN_H;
			
			double denominator = 0;
			double numeratorX = 0;
			double numeratorY = 0;
			
			for (int j=0; j<SEARCH_WIN_H; j++){
				for (int k=0; k<SEARCH_WIN_W; k++){
					double dX = dXBase+k;
					double dY = dYBase+j;


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

			newPositions.set(i*2, numeratorX/denominator);
			newPositions.set(i*2+1, numeratorY/denominator);
		}
		
		return newPositions;
	}
	
	private void searchMeanShift(float[] responseImg){
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 10));
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 5));
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 1));
	}
	
	private SimpleMatrix doChoosePeak(final float[] responseImg){
		SimpleMatrix newPositions = new SimpleMatrix(mModel.numPts*2, 1);
				
		for (int i=0; i<mModel.numPts; i++){
			double peak = 0;
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

			newPositions.set(i*2, 0, mCropPositions.get(i*2) - (SEARCH_WIN_W-1)/2 + peakX);
			newPositions.set(i*2+1, 0, mCropPositions.get(i*2+1) - (SEARCH_WIN_H-1)/2 + peakY);
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
	
	
	private SimpleMatrix getScaleRotateM(final SimpleMatrix params){
		return getScaleRotateM(params, 1, 0);
	}
	
	private SimpleMatrix getScaleRotateM(final double scale, final double theta){
		return getScaleRotateM(null, scale, theta);
	}
	
	private SimpleMatrix getScaleRotateM(final SimpleMatrix params, final double scale, final double theta){		
        double scaleFactor = scale;
        double rotateAngle = theta;
        
        if (params!=null){
    		double a = params.get(0, 0);	
            double b = params.get(1, 0);
            
            scaleFactor *= Math.sqrt(a*a + b*b);
            
            if (Math.abs(a) > 0.001){
            	rotateAngle += Math.atan(b/a);
            }
        }
		
        double v1 = scaleFactor * Math.cos(rotateAngle);
        double v2 = scaleFactor * Math.sin(rotateAngle);
        
		SimpleMatrix result = new SimpleMatrix(mModel.numPts*2, mModel.numPts*2);
		result.set(0);
		
		for (int i=0; i<mModel.numPts; i++){
			result.set(i*2, i*2, v1);
			result.set(i*2+1, i*2+1, v1);	
			result.set(i*2, i*2+1, -v2);
			result.set(i*2+1, i*2, v2);
		}
		
		return result;
	}

	private SimpleMatrix getTranslateM(final SimpleMatrix params){
		return getTranslateM(params, 0, 0);
	}
	
	private SimpleMatrix getTranslateM(final double offsetX, final double offsetY){
		return getTranslateM(null, offsetX, offsetY);
	}
	
	private SimpleMatrix getTranslateM(final SimpleMatrix params, final double offsetX, final double offsetY){
		double x =  offsetX;
		double y =  offsetY;
		
		if (params != null){
			x += params.get(2,0);
			y += params.get(3, 0);
		}
		
		SimpleMatrix result = new SimpleMatrix(mModel.numPts*2, 1);
		for (int i=0; i<mModel.numPts; i++){
			result.set(i*2, 0, x);
			result.set(i*2+1, 0, y);
		}
		
		return result;
	}
	
	private byte[] cropPatches(){
		SimpleMatrix currShape = getCurrentShape();
		
		int filterW = mModel.patchModel.sampleWidth;
		int filterH = mModel.patchModel.sampleHeight;
		int patchW = filterW+SEARCH_WIN_W-1;
		int patchH = filterH+SEARCH_WIN_H-1;
		int shiftW = -(patchW-1)/2;
		int shiftH = -(patchH-1)/2;
		byte[] ret = new byte[mModel.numPts*patchW*patchH];
		
		mCropPositions = currShape;
		
		for (int i=0; i<mModel.numPts; i++){
			int centerX = (int)(currShape.get(i*2));
			int centerY = (int)(currShape.get(i*2+1));
			for (int j=0; j<patchH; j++){
				for (int k=0; k<patchW; k++){
					int posX =  centerX + shiftW + k;
					int posY =  centerY + shiftH + j;
					
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
	
	private SimpleMatrix createJacobian (final SimpleMatrix params){
		SimpleMatrix jac = new SimpleMatrix(mModel.numPts*2, mCurrentParams.numRows());
		double j0,j1;
		
		SimpleMatrix meanShape = mModel.shapeModel.mMeanShape;
		SimpleMatrix eigenVectors = mModel.shapeModel.mEigenVectors;
		
		for (int i=0; i<mModel.numPts; i++){
			//1
			j0 = meanShape.get(i*2);
			j1 = meanShape.get(i*2+1);
			
			for (int j=0; j<eigenVectors.numCols(); j++){
				j0 += params.get(j+4)*eigenVectors.get(i*2, j);
				j1 += params.get(j+4)*eigenVectors.get(i*2+1, j);
			}
			jac.set(i*2, 0, j0);
			jac.set(i*2+1, 0, j1);
			
			//2
			j0 = meanShape.get(i*2+1);
			j1 = meanShape.get(i*2);	
			
			for (int j=0; j<eigenVectors.numCols(); j++){
				j0 += params.get(j+4)*eigenVectors.get(i*2+1, j);
				j1 += params.get(j+4)*eigenVectors.get(i*2, j);
			}
			jac.set(i*2, 1, -j0);
			jac.set(i*2+1, 1, j1);
			
			//3
			jac.set(i*2, 2, 1);
			jac.set(i*2+1, 2, 0);
			
			//4
			jac.set(i*2, 3, 0);
			jac.set(i*2+1, 3, 1);
			
			for (int j=0; j<eigenVectors.numCols(); j++){
				j0 = params.get(0)*eigenVectors.get(i*2, j) - params.get(1)*eigenVectors.get(i*2+1, j);
				j1 = params.get(0)*eigenVectors.get(i*2+1, j) + params.get(1)*eigenVectors.get(i*2, j);
				jac.set(i*2, 4+j, j0);
				jac.set(i*2+1, 4+j, j1);
			}
		}
		
		return jac;
	}
	
	//For test only
	private SimpleMatrix makeTestDate(SimpleMatrix params) {
		SimpleMatrix ret = new SimpleMatrix(mModel.numPts*2, 1);
		
		SimpleMatrix translate;
		SimpleMatrix sr;
		SimpleMatrix deviation = new SimpleMatrix(params.extractMatrix(4, mModel.numEVectors+4, 0, 1));
		
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
				    deviation.set(i, deviation.get(i) + values[i]);
				}
			}
		}
		else{
			
		}
				
		return sr.mult(mModel.shapeModel.mMeanShape.plus(mModel.shapeModel.mEigenVectors.mult(deviation))).plus(translate);
	}
	
	public void plot(Canvas canvas, Paint paint){		
		ShapeModel s = mModel.shapeModel;
		PathModel path = mModel.pathModel;
		

		if (test_PlotPatch){
			Paint pt = new Paint();
			byte[] patches = cropPatches();
			
			int offsetX = -(SEARCH_WIN_W+mModel.patchModel.sampleWidth-1)/2;
			int offsetY = -(SEARCH_WIN_H+mModel.patchModel.sampleHeight-1)/2;
			
			for (int i=0; i<mModel.numPts; i++){
				double centX = mCropPositions.get(i*2);
				double centY = mCropPositions.get(i*2+1);
				
				for (int j=0; j<SEARCH_WIN_H+mModel.patchModel.sampleHeight-1; j++){
					for (int k=0; k<(SEARCH_WIN_W+mModel.patchModel.sampleWidth-1); k++){
						int color = (patches[i*(SEARCH_WIN_W+mModel.patchModel.sampleWidth-1)*(SEARCH_WIN_H+mModel.patchModel.sampleHeight-1)+
						                    (SEARCH_WIN_W+mModel.patchModel.sampleWidth-1)*j+k])&0xFF;
						
						double left = (centX + k + offsetX)/mScaleFactor;
						double top = (centY + j + offsetY)/mScaleFactor;
						double right = (centX + k + offsetX + 1)/mScaleFactor;
						double bottom = (centY + j + offsetY + 1)/mScaleFactor;
								
						pt.setARGB(0xff, color, color, color);
						canvas.drawRect((float)left, (float)top, (float)right, (float)bottom, pt);
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
				double centX = mCropPositions.get(i*2);
				double centY = mCropPositions.get(i*2+1);
				
				for (int j=0; j<SEARCH_WIN_H; j++){
					for (int k=0; k<SEARCH_WIN_W; k++){
						int color = (int)((255*responses[i*SEARCH_WIN_W*SEARCH_WIN_H+j*SEARCH_WIN_W+k]));
						
						double left = (centX + k + offsetX)/mScaleFactor;
						double top = (centY + j + offsetY)/mScaleFactor;
						double right = (centX + k + offsetX + 1)/mScaleFactor;
						double bottom = (centY + j + offsetY + 1)/mScaleFactor;
						
						pt.setARGB(0xff, color, color, color);
						canvas.drawRect((float)left, (float)top, (float)right, (float)bottom, pt);
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
						double startX = mOriginalPositions.get(path.paths[i][j-1]*2);
						double startY = mOriginalPositions.get(path.paths[i][j-1]*2+1);
						double endX = mOriginalPositions.get(path.paths[i][j]*2);
						double endY = mOriginalPositions.get(path.paths[i][j]*2+1);
						canvas.drawLine((float)(startX/mScaleFactor), (float)(startY/mScaleFactor), (float)(endX/mScaleFactor), (float)(endY/mScaleFactor), pt);
					}				
				}
				
				pt.setColor(0xFF00FF00);
				
				for (int j=1; j<path.paths[i].length; j++){
					double startX = mCurrentPositions.get(path.paths[i][j-1]*2);
					double startY = mCurrentPositions.get(path.paths[i][j-1]*2+1);
					double endX = mCurrentPositions.get(path.paths[i][j]*2);
					double endY = mCurrentPositions.get(path.paths[i][j]*2+1);
					canvas.drawLine((float)(startX/mScaleFactor), (float)(startY/mScaleFactor), (float)(endX/mScaleFactor), (float)(endY/mScaleFactor), pt);
				}
				
				
			}
		}
		else if (test_PlotPts){
			Paint pt = new Paint();
			
			if (test_PlotOriginal){
				pt.setColor(0xFFFF0000);

				for (int i=0; i<mModel.numPts; i++){
					double centX = mOriginalPositions.get(i*2);
					double centY = mOriginalPositions.get(i*2+1);
					canvas.drawText(""+i, (float)(centX/mScaleFactor), (float)(centY/mScaleFactor), pt);
				}
			}
			
			pt.setColor(0xFF00FF00);

			for (int i=0; i<mModel.numPts; i++){
				double centX = mCurrentPositions.get(i*2);
				double centY = mCurrentPositions.get(i*2+1);
				canvas.drawText(""+i, (float)(centX/mScaleFactor), (float)(centY/mScaleFactor), pt);
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
				int value = (int)((mCurrentParams.get(i+4) * 50)/paramsLimit[i]) + 50;
				
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
	private double mScaleFactor;
	
	private FaceModel mModel;
	
	//Fit data / result
	private SimpleMatrix mOriginalPositions; //The positions for initial guess
	private SimpleMatrix mCropPositions;   //The positions for patch cropped
	private SimpleMatrix mCurrentPositions;  //The positions for current calculated
	private SimpleMatrix mCurrentParams;
		
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

