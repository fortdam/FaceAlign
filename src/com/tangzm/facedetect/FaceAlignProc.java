package com.tangzm.facedetect;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.zip.DataFormatException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;

import com.tangzm.facedetect.Filter2D.ResponseType;
import com.tangzm.facedetect.QMatrix.RepMode;

class NoFaceException extends Exception {
	public NoFaceException(){
		super("No face detected in image");
	}
}

class ModelUnloadedException extends Exception {
	public ModelUnloadedException() {
		super("Model data is not loaded");
	}
}

class MultipleSearchException extends Exception {
	public MultipleSearchException() {
		super("One instance does not support multiple search at same time");
	}
}

class NotInitializedException extends Exception {
	public NotInitializedException() {
		super("The initial-fitting is not performed before optimization");
	}
}

public class FaceAlignProc{
	
	public interface Callback {
		void finish(boolean status);
	}
	
	public interface CallbackWithEyePts {
		void finish (float[] eyePts);
	}
	
	public void init(Context ctx, int resId){
        QMatrix.init(ctx);

		mModel = new FaceModel(ctx, resId);
		
		mRS = RenderScript.create(ctx);
		
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
        
        mParamHist = new ArrayList<QMatrix>();
        mRunning = false;
	}
	

	
	private float[] makeInitialGuess(Bitmap bmp){
		FuncTracer.startFunc();
		
		float[] ret = new float[4];
		Face[] faces = new Face[1];
		int num = 0;
		int scaleFactor = (bmp.getWidth()<bmp.getHeight()?bmp.getWidth():bmp.getHeight())/INITIAL_FIT_MIN_DIM;
		int width = bmp.getWidth()/scaleFactor;
		int height = bmp.getHeight()/scaleFactor;
		Bitmap scaledBmp = bmp.createScaledBitmap(bmp, width, height, false);
		Bitmap processBmp;
		if (scaledBmp.getConfig() != Bitmap.Config.RGB_565) {
			processBmp = scaledBmp.copy(Bitmap.Config.RGB_565, false);			
		}
		else {
			processBmp = scaledBmp;
		}
		FaceDetector detector = new FaceDetector(width, height, 1);
		num = detector.findFaces(processBmp, faces);
		
		Face face = faces[0];
		
		PointF mid = new PointF();
		face.getMidPoint(mid);
		
		ret[0] = (mid.x - face.eyesDistance()/2) * scaleFactor;
		ret[1] = mid.y * scaleFactor;
		ret[2] = (mid.x + face.eyesDistance()/2) * scaleFactor;
		ret[3] = mid.y * scaleFactor;
		
		FuncTracer.endFunc();
		return ret;
	}
	
	
	private void doOptimize(Algorithm type) throws Exception{
		do {
			optimize(type);
		} while(false == checkConvergence(type));	
	}
	
	private void doSearchImage(Bitmap image, Algorithm type) throws Exception {
		float[] eyePositions = makeInitialGuess(image);
		
		if (null == eyePositions) {
			throw new NoFaceException();
		}
		
		initializeParameters(eyePositions[0], eyePositions[1], eyePositions[2], eyePositions[3]);
		setCurrentBitmap(image);
		doOptimize(type);
	}
	
	private void doSearchImage(Bitmap image, Algorithm type, float leftEyeX, float leftEyeY, float rightEyeX, float rightEyeY) throws Exception {
		initializeParameters(leftEyeX, leftEyeY, rightEyeX, rightEyeY);
		setCurrentBitmap(image);
		doOptimize(type);	
	}
	
	private void doOptimizeImage(Bitmap image, Algorithm type) throws Exception{
		FuncTracer.startFunc();
		if (null == mModel){
			throw new ModelUnloadedException();
		}
		
		clearFitHistory();
		adjustScaleFactor(); //use last result as current scale hint, it should be more proper than fixed scale factor
		setCurrentBitmap(image);
		doOptimize(type);
		FuncTracer.endFunc();
	}
	
	synchronized public float[] searchEyesInImage(final Bitmap image, final CallbackWithEyePts cb) throws Exception {
		if (mRunning) {
			throw new MultipleSearchException();
		}
		
		mRunning = true;
		
		if (null == cb){
			float[] eyePositions = makeInitialGuess(image);;
			mRunning = false;
			return eyePositions;
		}
		else {
			Handler handler = new Handler(new Handler.Callback() {
				@Override
				public boolean handleMessage(Message msg) {
					if (MESSAGE_ID_PROCESS_FINISH == msg.what){
						Bundle data = msg.getData();
						if (null == data){
							cb.finish(null);
						}
						else {
							cb.finish(data.getFloatArray("value"));
						}
						return true;
					}
					else {
						return false;
					}
				}
			});
			
			final Message msg = handler.obtainMessage(MESSAGE_ID_PROCESS_FINISH);
			msg.arg1 = 1;
			
			new Thread(new Runnable(){
				public void run() {
					try {
						float[] ret = makeInitialGuess(image);
						if (null != ret){
							Bundle data = new Bundle();
							data.putFloatArray("value", ret);
							msg.setData(data);
						}
						else {
							msg.setData(null);
						}
					}
					catch (Exception e){
						e.printStackTrace();
						msg.setData(null);
					}
					finally {
						mRunning = false;
						msg.sendToTarget();
					}
				}
			}).start();	
			return null;
		}
	}
	
