package com.tangzm.imagefacedetector;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;

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

class ShapeModel {	
	public double[] eigenValues;
	public double[][] eigenVectors;
	public double[] meanShape;
	
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
            for (int i=0; i<numPts*2; i++){
            	meanShape[i] = mean.getDouble(i);
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