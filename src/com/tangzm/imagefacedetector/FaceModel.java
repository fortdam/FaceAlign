package com.tangzm.imagefacedetector;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import android.content.Context;

interface FaceModelCallback {
	void modeLoaded();
}

public class FaceModel {
	
	public ShapeModel shapeModel;
	public PatchModel patchModel;
	public PathModel  pathModel;
	
	public int numEVectors;
	public int numPts;
	
	
	public FaceModel(Context context, int resId) {
		
		StringBuilder builder = new StringBuilder();
		InputStream is = context.getResources().openRawResource(resId);
		char[] buffer = new char[1024];
		
		try {
		    Reader reader = new BufferedReader(new InputStreamReader(is, "US-ASCII"));
		    int n;
		    while ((n = reader.read(buffer)) != -1) {
		        builder.append(buffer);
		    }
		} 
		catch(Exception e){
			e.printStackTrace();
		}
		finally {
			try{
		    	is.close();
		    	rawContent = new JSONObject(builder.toString());
		    }
			catch(Exception e){
				e.printStackTrace();
			}
		}
		
		try{
			 shapeModel = new ShapeModel(rawContent.getJSONObject(SHAPE_LABEL));
			 numEVectors = shapeModel.eigenValues.length;
			 numPts = shapeModel.meanShape.length/2;
			 
			 pathModel = new PathModel(rawContent.getJSONObject(PATH_LABEL));
			 patchModel = new PatchModel(rawContent.getJSONObject(PATCH_LABEL), numPts);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private static final String SHAPE_LABEL = "shapeModel";
	private static final String PATCH_LABEL = "patchModel";
	private static final String PATH_LABEL = "path";
	
	private JSONObject rawContent = null;
}

class PathModel {
    public int[][] paths;	
	
	public PathModel (JSONObject raw){
		try{
			JSONArray pathArr = raw.getJSONArray(NORM_LABEL);
			int len = pathArr.length();
			
			paths = new int[len][];
			
			for(int i=0; i<len; i++){
				JSONArray path = pathArr.optJSONArray(i);

				int size = path.length();
				paths[i] = new int[size];
					
				for (int j=0; j<size; j++){
					paths[i][j] = path.getInt(j);
				}
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private static final String NORM_LABEL = "normal";
}

class CvShape {
	public Mat meanShape;
	public Mat eigenVectors;
	public Mat eigenValues;
	public Mat eigenConstraints;
}

class ShapeModel {	
	public float[] eigenValues;
	public float[] eigenConstraints;
	public float[][] eigenVectors;
	public float[] meanShape;
	
	public CvShape cvData;
	
	public ShapeModel(JSONObject shape){
		try {
			int numEvec = shape.getInt(EVEC_NUM_LABEL);
			int numPts = shape.getInt(PTS_NUM_LABEL);
			
			eigenValues = new float[numEvec];
			eigenConstraints = new float[numEvec];
			eigenVectors = new float[numPts*2][numEvec];
			meanShape = new float[numPts*2];
			
			JSONArray evArray = shape.getJSONArray(EVALUE_LABEL);
			for (int i=0; i<numEvec; i++){
				eigenValues[i] = (float)(evArray.getDouble(i));
				eigenConstraints[i] = (float)(Math.sqrt(eigenValues[i])*3);
			}
			
			JSONArray evecArray = shape.getJSONArray(EVEC_LABEL);
			for (int i=0; i<numPts*2; i++){
				JSONArray evecItem = evecArray.getJSONArray(i);
				for (int j=0; j<numEvec; j++){
					eigenVectors[i][j] = (float)(evecItem.getDouble(j));
				}
			}
			
            JSONArray mean = shape.getJSONArray(MEAN_LABEL);
            for (int i=0; i<numPts; i++){
            	JSONArray point = mean.getJSONArray(i);
            	meanShape[i*2] = (float)(point.getDouble(0));
            	meanShape[i*2+1] = (float)(point.getDouble(1));
            }
            
            if (true){
            	cvData = new CvShape();
            	
            	cvData.meanShape = new Mat(numPts*2, 1, CvType.CV_32F);
            	cvData.eigenValues = new Mat(numEvec, 1, CvType.CV_32F);
            	cvData.eigenConstraints = new Mat(numEvec, 1, CvType.CV_32F);
            	cvData.eigenVectors = new Mat(numPts*2, numEvec, CvType.CV_32F);
            	
            	for (int i=0; i<numPts*2; i++){
            		cvData.meanShape.put(i, 0, meanShape[i]);
            	}
            	
            	for (int i=0; i<numEvec; i++){
            		cvData.eigenValues.put(i, 0, eigenValues[i]);
            		cvData.eigenConstraints.put(i, 0, eigenConstraints);
            	}
            	
            	for (int i=0; i<numPts*2; i++){
            		for (int j=0; j<numEvec; j++){
            			cvData.eigenVectors.put(i,j,eigenVectors[i][j]);
            		}
            	}
            }
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
	
	private static final String PTS_NUM_LABEL = "numPtsPerSample";
	private static final String EVEC_NUM_LABEL = "numEvalues";
	private static final String EVEC_LABEL = "eigenVectors";
	private static final String EVALUE_LABEL = "eigenValues";
	private static final String MEAN_LABEL = "meanShape";
}

class PatchModel {
	
	public float[] weightsList;
	public float[] biasList;
	public int sampleWidth;
	public int sampleHeight;
			
	public PatchModel(JSONObject patches, int num){
		patchNum = num;
		
		try{
			JSONArray weightsArr = patches.getJSONObject(WEIGHT_LABEL).getJSONArray(TYPE_LABEL);
			JSONArray biasArr = patches.getJSONObject(BIAS_LABEL).getJSONArray(TYPE_LABEL);
			JSONArray sizeArr = patches.getJSONArray(SIZE_LABEL);
			
			sampleWidth = sizeArr.getInt(0);
			sampleHeight = sizeArr.getInt(1);
			int sampleSize = sampleWidth*sampleHeight;
			
			weightsList = new float[sampleSize*num];
			biasList = new float[num];
			
			for (int i=0; i<num; i++){
				JSONArray weights = weightsArr.getJSONArray(i);
				for (int j=0; j<sampleSize; j++){
					weightsList[i*sampleSize+j] = (float)(weights.getDouble(j));
				}
				biasList[i] = (float)(biasArr.getDouble(i));
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
	
	private int patchNum;
	private static final String SIZE_LABEL = "patchSize";
	private static final String WEIGHT_LABEL= "weights";
	private static final String BIAS_LABEL= "bias";
	private static final String TYPE_LABEL = "raw";
}