	synchronized public void searchInImage(final Bitmap image, final Algorithm type, final Callback cb) throws Exception{
		FuncTracer.startFunc();		
		
		if (mRunning) {
			throw new MultipleSearchException();
		}
		
		mRunning = true;
		
		if (null == cb){
			doSearchImage(image, type);
			mRunning = false;
		}
		else {
			Handler handler = new Handler(new Handler.Callback() {
				@Override
				public boolean handleMessage(Message msg) {
					if (MESSAGE_ID_PROCESS_FINISH == msg.what) {
						// TODO Auto-generated method stub
						if (1 == msg.arg1){
							cb.finish(true);
						}
						else {
							cb.finish(false);
						}
						return true;
					}
					else {
						return false;
					}
				}
			});
			
			final Message msg = handler.obtainMessage(MESSAGE_ID_PROCESS_FINISH);
			msg.arg1 = 1;
			
			new Thread(new Runnable(){
				public void run() {
					try {
						doSearchImage(image, type);
					}
					catch (Exception e){
						e.printStackTrace();
						msg.arg1 = 0;
					}
					finally {
						mRunning = false;
						msg.sendToTarget();
					}
				}
			}).start();
		}
		
		FuncTracer.endFunc();
	}
	
	synchronized public void searchInImage(final Bitmap image, final Algorithm type, final Callback cb, final float leftEyeX, final float leftEyeY, final float rightEyeX, final float rightEyeY) throws Exception{
		FuncTracer.startFunc();		
		
		if (mRunning) {
			throw new MultipleSearchException();
		}
		
		mRunning = true;
		
		if (null == cb){
			doSearchImage(image, type, leftEyeX, leftEyeY, rightEyeX, rightEyeY);
			mRunning = false;
		}
		else {
			Handler handler = new Handler(new Handler.Callback() {
				@Override
				public boolean handleMessage(Message msg) {
					if (MESSAGE_ID_PROCESS_FINISH == msg.what){
						if (1 == msg.arg1){
							cb.finish(true);
						}
						else {
							cb.finish(false);
						}
						return true;
					}
					else {
						return false;
					}
				}
			});
			
			final Message msg = handler.obtainMessage(MESSAGE_ID_PROCESS_FINISH);
			msg.arg1 = 1;
			
			new Thread(new Runnable(){
				public void run() {
					try {
						doSearchImage(image, type, leftEyeX, leftEyeY, rightEyeX, rightEyeY);
					}
					catch (Exception e){
						e.printStackTrace();
						msg.arg1 = 0;
					}
					finally {
						mRunning = false;
						msg.sendToTarget();
					}
				}
			}).start();
		}
		
		FuncTracer.endFunc();
	}
	
	synchronized public void optimizeInImage(final Bitmap image, final Algorithm type, final Callback cb) throws Exception{
		FuncTracer.startFunc();		
				
		if (mRunning) {
			throw new MultipleSearchException();
		}
		
		mRunning = true;
		
		if (null == cb){
			doOptimizeImage(image, type);
			mRunning = false;
		}
		else {
			Handler handler = new Handler(new Handler.Callback() {
				@Override
				public boolean handleMessage(Message msg) {
					if (MESSAGE_ID_PROCESS_FINISH == msg.what){
						if (1 == msg.arg1){
							cb.finish(true);
						}
						else {
							cb.finish(false);
						}
						return true;
					}
					else {
						return false;
					}
				}
			});
			
			final Message msg = handler.obtainMessage(MESSAGE_ID_PROCESS_FINISH);
			msg.arg1 = 1;
			
			new Thread(new Runnable(){
				public void run() {
					try {
						doOptimizeImage(image, type);
					}
					catch (Exception e){
						e.printStackTrace();
						msg.arg1 = 0;
					}
					finally {
						mRunning = false;
						msg.sendToTarget();
					}
				}
			}).start();
		}
		
		FuncTracer.endFunc();
	}
	
