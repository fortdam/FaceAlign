package com.tangzm.facedetect;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Type;
import android.renderscript.RenderScript;

class Transaction{	
	public Transaction (RawImage owner){
		mType = owner.getRawType();

		mMirror = 0;
		mRotate = 0;
		
		mImageWidth = -1;
		mImageHeight = -1;
		
		mScaleX = 1.0f;
		mScaleY = 1.0f;
		
		mOwner = owner;
	}
	
	public Transaction rotate(int degree){
		mRotate += degree;
		return this;
	}
	
	public int getRotate() {
		normalize();
		return mRotate;
	}
	
	public Transaction mirror() {
		mMirror = 1-mMirror;
		return this;
	}
	
	public int getMirror() {
		return mMirror;
	}
	
	public Transaction setWidth(int width){
		mImageWidth = width;
		mScaleX = 1.0f; //Invalidate the scale factor
		return this;
	}
	
	public int getWidth() {
		normalize();
		return mImageWidth;
	}
	
	public Transaction setHeight(int height){
		mImageHeight = height;
		mScaleY = 1.0f; //Invalidate the scale factor
		return this;
	}
	
	public int getHeight() {
		normalize();
		return mImageHeight;
	}
	
	public Transaction scaleX(float factor){
		mScaleX *= factor;
		return this;
	}
	
	public Transaction scaleY(float factor){
		mScaleY *= factor;
		return this;
	}
	
	public Transaction scale(float factor){
		return scaleX(factor).scaleY(factor);
	}
	
	public Transaction setType(RawImage.ImgType type) {
		mType = type;
		return this;
	}
	
	public RawImage.ImgType getType() {
		return mType;
	}
	
	public Transaction normalize(){
		//Normalize rotation
		while (mRotate > 360) {
			mRotate -= 360;
		}
		
		while (mRotate < 0) {
			mRotate += 360;
		}
		
		//Normalize width & height
		if (-1 == mImageWidth) {
			if (90 == mRotate%180) {
				mImageWidth = (int)(mOwner.getRawHeight() * mScaleX);
			}
			else {
				mImageWidth = (int)(mOwner.getRawWidth() * mScaleX);
			}
			mScaleX = 1.0f;
		}
		
		if (-1 == mImageHeight) {
			if (90 == mRotate%180) {
				mImageHeight = (int)(mOwner.getRawWidth() * mScaleY);
			}
			else {
				mImageHeight = (int)(mOwner.getRawHeight() * mScaleY);
			}	
			mScaleY = 1.0f;
		}
		
		return this;
	}
	

	private RawImage.ImgType mType;

	private int mMirror;
	private int mRotate;
	
	private int mImageWidth;
	private int mImageHeight;
	
	private float mScaleX;
	private float mScaleY;
	
	private RawImage mOwner;
};



public class RawImage {
	
	public static enum ImgType{
		TYPT_DEFAULT,
		TYPE_NV21,
		TYPE_RGB565,
		TYPE_RGB888,
		TYPE_ARGB8888,
		TYPE_GRAY8
	};
	
	
	public static void init(Context ctx){
		mRS = RenderScript.create(ctx);
		mScript = new ScriptC_raw_image(mRS);
	}
	
	public RawImage(byte[] buffer, int width, int height, ImgType type){
		mData = buffer;
		mWidth = width;
		mHeight = height;
		mType = type;
		mTransact = null;
	}
	
	public ImgType getRawType() {
		return mType;
	}
	
	public int getRawWidth() {
		return mWidth;
	}
	
	public int getRawHeight() {
		return mHeight;
	}
	
	public byte[] getRawData() {
		return mData;
	}
	
	public RawImage setType(ImgType type) {
		getTransact().setType(type);
		return this;
	}
	
	public ImgType getType() {
		if (null == mTransact){
			return getRawType();
		}
		else {
			return mTransact.getType();
		}
	}
	
	public RawImage setWidth(int width) {
		getTransact().setWidth(width);
		return this;
	}
	
	public int getWidth() {
		if (null == mTransact) {
			return getRawWidth();
		}
		else {
			return mTransact.getWidth();
		}
	}
	
	public RawImage setHeight(int height) {
		getTransact().setHeight(height);
		return this;
	}
	
	public int getHeight() {
		if (null == mTransact) {
			return getRawHeight();
		}
		else {
			return mTransact.getHeight();
		}
	}
	
	public RawImage rotate(int degree) {
		getTransact().rotate(degree);
		return this;
	}
	
	public RawImage mirror() {
		getTransact().mirror();
		return this;
	}
	
	public RawImage scaleX(float factor) {
		getTransact().scaleX(factor);
		return this;
	}
	
	public RawImage scaleY(float factor) {
		getTransact().scaleY(factor);
		return this;
	}
	
	public RawImage scale(float factor) {
		getTransact().scale(factor);
		return this;
	}
	
	public byte[] getData() {
		if (null == mTransact){
			return getRawData();
		}
		else {
			performTransact();
			return getRawData();
		}
	}
	
