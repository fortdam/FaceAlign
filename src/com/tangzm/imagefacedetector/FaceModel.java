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
			 shapeModel = new ShapeModel(rawContent.getJSONObject(SHAPE_LABLE));
			 numEVectors = shapeModel.eigenValues.length;
			 numPts = shapeModel.meanShape.length/2;
			 
			 pathModel = new PathModel(rawContent.getJSONObject(PATH_LABEL));
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private static final String SHAPE_LABLE = "shapeModel";
	private static final String PATCH_LABLE = "patchModel";
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
}

class ShapeModel {	
	public double[] eigenValues;
	public double[][] eigenVectors;
	public double[] meanShape;
	
	public CvShape cvData;
	
	public ShapeModel(JSONObject shape){
		try {
			int numEvec = shape.getInt(EVEC_NUM_LABEL);
			int numPts = shape.getInt(PTS_NUM_LABEL);
			
			eigenValues = new double[numEvec];
			eigenVectors = new double[numPts*2][numEvec];
			meanShape = new double[numPts*2];
			
			JSONArray evArray = shape.getJSONArray(EVALUE_LABEL);
			for (int i=0; i<numEvec; i++){
				eigenValues[i] = evArray.getDouble(i);
			}
			
			JSONArray evecArray = shape.getJSONArray(EVEC_LABEL);
			for (int i=0; i<numPts*2; i++){
				JSONArray evecItem = evecArray.getJSONArray(i);
				for (int j=0; j<numEvec; j++){
					eigenVectors[i][j] = evecItem.getDouble(j);
				}
			}
			
            JSONArray mean = shape.getJSONArray(MEAN_LABEL);
            for (int i=0; i<numPts; i++){
            	JSONArray point = mean.getJSONArray(i);
            	meanShape[i*2] = point.getDouble(0);
            	meanShape[i*2+1] = point.getDouble(1);
            }
            
            if (true){
            	cvData = new CvShape();
            	
            	cvData.meanShape = new Mat(numPts*2, 1, CvType.CV_64F);
            	cvData.eigenValues = new Mat(numEvec, 1, CvType.CV_64F);
            	cvData.eigenVectors = new Mat(numPts*2, numEvec, CvType.CV_64F);
            	
            	for (int i=0; i<numPts*2; i++){
            		cvData.meanShape.put(i, 0, meanShape[i]);
            	}
            	
            	for (int i=0; i<numEvec; i++){
            		cvData.eigenValues.put(i, 0, eigenValues[i]);
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
	
}