	private void initializeParameters(float leftEyeX, float leftEyeY, float rightEyeX, float rightEyeY) throws Exception {
		if (null == mModel){
			throw new ModelUnloadedException();
		}
		
		mOriginalPositions = null;
		mCropPositions = null;
		mCurrentPositions = null;
		mCurrentParams = null;	
		mParamHist.clear();
		mOptCount = 0;
				
		int leftEyeIndex = mModel.pathModel.paths[mModel.pathModel.paths.length-2][0];
		int rightEyeIndex = mModel.pathModel.paths[mModel.pathModel.paths.length-1][0];
		
		double meanLeftX = mModel.shapeModel.mMeanShape.get(leftEyeIndex*2);
        double meanLeftY = mModel.shapeModel.mMeanShape.get(leftEyeIndex*2+1);
        double meanRightX = mModel.shapeModel.mMeanShape.get(rightEyeIndex*2);
        double meanRightY = mModel.shapeModel.mMeanShape.get(rightEyeIndex*2+1);
		
		double meanAngle = Math.atan((meanRightY-meanLeftY)/(meanRightX-meanLeftX));
		double currAngle = Math.atan((rightEyeY-leftEyeY)/(rightEyeX-leftEyeX));
		
		double diffAngle = currAngle - meanAngle;
		
        double meanDist = Math.hypot(meanRightX-meanLeftX, meanRightY-meanLeftY);
        double currDist = Math.hypot(rightEyeX-leftEyeX, rightEyeY-leftEyeY);
        
        double scale = meanDist/currDist;
        mScaleFactor = scale;
                
        leftEyeX *= scale;
        leftEyeY *= scale;
        rightEyeX *= scale;
        rightEyeY *= scale;
        
		mCurrentParams = new QMatrix(mModel.numEVectors+4, 1);
		
		mCurrentParams.set(0, (float)Math.cos(diffAngle)); //Alpha * cos(theta)
		mCurrentParams.set(1, (float)Math.sin(diffAngle)); //Alpha * sin(theta)
		mCurrentParams.set(2, (float)((rightEyeX+leftEyeX)/2-(meanRightX+meanLeftX)/2)); //translate X
		mCurrentParams.set(3, (float)((rightEyeY+leftEyeY)/2-(meanRightY+meanLeftY)/2));  //translate Y
        
        mCurrentPositions = getCurrentShape();
        mOriginalPositions = new QMatrix(mCurrentPositions, true);
        mParamHist.add(new QMatrix(mCurrentParams, true));
	}
	
	private void adjustScaleFactor() {
		//Always normalize the size of processed image, not let it too big or too small
		float adjustScale = (float)Math.sqrt(Math.pow(mCurrentParams.get(0),2) + Math.pow(mCurrentParams.get(1),2));
		Log.i(TAG, "adjustScale = "+adjustScale);
		
        if (adjustScale >= 2.5 || adjustScale <=0.4) {
			mScaleFactor /= adjustScale;
			mCurrentParams.set(0, mCurrentParams.get(0)/adjustScale);
			mCurrentParams.set(1, mCurrentParams.get(1)/adjustScale);
			mCurrentParams.set(2, mCurrentParams.get(2)/adjustScale);
			mCurrentParams.set(3, mCurrentParams.get(3)/adjustScale);
        }
	}
	
	private void setCurrentBitmap(Bitmap image) throws Exception
	{	
        Bitmap imgProcess = Bitmap.createScaledBitmap(image, (int)(image.getWidth()*mScaleFactor), (int)(image.getHeight()*mScaleFactor), true);

        mImageW = imgProcess.getWidth();
        mImageH = imgProcess.getHeight();
        
        //Create the float image
        ;
        
        if (imgProcess.getConfig() == Bitmap.Config.ARGB_8888) {
	        ScriptC_im2float script = new ScriptC_im2float(mRS);
	        Allocation inAlloc = Allocation.createFromBitmap(mRS, imgProcess);
	        Type.Builder tb = new Type.Builder(mRS, Element.U8(mRS));
	        tb.setX(mImageW).setY(mImageH);
	        mImgGrayScaled = new byte[mImageW*mImageH];
	        Allocation outAlloc = Allocation.createTyped(mRS, tb.create());
	        script.forEach_root(inAlloc, outAlloc);
	        outAlloc.copyTo(mImgGrayScaled);		
        }
        else if (imgProcess.getConfig() == Bitmap.Config.RGB_565) {
	        ScriptC_im2float_565 script = new ScriptC_im2float_565(mRS);
	        Type.Builder tbIn = new Type.Builder(mRS, Element.U8_2(mRS));
	        Type.Builder tbOut = new Type.Builder(mRS, Element.U8(mRS));
	        tbIn.setX(mImageW).setY(mImageH);
	        tbOut.setX(mImageW).setY(mImageH);
	        byte[] mOriginalBytes = new byte[mImageW*mImageH*2];
	        mImgGrayScaled = new byte[mImageW*mImageH];
	        Allocation inAlloc = Allocation.createTyped(mRS, tbIn.create());
	        Allocation outAlloc = Allocation.createTyped(mRS, tbOut.create());
	        ByteBuffer buffer = ByteBuffer.wrap(mOriginalBytes);
	        imgProcess.copyPixelsToBuffer(buffer);
	        inAlloc.copyFrom(mOriginalBytes);
	        script.forEach_root(inAlloc, outAlloc);
	        outAlloc.copyTo(mImgGrayScaled);
        }
        else {
	        throw new DataFormatException("Only support Bitmap in ARGB_8880");     	
        }
        

	}
	
	private void clearFitHistory() {
		mParamHist.clear();
		mOptCount = 0;
		
        mParamHist.add(new QMatrix(mCurrentParams, true));
	}
	
	
	private QMatrix getScaleRotateM(final QMatrix params){
		return getScaleRotateM(params, 1, 0);
	}
	