	static RawImage fromBitmap(Bitmap bitmap){
		ImgType type;
		byte[] data;
		
		if (Bitmap.Config.RGB_565 == bitmap.getConfig()) {
			type = ImgType.TYPE_RGB565;
			data = new byte[2 * bitmap.getWidth()*bitmap.getHeight()];
		}
		else if (Bitmap.Config.ARGB_8888 == bitmap.getConfig()) {
			type = ImgType.TYPE_ARGB8888;
			data = new byte[4* bitmap.getWidth()*bitmap.getHeight()];
		}
		else if (Bitmap.Config.ALPHA_8 == bitmap.getConfig()) {
			type = ImgType.TYPE_GRAY8;
			data = new byte[bitmap.getWidth()*bitmap.getHeight()];
		}
		else {
			return null;
		}
		
		Buffer buffer = ByteBuffer.wrap(data);
		bitmap.copyPixelsToBuffer(buffer);
		return new RawImage(data, bitmap.getWidth(),bitmap.getHeight(),type);
	}
	
	public Bitmap toBitmap(){
		Bitmap bmp;
		
		if (ImgType.TYPE_RGB565 == getType()){
			bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.RGB_565);
		}
		else if (ImgType.TYPE_ARGB8888 == getType() || ImgType.TYPT_DEFAULT == getType()){
			bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
		}
		else if (ImgType.TYPE_GRAY8 == getType()) {
			bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ALPHA_8);
		}
		else {
			return null;
		}
		
		Buffer buffer = ByteBuffer.wrap(getData());
		bmp.copyPixelsFromBuffer(buffer);
		
		return bmp;
	}
	
	private Transaction getTransact() {
		if (null == mTransact){
			mTransact = new Transaction(this);
		}
		
		return mTransact;
	}
	
	private void performTransact() {
		if (null == mTransact){
			return;
		}
		
		mTransact.normalize();

		Type.Builder tbIn;
		
		tbIn = new Type.Builder(mRS, Element.U8(mRS));
        Type.Builder tbOut ;
        tbOut= new Type.Builder(mRS, Element.U8(mRS));
        
        if (ImgType.TYPE_NV21 == mType){
        	int size = mWidth*mHeight+((mWidth+1)/2) * ((mHeight+1)/2) * 2;
        	tbIn.setX(size);
        	
        	mScript.set_inType(21);
        }
        else if (ImgType.TYPE_GRAY8 == mType){
        	tbIn.setX(mWidth*mHeight);
        	mScript.set_inType(8);
        }
        else if (ImgType.TYPE_RGB565 == mType) {
        	tbIn.setX(mWidth*mHeight*2);
        	mScript.set_inType(565);
        }
        else if (ImgType.TYPE_RGB888 == mType) {
        	tbIn.setX(mWidth*mHeight*3);
        	mScript.set_inType(888);
        }
        else if (ImgType.TYPE_ARGB8888 == mType || ImgType.TYPT_DEFAULT == mType){
        	tbIn.setX(mWidth*mHeight*4);
        	mScript.set_inType(8888);
        }
        
        int finalImageSize = mTransact.getWidth() * mTransact.getHeight();
        
        if (ImgType.TYPE_GRAY8 == mTransact.getType()) {
        	tbOut.setX(finalImageSize);
        	mScript.set_outType(8);
        }
        else if (ImgType.TYPE_RGB565 == mTransact.getType()){
        	tbOut.setX(finalImageSize*2);
        	mScript.set_outType(565);
        }
        else if (ImgType.TYPE_RGB888 == mTransact.getType()) {
        	tbOut.setX(finalImageSize*3);
        	mScript.set_outType(888);
        }
        else if (ImgType.TYPE_ARGB8888 == mTransact.getType()) {
        	tbOut.setX(finalImageSize*4);
        	mScript.set_outType(8888);
        }

        Allocation inAlloc = Allocation.createTyped(mRS, tbIn.create());
        Allocation outAlloc = Allocation.createTyped(mRS, tbOut.create());

        inAlloc.copyFrom(mData);

        mScript.set_rotate(mTransact.getRotate());
        mScript.set_mirror(mTransact.getMirror());

        mScript.set_oWidth(mWidth);
        mScript.set_oHeight(mHeight);
        mScript.set_oSize(mWidth*mHeight);

        if (90 == mTransact.getRotate()%180) {
        	mScript.set_mWidth(mHeight);
        	mScript.set_mHeight(mWidth);        	
        }
        else {
        	mScript.set_mWidth(mWidth);
        	mScript.set_mHeight(mHeight);
        }

        mScript.set_sWidth(mTransact.getWidth());
        mScript.set_sHeight(mTransact.getHeight());
        
        
        mScript.bind_input(inAlloc);
        mScript.bind_output(outAlloc);
        
        
        int[] indexBuffer = new int[mTransact.getHeight()];
        
        for (int i=0; i<indexBuffer.length; i++){
        	indexBuffer[i] = i;
        }
        
        Type.Builder tbIndex = new Type.Builder(mRS, Element.U32(mRS));
        tbIndex.setX(indexBuffer.length);
        Allocation indexAlloc = Allocation.createTyped(mRS, tbIndex.create());
        indexAlloc.copyFrom(indexBuffer);
        mScript.forEach_process(indexAlloc);
        
        if (mData.length != outAlloc.getBytesSize()){
        	mData = new byte[outAlloc.getBytesSize()];
        }
        
        outAlloc.copyTo(mData);
        
        mWidth = mTransact.getWidth();
        mHeight = mTransact.getHeight();
        mType = mTransact.getType();
        mTransact = null;
	}
	
	private byte[] mData;
	private int mWidth;
	private int mHeight;
	private ImgType mType;
	private Transaction mTransact;
	
	private static ScriptC_raw_image mScript;
	private static RenderScript mRS;
}
