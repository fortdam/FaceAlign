package com.tangzm.facedetect;

import com.tangzm.facedetect.ScriptC_filter2d;

import android.content.Context;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

public class Filter2D {
	public Filter2D(Context ctx, float[] weights, float[] biases, byte[] patches, int numPatch, int filterW, int filterH, int patchW, int patchH){
		FuncTracer.startFunc();
		
		mRS = RenderScript.create(ctx);
		
		mScript = new ScriptC_filter2d(mRS);
		
		int responseSize = (patchW-filterW+1)*(patchH-filterH+1);
		mResponse = new float[responseSize*numPatch];
		
		mWeightAlloc = Allocation.createSized(mRS, Element.F32(mRS), filterW*filterH*numPatch, Allocation.USAGE_SCRIPT);
		mBiasAlloc = Allocation.createSized(mRS, Element.F32(mRS), numPatch, Allocation.USAGE_SCRIPT);
		mPatchAlloc = Allocation.createSized(mRS, Element.U8(mRS), patchW*patchH*numPatch, Allocation.USAGE_SCRIPT);
		mResponseAlloc = Allocation.createSized(mRS, Element.F32(mRS), numPatch*responseSize, Allocation.USAGE_SCRIPT);
		mProcResponseAlloc = Allocation.createSized(mRS, Element.F32(mRS), numPatch*responseSize, Allocation.USAGE_SCRIPT);
		
		mFilterIndexAlloc = Allocation.createSized(mRS, Element.U32(mRS), numPatch*responseSize, Allocation.USAGE_SCRIPT);
		mRegularizeIndexAlloc = Allocation.createSized(mRS, Element.U32(mRS), numPatch, Allocation.USAGE_SCRIPT);;
		
		if (null != weights){
			mWeightAlloc.copyFrom(weights);
		}
		
		if (null != biases){
			mBiasAlloc.copyFrom(biases);
		}
		
		if (null != patches){
			mPatchAlloc.copyFrom(patches);
		}
		
		int[] indice = new int[numPatch*responseSize];
		int[] regularIndice = new int[numPatch];
		
		for (int i=0; i<numPatch; i++){
			for (int k=0; k<(patchH-filterH+1); k++){
				for (int l=0; l<(patchW-filterW+1); l++){
					int index = i*responseSize + k*(patchW-filterW+1) + l;
					indice[index] = index; 
				}
			}
			regularIndice[i] = i;
		}
		mFilterIndexAlloc.copyFrom(indice);
		mRegularizeIndexAlloc.copyFrom(regularIndice);
		
		mScript.set_patchNum(numPatch);

		mScript.set_weightWidth(filterW);
		mScript.set_weightHeight(filterH);
		mScript.set_weightSize(filterW*filterH);
		
		mScript.set_patchWidth(patchW);
		mScript.set_patchHeight(patchH);
		mScript.set_patchSize(patchW*patchH);
		
		mScript.set_respWidth(patchW-filterW+1);
		mScript.set_respHeight(patchH-filterH+1);
		mScript.set_respSize(responseSize);
		
		mScript.bind_gWeightsList(mWeightAlloc);
		mScript.bind_gBiasList(mBiasAlloc);
		mScript.bind_gPatchList(mPatchAlloc);
		mScript.bind_gResponseList(mResponseAlloc);
		mScript.bind_gProcResponseList(mProcResponseAlloc);
		
		FuncTracer.endFunc();
	}
	
	public void setModel(float[] weights, float[] biases){
		mWeightAlloc.copyFrom(weights);
		mBiasAlloc.copyFrom(biases);
		mScript.bind_gWeightsList(mWeightAlloc);
		mScript.bind_gBiasList(mBiasAlloc);
	}
	
	public void setPatches(byte[] patches){
		mPatchAlloc.copyFrom(patches);
		mScript.bind_gPatchList(mPatchAlloc);
	}
	
	public void process(ResponseType type){
		FuncTracer.startFunc();
		
		mScript.forEach_filter(mFilterIndexAlloc);
		
		if (ResponseType.RAW == type){
			mResponseAlloc.copyTo(mResponse);
		}
		else {
			if (ResponseType.REGULARIZED == type || ResponseType.REG_NORMALIZED == type) {
				mScript.set_regularized((short)1);
			}
			else {
				mScript.set_regularized((short)0);
			}
			
			if (ResponseType.NORMALIZED == type || ResponseType.REG_NORMALIZED == type) {
				mScript.set_normalized((short)1);
			}
			else {
				mScript.set_normalized((short)0);
			}
			
			mScript.forEach_regNorm(mRegularizeIndexAlloc);
			mProcResponseAlloc.copyTo(mResponse);
		}
		
		FuncTracer.endFunc();
	}
	
	public float[] gerResponseImages(){
		for (int i=0; i<mResponse.length; i++) {
			if (Float.isNaN(mResponse[i])) {
				mResponse[i] = 0;
			}
		}
		return mResponse;
	}
	
	public enum ResponseType {
		RAW,
		REGULARIZED,
		NORMALIZED,
		REG_NORMALIZED,
	}
	
	
	private RenderScript mRS;
	private ScriptC_filter2d mScript;
	
	private Allocation mWeightAlloc;
	private Allocation mBiasAlloc;
	private Allocation mPatchAlloc;
	private Allocation mResponseAlloc;
	private Allocation mProcResponseAlloc;
	private Allocation mFilterIndexAlloc;
	private Allocation mRegularizeIndexAlloc;
	
	private float[] mResponse;
}
