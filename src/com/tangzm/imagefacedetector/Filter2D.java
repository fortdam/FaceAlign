package com.tangzm.imagefacedetector;

import android.content.Context;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;

public class Filter2D {
	public Filter2D(Context ctx, float[] weights, float[] biases, byte[] patches, int numPatch, int filterW, int filterH, int patchW, int patchH){
		mRS = RenderScript.create(ctx);
		
		mScript = new ScriptC_filter2d(mRS, ctx.getResources(), R.raw.filter2d);
		
		int responseSize = (patchW-filterW+1)*(patchH-filterH+1);
		mResponse = new float[responseSize*numPatch];
		
		mWeightAlloc = Allocation.createSized(mRS, Element.F32(mRS), weights.length, Allocation.USAGE_SCRIPT);
		mBiasAlloc = Allocation.createSized(mRS, Element.F32(mRS), biases.length, Allocation.USAGE_SCRIPT);
		mPatchAlloc = Allocation.createSized(mRS, Element.U8(mRS), patches.length, Allocation.USAGE_SCRIPT);
		mResponseAlloc = Allocation.createSized(mRS, Element.F32(mRS), numPatch*responseSize, Allocation.USAGE_SCRIPT);
		mIndexAlloc = Allocation.createSized(mRS, Element.U32(mRS), numPatch*responseSize, Allocation.USAGE_SCRIPT);
		
		mWeightAlloc.copyFrom(weights);
		mBiasAlloc.copyFrom(biases);
		mPatchAlloc.copyFrom(patches);
		
		int[] indice = new int[numPatch*responseSize];
		
		for (int i=0; i<numPatch; i++){
			for (int k=0; k<(patchH-filterH+1); k++){
				for (int l=0; l<(patchW-filterW+1); l++){
					int index = i*responseSize + k*(patchW-filterW+1) + l;
					indice[index] = index; 
				}
			}
		}
		mIndexAlloc.copyFrom(indice);
		
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
	}
	
	public void setPatches(byte[] patches){
		mPatchAlloc.copyFrom(patches);
	}
	
	public void process(){
		mScript.forEach_root(mIndexAlloc, mIndexAlloc);
		mResponseAlloc.copyTo(mResponse);
	}
	
	public float[] gerResponseImages(){
		return mResponse;
	}
	
	private RenderScript mRS;
	private ScriptC_filter2d mScript;
	
	private Allocation mWeightAlloc;
	private Allocation mBiasAlloc;
	private Allocation mPatchAlloc;
	private Allocation mResponseAlloc;
	private Allocation mIndexAlloc;
	
	private float[] mResponse;
}
