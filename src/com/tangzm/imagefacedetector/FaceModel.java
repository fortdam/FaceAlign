package com.tangzm.imagefacedetector;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.ejml.simple.SimpleMatrix;
import org.json.JSONArray;
import org.json.JSONObject;


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
			 numEVectors = shapeModel.mEigenValues.numRows();
			 numPts = shapeModel.mMeanShape.numRows()/2;
			 
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


class ShapeModel {	
	
	public SimpleMatrix mMeanShape;
	public SimpleMatrix mEigenVectors;
	public SimpleMatrix mEigenValues;
	public SimpleMatrix mEigenConstraints;
	
	public ShapeModel(JSONObject shape){
		double[] eigenValues;
		double[] eigenConstraints;
		double[][] eigenVectors;
		double[] meanShape;
		
		try {
			int numEvec = shape.getInt(EVEC_NUM_LABEL);
			int numPts = shape.getInt(PTS_NUM_LABEL);
			
			eigenValues = new double[numEvec];
			eigenConstraints = new double[numEvec];
			eigenVectors = new double[numPts*2][numEvec];
			meanShape = new double[numPts*2];
			
			JSONArray evArray = shape.getJSONArray(EVALUE_LABEL);
			for (int i=0; i<numEvec; i++){
				eigenValues[i] = evArray.getDouble(i);
				eigenConstraints[i] = Math.sqrt(eigenValues[i])*3;
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
            	mMeanShape = new SimpleMatrix(numPts*2, 1, true, meanShape);
            	mEigenValues = new SimpleMatrix(numEvec, 1, true, eigenValues);
            	mEigenConstraints = new SimpleMatrix(numEvec, 1, true, eigenConstraints);
            	mEigenVectors = new SimpleMatrix(eigenVectors);
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