	private QMatrix getScaleRotateM(final double scale, final double theta){
		return getScaleRotateM(null, scale, theta);
	}

	private QMatrix getScaleRotateM(final QMatrix params, final double scale, final double theta){		
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
		
        float v1 = (float)(scaleFactor * Math.cos(rotateAngle));
        float v2 = (float)(scaleFactor * Math.sin(rotateAngle));
        
		QMatrix result = new QMatrix(2, 2);

		result.set(0, 0, v1);
		result.set(1, 1, v1);	
		result.set(0, 1, -v2);
		result.set(1, 0, v2);
		
		return result;
	}
	
	private QMatrix getTranslateM(final QMatrix params){
		return getTranslateM(params, 0, 0);
	}
	
	private QMatrix getTranslateM(final double offsetX, final double offsetY){
		return getTranslateM(null, offsetX, offsetY);
	}
	
	private QMatrix getTranslateM(final QMatrix params, final double offsetX, final double offsetY){
		double x =  offsetX;
		double y =  offsetY;
		
		if (params != null){
			x += params.get(2,0);
			y += params.get(3, 0);
		}
		
		QMatrix result = new QMatrix(2, 1);
		
		result.set(0, (float)x);
		result.set(1, (float)y);
		
		return result;
	}
	
	
	private QMatrix getShape(final QMatrix params){
		FuncTracer.startFunc();
		
		QMatrix translate = getTranslateM(params);
		QMatrix sr = getScaleRotateM(params);
		QMatrix deviation = params.rows(4, mModel.numEVectors+4);
		
		QMatrix evec = mModel.shapeModel.mEigenVectors;
		QMatrix mean = mModel.shapeModel.mMeanShape;
		
		QMatrix result = evec.mult(deviation).plusSelf(mean).multRepSelf(sr).plusRepSelf(translate, RepMode.VERTICAL_ONLY);
		
		FuncTracer.endFunc();
		
		return result;

	}

	
	private QMatrix getCurrentShape(){
		return getShape(mCurrentParams);
	}
	
	private QMatrix regularizeParams(QMatrix params){	
		for (int i=0; i<mModel.numEVectors; i++){
			float value = params.get(i+4, 0);
			float constrain = mModel.shapeModel.mEigenConstraints.get(i, 0);
			
			if (value > constrain){
				params.set(i+4, 0, constrain);
			}
			else if (value < -constrain){
				params.set(i+4, 0, -constrain);
			}
		}
		return params;
	}
	
	private QMatrix createJacobian (final QMatrix params){
		FuncTracer.startFunc();
		
		QMatrix jac = new QMatrix(mModel.numPts*2, mCurrentParams.numRows());
		float j0,j1;
		
		QMatrix meanShape = mModel.shapeModel.mMeanShape;
		QMatrix eigenVectors = mModel.shapeModel.mEigenVectors;
	
		
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
		
		FuncTracer.endFunc();
		return jac;
	}
	
	private QMatrix createInvCovariance(QMatrix meanPos, float[] responseImg){
		QMatrix result = new QMatrix(mModel.numPts*2, mModel.numPts*2);
		
		QMatrix currPtsConv = new QMatrix(2,2);
		float[] zeros = new float[]{0,0,0,0};
		
		for (int i=0; i<mModel.numPts; i++) {
			float meanX = meanPos.get(i*2) - mCropPositions.get(i*2) + (SEARCH_WIN_W - 1);
			float meanY = meanPos.get(i*2+1) - mCropPositions.get(i*2+1) + (SEARCH_WIN_H -1);
			
			currPtsConv.set(0, zeros);
			int respImgOffset = i*SEARCH_WIN_W*SEARCH_WIN_H;
			
			for (int j=0; j<SEARCH_WIN_W; j++) {
				for (int k=0; k<SEARCH_WIN_H; k++) {
					float a = responseImg[respImgOffset+k*SEARCH_WIN_H+j];
					float dx = j-meanX;
					float dy = k-meanY;
									
					currPtsConv.set(0, currPtsConv.get(0)+a*dx*dx);
					currPtsConv.set(1, currPtsConv.get(1)+a*dx*dy);
					currPtsConv.set(3, currPtsConv.get(3)+a*dy*dy);
				}
			}
			
			currPtsConv.set(2, currPtsConv.get(1));

			QMatrix invConvPts = currPtsConv.invert();
			
			if (Float.isNaN(invConvPts.get(0)) ||
					Float.isNaN(invConvPts.get(1)) ||
					Float.isNaN(invConvPts.get(2)) ||
					Float.isNaN(invConvPts.get(3))) {
				//in extreme case, we just ignore this point
				invConvPts.set(0, zeros); 
			}
				
			
			result.set(i*2, i*2, invConvPts.get(0));
			result.set(i*2+1, i*2, invConvPts.get(1));
			result.set(i*2, i*2+1, invConvPts.get(2));
			result.set(i*2+1,  i*2+1, invConvPts.get(3));
		}
		
		return result;
	}
	
