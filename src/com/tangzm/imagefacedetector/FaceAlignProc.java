package com.tangzm.imagefacedetector;

import com.tangzm.imagefacedetector.QMatrix.RepMode;

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
        
        QMatrix.init(ctx);
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
		Bitmap processBmp = scaledBmp.copy(Bitmap.Config.RGB_565, false);
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
	
	public void searchInImage(Context ctx, Bitmap image) throws Exception{
		FuncTracer.startFunc();
		
		float[] eyePositions = makeInitialGuess(image);
		
		if (null == eyePositions){
			throw new Exception("Can not find face");
		}
		
		searchInImage(ctx, image, eyePositions[0], eyePositions[1], eyePositions[2], eyePositions[3]);
		
		FuncTracer.endFunc();
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
        
		mCurrentParams = new QMatrix(mModel.numEVectors+4, 1);
		
		mCurrentParams.set(0, (float)Math.cos(diffAngle)); //Alpha * cos(theta)
		mCurrentParams.set(1, (float)Math.sin(diffAngle)); //Alpha * sin(theta)
		mCurrentParams.set(2, (float)((rightX+leftX)/2-(meanRightX+meanLeftX)/2)); //translate X
		mCurrentParams.set(3, (float)((rightY+leftY)/2-(meanRightY+meanLeftY)/2));  //translate Y
				
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
        mOriginalPositions = new QMatrix(mCurrentPositions, true);       
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
		FuncTracer.startFunc();
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
		FuncTracer.endFunc();
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
	
	private void updateCurrent(QMatrix newPositions){
		FuncTracer.startFunc();

		QMatrix jacob = createJacobian(mCurrentParams);
		QMatrix transJacob = jacob.transpose();
		
		QMatrix deltaParams = transJacob.mult(jacob).invert().mult(transJacob).mult(newPositions.minusSelf(mCurrentPositions));
		
		mCurrentParams = regularizeParams(deltaParams.plusSelf(mCurrentParams));
		mCurrentPositions = getShape(mCurrentParams);
	
		//mCurrentPositions.verify(mCurrentPositions);
		
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
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 20));
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 10));
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 5));
		updateCurrent(doMeanShift(responseImg, mCurrentPositions, 1));
		
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
	
	public void optimize(Algorithm type) throws Exception{
		FuncTracer.startFunc();
		
		//Get Filter response
		mFilter.setPatches(cropPatches());
		
		//Optimize algorithm
		if (Algorithm.ASM == type){
			mFilter.process(false);
			searchPeak(mFilter.gerResponseImages());
		}
		else if (Algorithm.KDE == type){
			mFilter.process(true);
			searchMeanShift(mFilter.gerResponseImages());
		}
		else {
			throw new Exception("Unsupported algorithm");
		}
		
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
					
					if (posX<0 || posY<0 || posX>mImageW || posY>mImageH){ //out of image
						ret[i*patchW*patchH+j*patchW+k] = 0; //set as black
					}
					else {						
						ret[i*patchW*patchH+j*patchW+k] = (mImgGrayScaled[(int)(posY*mImageW) + posX]);
					}
				}
			}
		}
		
		FuncTracer.endFunc();
		return ret;
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
			
			Paint pt = new Paint();
			pt.setColor(0xFF00FF00);
			pt.setTextSize(30);
			String text = new String();
			
			String[] paramLabels = {
			/*1*/	"left/right: ",
			/*2*/	"up/down: ",
			/*3*/	"laugth/angry: ",
			/*4*/	"yelling/silient: ",
			/*5*/	"oval/diamond face: ",
			/*6*/	"narrow/wide mouth: ",
			/*7*/	"close/open mouth: ",
			/*8*/	"skew to right/left: ",
			/*9*/	"wide/narrow face: ",
			/*10*/	"small/big eyes: ",
			/*11*/	"right/left eye up: ",
			/*12*/	"oval/diamond face??: ",
			/*13*/	"long/short eyebrow: ",
			/*14*/	"xxx: ",
			/*15*/	"feel pity/happy: ",
			/*16*/	"narrow/wide chin: ",
			/*17*/	"chin to left/right: ",
			/*18*/	"big/small brow center: ",
			/*19*/	"high/low face contour: ",
			/*20*/	"nose to left/right against mouth: "
			};
			
			for (int i=0; i<mModel.shapeModel.mEigenConstraints.length(); i++){
				int value = (int)((mCurrentParams.get(i+4) * 50)/mModel.shapeModel.mEigenConstraints.get(i)) + 50;
				
				canvas.drawText(paramLabels[i] + value + "/100", 0, (i+1)*(30+5), pt);		
			}
		}
	}
	
	public enum Algorithm {
		ASM,
		CQF, //Not implemented
		KDE 
	};
	
	private static final String TAG = "FaceAlignProc";

	private static final int INITIAL_FIT_MIN_DIM = 100;

	private static final int SEARCH_WIN_W = 11;
	private static final int SEARCH_WIN_H = 11;
	
	//Image Data
	private int mImageW;
	private int mImageH;
	private byte[] mImgGrayScaled;
	private double mScaleFactor;
	
	private FaceModel mModel;
	
	//Fit data / result	
	private QMatrix mOriginalPositions; //The positions for initial guess
	private QMatrix mCropPositions;   //The positions for patch cropped
	private QMatrix mCurrentPositions;  //The positions for current calculated
	private QMatrix mCurrentParams;	
	
	private Filter2D mFilter;
	
	//For test / plotting
	private boolean test_PlotContour = true;
	private boolean test_PlotPts = false;
	
	private boolean test_PlotOriginal = true;
	
	private boolean test_PlotParams = true;
	
	private boolean test_PlotPatch = false;
	private boolean test_PlotResponse = false;
	//For test / plotting
}