	private void updateCurrent(QMatrix newPositions){
		FuncTracer.startFunc();
		
		QMatrix jacob = createJacobian(mCurrentParams);
		QMatrix transJacob = jacob.transpose();
				
		QMatrix deltaParams = transJacob.mult(jacob).invert().mult(transJacob).mult(newPositions.minusSelf(mCurrentPositions));
       
		Log.i(TAG, "The deltaParam sum = " + deltaParams.innerProduct());

		mCurrentParams = regularizeParams(deltaParams.plusSelf(mCurrentParams));
		mCurrentPositions = getShape(mCurrentParams);
			
		FuncTracer.endFunc();
	}
	
	private void updateCurrentCQF(QMatrix newPositions, float[] responseImg) {
		FuncTracer.startFunc();
		QMatrix jacob = createJacobian(mCurrentParams);
		QMatrix transJacob = jacob.transpose();
		QMatrix invCovariance = createInvCovariance(newPositions, responseImg);
		newPositions.printOut();
		invCovariance.printOut();
		QMatrix deltaParams = transJacob.mult(invCovariance).mult(jacob).invert().mult(transJacob).mult(invCovariance).mult(newPositions.minusSelf(mCurrentPositions));
		
		mCurrentParams = regularizeParams(deltaParams.plusSelf(mCurrentParams));
		mCurrentPositions = getShape(mCurrentParams);		
		FuncTracer.endFunc();
	}
	
	private QMatrix doMeanShift(final float[] responseImg, final QMatrix currPositions, final int variance){
		FuncTracer.startFunc();
		
		QMatrix newPositions = new QMatrix(mModel.numPts*2, 1);
		
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

			newPositions.set(i*2, (float)(numeratorX/denominator));
			newPositions.set(i*2+1, (float)(numeratorY/denominator));
		}
		
		FuncTracer.endFunc();
		return newPositions;
	}
	
	private void searchMeanShift(float[] responseImg){
		FuncTracer.startFunc();
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 10));
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 5));
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 1));
		FuncTracer.endFunc(); 
	}
	
	private QMatrix doChooseMean(final float[] responseImg){
		FuncTracer.startFunc();
		
		QMatrix newPositions = new QMatrix(mModel.numPts*2, 1);
				
		for (int i=0; i<mModel.numPts; i++){
			double respValue = 0;
			double meanX = 0;
			double meanY = 0;
			int respImgOffset = i*SEARCH_WIN_W*SEARCH_WIN_H;
			
			for (int j=0; j<SEARCH_WIN_W; j++){
				for (int k=0; k<SEARCH_WIN_H; k++){
					meanX += responseImg[respImgOffset+k*SEARCH_WIN_W+j]*j;
				    meanY += responseImg[respImgOffset+k*SEARCH_WIN_W+j]*k;
				}
			}

			newPositions.set(i*2, 0, (float)(mCropPositions.get(i*2) - (SEARCH_WIN_W-1)/2 + meanX));
			newPositions.set(i*2+1, 0, (float)(mCropPositions.get(i*2+1) - (SEARCH_WIN_H-1)/2 + meanY));
		}
		FuncTracer.endFunc();
		return newPositions;
	}
	
	private void searchConvQuadFit(float[] responseImg) {
		FuncTracer.startFunc();
		
		QMatrix meanPositions = doChooseMean(responseImg);
				
		updateCurrentCQF(meanPositions, responseImg);
		
		FuncTracer.endFunc();
	}
	
	private QMatrix doChoosePeak(final float[] responseImg){
		FuncTracer.startFunc();
		
		QMatrix newPositions = new QMatrix(mModel.numPts*2, 1);
				
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

			newPositions.set(i*2, 0, (float)(mCropPositions.get(i*2) - (SEARCH_WIN_W-1)/2 + peakX));
			newPositions.set(i*2+1, 0, (float)(mCropPositions.get(i*2+1) - (SEARCH_WIN_H-1)/2 + peakY));
		}
		FuncTracer.endFunc();
		return newPositions;
	}
	
	private void searchPeak(float[] responseImg){
		FuncTracer.startFunc();
		
		updateCurrent(doChoosePeak(responseImg));
		
		FuncTracer.endFunc();
	}
	
	private boolean checkConvergence(Algorithm type) {
		
		if ((Algorithm.ASM_QUICK==type && mOptCount>=ASM_QUICK_OPTIMIZATION_LIMIT) || 
				(Algorithm.CQF_QUICK==type && mOptCount>=CQF_QUICK_OPTIMIZATION_LIMIT) ||
				(Algorithm.KDE_QUICK==type && mOptCount>=KDE_QUICK_OPTIMIZATION_LIMIT)){
			return true;
		}
	    else if (mOptCount >= OPTIMIZATION_LIMIT) {
			return true;
		}
		
		if (mParamHist.size() >= 3) {
			QMatrix currentParam = mParamHist.get(mParamHist.size()-1);
			QMatrix lastParam = mParamHist.get(mParamHist.size()-2);
			QMatrix lastlastParam = mParamHist.get(mParamHist.size()-3);
			
			float currDiff = currentParam.minus(lastParam).innerProduct();
			float lastDiff = currentParam.minus(lastlastParam).innerProduct();
			
			Log.v(TAG, "optimize: currentDiff="+currDiff+" lastDiff="+lastDiff);
			
			if (currDiff > lastDiff || currDiff < CONVERGENCE_THRESHOLD) {
				return true;
			}
		}
		
		return false;
	}
	
	private void optimize(Algorithm type) throws Exception{
		FuncTracer.startFunc();
		Log.i(TAG, "test opt");
		
		//Get Filter response
		mFilter.setPatches(cropPatches());
		
		switch(type){	
		case CQF:
		case CQF_QUICK:
			mFilter.process(ResponseType.REG_NORMALIZED);
			searchConvQuadFit(mFilter.gerResponseImages());
			break;
			
		case KDE:
		case KDE_QUICK:
			mFilter.process(ResponseType.REGULARIZED);
			searchMeanShift(mFilter.gerResponseImages());
			break;
			
		case ASM:
		case ASM_QUICK:
		default:
			mFilter.process(ResponseType.RAW);
			searchPeak(mFilter.gerResponseImages());
			break;
		}
		
		mParamHist.add(new QMatrix(mCurrentParams, true));
		while (mParamHist.size() > PARAM_HIST_SIZE) {
			mParamHist.remove(0);
		}
		
		mOptCount++;
		FuncTracer.endFunc();
	}
	
	private byte[] cropPatches(){
		FuncTracer.startFunc();
		
		QMatrix currShape = getCurrentShape();
		
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
					
					if (posX<0 || posY<0 || posX>=mImageW || posY>=mImageH){ //out of image
						ret[i*patchW*patchH+j*patchW+k] = 0; //set as black 
						//Log.i(TAG, "index="+(patchW*patchH+j*patchW+k)+"val="+0);
					}
					else {						
						ret[i*patchW*patchH+j*patchW+k] = (mImgGrayScaled[(int)(posY*mImageW) + posX]);
						//Log.i(TAG, "index="+(i*patchW*patchH+j*patchW+k)+"val="+(mImgGrayScaled[(int)(posY*mImageW) + posX]));

					}
				}
			}
		}
		
		FuncTracer.endFunc();
		return ret;
	}
	
	public boolean isRunning() {
		return mRunning;
	}
		
	public void drawTestInfo(Canvas canvas, float scale, float translateX, float translateY){		
		FuncTracer.startFunc();
		ShapeModel s = mModel.shapeModel;
		PathModel path = mModel.pathModel;
		
		Log.v(TAG, "DrawTestInfo: mScaleFactor="+mScaleFactor+" scale="+scale+" translateX="+translateX+ " translateY="+translateY); 
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
								
						left = left*scale+translateX;
						top = top*scale+translateY;
						right = right*scale+translateX;
						bottom = bottom*scale+translateY;
						
						pt.setARGB(0xFF, color, color, color);
						
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
			
			for (int i=27; i<28; i++){
				double centX = mCropPositions.get(i*2);
				double centY = mCropPositions.get(i*2+1);
				
				for (int j=0; j<SEARCH_WIN_H; j++){
					for (int k=0; k<SEARCH_WIN_W; k++){
						int color = (int)((255*responses[i*SEARCH_WIN_W*SEARCH_WIN_H+j*SEARCH_WIN_W+k]));
						
						double left = (centX + k + offsetX)/mScaleFactor;
						double top = (centY + j + offsetY)/mScaleFactor;
						double right = (centX + k + offsetX + 1)/mScaleFactor;
						double bottom = (centY + j + offsetY + 1)/mScaleFactor;
												
						left = left*scale+translateX;
						top = top*scale+translateY;
						right = right*scale+translateX;
						bottom = bottom*scale+translateY;
						
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
					
					if (1 == path.paths[i].length){
						double centX = mCurrentPositions.get(path.paths[i][0]*2) / mScaleFactor;
						double centY = mCurrentPositions.get(path.paths[i][0]*2+1) / mScaleFactor;
						centX = centX*scale+translateX;
						centY = centY*scale+translateY;
						canvas.drawCircle((float)centX, (float)centY, (float)test_circle_radius, pt);
					}
					else {
						for (int j=1; j<path.paths[i].length; j++){
							double startX = mOriginalPositions.get(path.paths[i][j-1]*2) / mScaleFactor;
							double startY = mOriginalPositions.get(path.paths[i][j-1]*2+1) / mScaleFactor;
							double endX = mOriginalPositions.get(path.paths[i][j]*2) / mScaleFactor;
							double endY = mOriginalPositions.get(path.paths[i][j]*2+1) / mScaleFactor;
							
							startX = startX*scale+translateX;
							endX = endX*scale+translateX;
							startY = startY*scale+translateY;
							endY = endY*scale+translateY;
							
							
							canvas.drawLine((float)startX, (float)startY, (float)endX, (float)endY, pt);
						}		
					}
				}
				
				pt.setColor(0xFF00FF00);
				
				if (1 == path.paths[i].length){
					double centX = mCurrentPositions.get(path.paths[i][0]*2) / mScaleFactor;
					double centY = mCurrentPositions.get(path.paths[i][0]*2+1) / mScaleFactor;
					centX = centX*scale+translateX;
					centY = centY*scale+translateY;
					canvas.drawCircle((float)centX, (float)centY, (float)test_circle_radius, pt);
				}
				else {
					for (int j=1; j<path.paths[i].length; j++){
						double startX = mCurrentPositions.get(path.paths[i][j-1]*2) / mScaleFactor;
						double startY = mCurrentPositions.get(path.paths[i][j-1]*2+1) / mScaleFactor;
						double endX = mCurrentPositions.get(path.paths[i][j]*2) / mScaleFactor;
						double endY = mCurrentPositions.get(path.paths[i][j]*2+1) / mScaleFactor;
										
						startX = startX*scale+translateX;
						endX = endX*scale+translateX;
						startY = startY*scale+translateY;
						endY = endY*scale+translateY;
						
						canvas.drawLine((float)startX, (float)startY, (float)endX, (float)endY, pt);
					
						Log.v(TAG, "Print contour:  ("+ startX + "," + startY + ") to (" +endX+","+endY+")" );
					}
				}
			}
		}
		else if (test_PlotPts){
			Paint pt = new Paint();
			
			if (test_PlotOriginal){
				pt.setColor(0xFFFF0000);

				for (int i=0; i<mModel.numPts; i++){
					double centX = mOriginalPositions.get(i*2) / mScaleFactor;
					double centY = mOriginalPositions.get(i*2+1) / mScaleFactor;
					
					centX = centX*scale+translateX;
					centY = centY*scale+translateY;
					
					canvas.drawText(""+i, (float)centX, (float)centY, pt);
				}
			}
			
			pt.setColor(0xFF00FF00);

			for (int i=0; i<mModel.numPts; i++){
				double centX = mCurrentPositions.get(i*2) / mScaleFactor;
				double centY = mCurrentPositions.get(i*2+1) / mScaleFactor;
				
				centX = centX*scale+translateX;
				centY = centY*scale+translateY;
				
				canvas.drawText(""+i, (float)centX, (float)centY, pt);
			}
			

		}
		
		if (test_PlotParams){
			//Check spca modeling at http://auduno.github.io/clmtrackr/examples/modelviewer_spca.html 
			
			Paint pt = new Paint();
			pt.setColor(0xFF00FF00);
			pt.setTextSize(30);
			String text = new String();
			
			for (int i=0; i<mModel.shapeModel.mEigenConstraints.length(); i++){
				int value = (int)((mCurrentParams.get(i+4) * 50)/mModel.shapeModel.mEigenConstraints.get(i)) + 50;
				
				canvas.drawText(Parameter.fromInteger(i).desciption()+ ": " + value + "/100", 0, (i+1)*(30+5), pt);		
			}
		}
		
		FuncTracer.endFunc();
	}
	
	public int getFitParameter(Parameter param) {		
		return (int)((mCurrentParams.get(param.mValue+4) * 50)/mModel.shapeModel.mEigenConstraints.get(param.mValue)) + 50;
	}
	
	public float[] getFitShape(Organ organ) {
		float[] result = new float[2*mModel.pathModel.paths[organ.mValue].length];
		
		for (int i=0; i<result.length/2; i++) {
			result[i*2] = (float)(mCurrentPositions.get(mModel.pathModel.paths[organ.mValue][i] * 2) / mScaleFactor);
			result[i*2+1] = (float)(mCurrentPositions.get(mModel.pathModel.paths[organ.mValue][i] * 2 + 1) / mScaleFactor);
		};
		
		return result;
	}
	
	public enum Parameter {
		POSE_LEFT_RIGHT(0),
		POSE_DOWN_UP(1),
		EXPRESSION_SURPRISE_BORING(2),
		MOUTH_OPEN_CLOSE(3),
		FACE_FAT_THIN(4),
		MOUTH_SMALL_SHEEPISH(5),
		MOUTH_CLOSE_OPEN(6),
		POSE_RIGHT_LEFT_EXT(7),
		POSE_UP_DOWN_EXT(8),
		EYE_SMALL_BIG(9),
		EYE_UP_RIGHT_LEFT(10),
		EYE_LEFT_RIGHT(11),
		EYE_BROW_LONG_SHORT(12),
		EYE_UP_LEFT_RIGHT_EXT(13),
		EXP_SAD_SMILE(14),
		FACE_THIN_FAT_EXT(15),
		POSE_LEFT_RIGHT_EXT(16),
		EYE_BROW_CENTER_BIG_SMALL(17),
		FACE_CONTOUR_HIGHT_LOW(18),
		NOSE_LEFT_RIGHT(19);
		
		private final int mValue;
		
		private Parameter(int value) {
			mValue = value;
		}
		
		public static Parameter fromInteger(int value){
			switch(value){
			case 0:
				return POSE_LEFT_RIGHT;
			case 1:
				return POSE_DOWN_UP;
			case 2:
				return EXPRESSION_SURPRISE_BORING;
			case 3:
				return MOUTH_OPEN_CLOSE;
			case 4:
				return FACE_FAT_THIN;
			case 5:
				return MOUTH_SMALL_SHEEPISH;
			case 6:
				return MOUTH_CLOSE_OPEN;
			case 7:
				return POSE_RIGHT_LEFT_EXT;
			case 8:
				return POSE_UP_DOWN_EXT;
			case 9:
				return EYE_SMALL_BIG;
			case 10:
				return EYE_UP_RIGHT_LEFT;
			case 11:
				return EYE_LEFT_RIGHT;
			case 12:
				return EYE_BROW_LONG_SHORT;
			case 13:
				return EYE_UP_LEFT_RIGHT_EXT;
			case 14:
				return EXP_SAD_SMILE;
			case 15:
				return FACE_THIN_FAT_EXT;
			case 16:
				return POSE_LEFT_RIGHT_EXT;
			case 17:
				return EYE_BROW_CENTER_BIG_SMALL;
			case 18:
				return FACE_CONTOUR_HIGHT_LOW;
			case 19:
				return NOSE_LEFT_RIGHT;
			default:
				return null;
			}
		}
		
		public static int maxInteger(){
			return 19;
		}
		
		public String desciption() {
			return this.toString().replaceAll("_", " ").toLowerCase();
		}
	};
	
	public enum Organ {
		FACE(0),
		RIGHT_EYEBROW(1),
		LEFT_EYEBROW(2),
		RIGHT_EYE(3),
		LEFT_EYE(4),
		NOSE_FLOOR(5),
		NOSE_EDGE(6),
		LIPS(7),
		LEFT_EYEBALL(8),
		RIGHT_EYEBALL(9);
		
		private final int mValue;
		
		private Organ(int value) {
			mValue = value;
		}		
		
		public static Organ fromInteger(int value){
			switch(value) {
			case 0:
				return FACE;
			case 1:
				return RIGHT_EYEBROW;
			case 2:
				return LEFT_EYEBROW;
			case 3:
				return RIGHT_EYE;
			case 4:
				return LEFT_EYE;
			case 5:
				return NOSE_FLOOR;
			case 6:
				return NOSE_EDGE;
			case 7:
				return LIPS;
			case 8:
				return LEFT_EYEBALL;
			case 9:
				return RIGHT_EYEBALL;
			default:
				return null;
			}
		}
		
		
		public static int maxInteger(){
			return 9;
		}
		
		public String desciption() {
			return this.toString().replaceAll("_", " ").toLowerCase();
		}
	};
	
	public enum Algorithm {
		DEFAULT,
		ASM, //Active Shape Model.
		ASM_QUICK, //A simple version of ASM, it only runs $(ASM_QUICK_OPTIMIZATION_LIMIT) time of ASM optimization.
		CQF, //Convex Quadratic Function. Actually we use gaussian distribution to similarize quadratic function
		CQF_QUICK, //A simple version of CQF, it only runs $(CQF_QUICK_OPTIMIZATION_LIMIT) limit of CQF optimization.
		KDE, //Kernel Density Estimation. Also called "mean-shift"
		KDE_QUICK, //A simple version of KDE, it only runs $(KDE_QUICK_OPTIMIZATION_LIMIT) limit of KDE optimization.
	};
	
	private static final String TAG = "FaceAlignProc";


	private static final int INITIAL_FIT_MIN_DIM = 100;
	
	private static final int MESSAGE_ID_PROCESS_FINISH = 12375; //a magic number

	private static final int SEARCH_WIN_W = 11;
	private static final int SEARCH_WIN_H = 11;
	
	private static final int PARAM_HIST_SIZE = 3;
	
	private static final float CONVERGENCE_THRESHOLD = 1.5f;
	
	private static final int OPTIMIZATION_LIMIT = 15;
	private static final int ASM_QUICK_OPTIMIZATION_LIMIT = 1;
	private static final int CQF_QUICK_OPTIMIZATION_LIMIT = 1;
	private static final int KDE_QUICK_OPTIMIZATION_LIMIT = 1;
	
	private RenderScript mRS;
	
	//Image Data
	private int mImageW;
	private int mImageH;
	private byte[] mImgGrayScaled;
	private double mScaleFactor;
	
	private FaceModel mModel;
	
	//For multi-threading
	private boolean mRunning;
	
	//Fit data / result	
	private QMatrix mOriginalPositions; //The positions for initial guess
	private QMatrix mCropPositions;   //The positions for patch cropped
	private QMatrix mCurrentPositions;  //The positions for current calculated
	private QMatrix mCurrentParams;	
	
	private ArrayList<QMatrix> mParamHist;
	
	private int mOptCount;
	
	private Filter2D mFilter;
	
	//For test 
	private boolean test_PlotContour = true;
	private boolean test_PlotPts = false;
	
	private boolean test_PlotOriginal = false;
	
	private boolean test_PlotParams = false;
	
	private boolean test_PlotPatch = false;
	private boolean test_PlotResponse = false;
	
	static final int test_circle_radius = 6;
	//For test
}

