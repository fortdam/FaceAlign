package com.tangzm.facedetect;

import org.ejml.simple.SimpleMatrix;

import com.tangzm.facedetect.ScriptC_qmat;

import android.content.Context;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;

public class QMatrix {
	
	public static void init(Context ctx) {
		mRS = RenderScript.create(ctx);
		mScript = new ScriptC_qmat(mRS);
		mTB = new Type.Builder(mRS, Element.U8(mRS));
	}
	
	public QMatrix(int rows, int columns){
		mRows = rows;
		mColumns = columns;
		mData = new float[rows*columns];
	}
	
	public static QMatrix eye(int dim) {
		QMatrix m = new QMatrix(dim,dim);
		for (int i=0; i<dim; i++) {
			m.mData[i*dim+i] = 1;
		}
		return m;
	}
	
	public static QMatrix zeros(int rows, int columns) {
		QMatrix m = new QMatrix(rows,columns);
		return m;
	}
	
	
	public QMatrix(QMatrix m, boolean deepCopy){
		mRows = m.mRows;
		mColumns = m.mColumns;
		
		if (deepCopy) {
			mData = new float[mRows*mColumns];
			System.arraycopy(m.mData, 0, mData, 0, mRows*mColumns);
		}
		else {
			mData = m.mData; //Using shallow copy
		}
	}
	
	public QMatrix(float[] data, int rows, int columns, boolean wrapper) {
		if (STRICT_MODE && data.length<rows*columns){
			Log.e(TAG, "Exception: Size error of creating QMatrix");
			return;
		}
		
		mRows = rows;
		mColumns = columns;
		
		if (wrapper) {
			mData = data;
		}
		else {
			mData = new float[rows*columns];
			System.arraycopy(data, 0, mData, 0, rows*columns);
		}
	}
	
	public int numRows(){
		return mRows;
	}
	
	public int numCols() {
		return mColumns;
	}
	
	public int length() {
		return (mRows>mColumns?mRows:mColumns);
	}
	
	public float get(int index) {
		return mData[index];
	}
	
	public float get(int row, int col) {
		return mData[row*mColumns+col];
	}
	
	public float[] get(int row, int col, int num){
		float[] result = new float[num];
		System.arraycopy(mData, row*mColumns+col, result, 0, num);
		return result;
	}
	
	public void set(int index, float val){
		mData[index] = val;
	}
	
	public void set(int index, float[] values) {
		System.arraycopy(values, 0, mData, index, values.length);
	}
	
	public void set(int row, int col, float val) {
		mData[row*mColumns+col] = val;
	}
	
	public void set(int row, int col, float[] values) {
		System.arraycopy(values, 0, mData, row*mColumns+col, values.length);
	}
	
	public QMatrix columns(int start, int end) {
		if (STRICT_MODE && (end>mColumns+1 || end<=start || start<0)){
			Log.e(TAG, "Exception: Size error of creating QMatrix");
			return null;
		}
		
		QMatrix m = new QMatrix(mRows, end-start);
		
		for (int i=0; i<mRows; i++){
			System.arraycopy(mData, i*mColumns+start, m.mData, i*m.mColumns, m.mColumns);
		}
		
		return m;
	}
	
	public QMatrix rows(int start, int end) {
		if (STRICT_MODE && (end>mRows+1 || end<=start || start<0)){
			Log.e(TAG, "Exception: Size error of creating QMatrix");
			return null;
		}
		
		QMatrix m = new QMatrix(end-start, mColumns);
		
		System.arraycopy(mData, start*mColumns, m.mData, 0, m.mRows*m.mColumns);
		
		return m;		
	}
	
	private void plus_core(QMatrix target, QMatrix mat1, QMatrix mat2) {
		for (int i=0; i<target.mData.length; i++){
			target.mData[i] = mat1.mData[i] + mat2.mData[i];
		}	
	}
	
	public QMatrix plus(QMatrix another) {
		if (STRICT_MODE && (mRows != another.mRows || mColumns != another.mColumns)){
			Log.e(TAG, "Exception: Size error of adding QMatrix");
			return null;
		}
		
		QMatrix result = new QMatrix(mRows, mColumns);
		plus_core(result, this, another);
		
		return result;
	}
	
	public QMatrix plusSelf(QMatrix another) {
		if (STRICT_MODE && (mRows != another.mRows || mColumns != another.mColumns)){
			Log.e(TAG, "Exception: Size error of adding QMatrix");
			return null;
		}	
		
		plus_core(this, this, another);
		
		return this;
	}
	
	private void minus_core(QMatrix target, QMatrix mat1, QMatrix mat2) {
		for (int i=0; i<target.mData.length; i++){
			target.mData[i] = mat1.mData[i] - mat2.mData[i];
		}	
	}
	
	public QMatrix minus(QMatrix another) {
		if (STRICT_MODE && (mRows != another.mRows || mColumns != another.mColumns)){
			Log.e(TAG, "Exception: Size error of minus QMatrix");
			return null;
		}
		
		QMatrix result = new QMatrix(mRows, mColumns);
		minus_core(result, this, another);
		
		return result;
	}
	
	public QMatrix minusSelf(QMatrix another) {
		if (STRICT_MODE && (mRows != another.mRows || mColumns != another.mColumns)){
			Log.e(TAG, "Exception: Size error of minus QMatrix");
			return null;
		}	
		
		minus_core(this, this, another);
		
		return this;
	}
	
	private static void plusRep_core(QMatrix target, QMatrix base, QMatrix factor, RepMode mode){
		int rowDist = base.mRows/factor.mRows;
		int colDist = base.mColumns/factor.mColumns;
		
		if (target != base){
			System.arraycopy(base.mData, 0, target.mData, 0, base.mRows*base.mColumns);
		}
		
		if (RepMode.HORIZONTAL_ONLY == mode) {
			rowDist = 1;
		}
		
		if (RepMode.VERTICAL_ONLY == mode) {
			colDist = 1;
		}
		
		for (int rb=0; rb<rowDist; rb++) {
			int rMove = rb*factor.mRows;
			for (int cb=0; cb<colDist; cb++) {
				int cMove = cb*factor.mColumns;
				for (int i=0; i<factor.mRows; i++) {
					for (int j=0; j<factor.mColumns; j++) {
						target.mData[(rMove+i)*target.mColumns+(cMove+j)] += 
								factor.mData[i*factor.mColumns+j];
					}
				}
			}
		}		
	}
	
	public QMatrix plusRep(QMatrix another, RepMode mode) {
		QMatrix result = new QMatrix(mRows, mColumns);
		plusRep_core(result, this, another, mode);
		return result;
	}
	
	public QMatrix plusRepSelf(QMatrix another, RepMode mode) {
		plusRep_core(this, this, another, mode);
		return this;
	}
	
	private static void multRep_core(QMatrix target, QMatrix factorMat, QMatrix baseMat) {
		
		int repNum = baseMat.mRows/factorMat.mRows;
		
		Allocation op1Mat = Allocation.createSized(mRS, Element.F32(mRS), factorMat.mRows*factorMat.mColumns);
		Allocation op2Mat = Allocation.createSized(mRS, Element.F32(mRS), baseMat.mRows*baseMat.mColumns);
		Allocation resultMat = Allocation.createSized(mRS, Element.F32(mRS), baseMat.mRows*baseMat.mColumns);
		Allocation index = Allocation.createTyped(mRS, mTB.setX(1).setY(repNum).create());
		
		op1Mat.copyFrom(factorMat.mData);
		op2Mat.copyFrom(baseMat.mData);
		
		mScript.bind_opMat1(op1Mat);
		mScript.bind_opMat2(op2Mat);
		mScript.bind_resultMat(resultMat);
		mScript.set_numRow(baseMat.mRows);
		mScript.set_numColumn(baseMat.mColumns);
		mScript.set_dim(factorMat.mRows);
		
		mScript.forEach_mulRep(index);
		
		resultMat.copyTo(target.mData);		
	}
	
	public QMatrix multRep(QMatrix another) {
		if (STRICT_MODE && (another.mRows != another.mColumns)){
			Log.e(TAG, "Exception: Size error of mulRep QMatrix");
			return null;
		}
		
		QMatrix result = new QMatrix(mRows, mColumns);
		multRep_core(result, another, this);
		return result;
	}
	
	public QMatrix multRepSelf(QMatrix another) {
		if (STRICT_MODE && (another.mRows != another.mColumns)){
			Log.e(TAG, "Exception: Size error of mulRep QMatrix");
			return null;
		}
		
		multRep_core(this, another, this);
		return this;
	}
	
	public QMatrix transpose(){
		QMatrix result = new QMatrix(mColumns, mRows);
		
		for (int i=0; i<mRows; i++) {
			for (int j=0; j<mColumns; j++) {
				result.mData[j*result.mColumns+i] = mData[i*mColumns+j];
			}
		}
		return result;
	}
	
	public void luDecomp(){
		if (STRICT_MODE && mRows!=mColumns) {
			Log.e(TAG, "Exception: Size error of L/U decomposition QMatrix");
			return;
		}
		
		mLUMat = new float[mRows*mColumns];
		mPMat = new int[mRows];
		float[] tempLine = new float[mColumns];
		
		for (int i=0; i<mRows; i++){
			mPMat[i] = i;
		}
		
		System.arraycopy(mData, 0, mLUMat, 0, mRows*mColumns);
		
		for (int i=0; i<mRows-1; i++) {
			int maxii = i;
			
			for (int k=i+1; k<mRows; k++){
				if (Math.abs(mLUMat[k*mColumns+i]) > Math.abs(mLUMat[maxii*mColumns+i])) {
					maxii = k;
				}
			}
			
			if (maxii != i) {
				//exchange 2 lines
				mPMat[i] = maxii;
				mPMat[maxii] = i;
				
				System.arraycopy(mLUMat, i*mColumns, tempLine, 0, mColumns);
				System.arraycopy(mLUMat, maxii*mColumns, mLUMat, i*mColumns, mColumns);
				System.arraycopy(tempLine, 0, mLUMat, maxii*mColumns, mColumns);
			}
			
			
			for (int j=i+1; j<mColumns; j++) {
				mLUMat[j*mColumns + i] /= mLUMat[i*mColumns+i];
				
				for (int k=i+1; k<mRows; k++) {
					mLUMat[j*mColumns+k] -= (mLUMat[i*mColumns+k] * mLUMat[j*mColumns+i]);
				}
			}
		}
	}
	
	private QMatrix multSlow(QMatrix another) {
		QMatrix result = new QMatrix(mRows, another.mColumns);

		for (int i=0; i<mRows; i++) {
			for (int j=0; j<another.mColumns; j++){
				float value = 0;
				for (int k=0; k<mColumns; k++) {
					value += get(i,k)*another.get(k, j);
				}
				result.set(i, j, value);
			}
		}
		
		return result;
	}

	public QMatrix mult(QMatrix another) {
		if (STRICT_MODE && mColumns!=another.mRows) {
			Log.e(TAG, "Exception: Size error multiplying QMatrix");
			return null;
		}
		
		if (mRows*mColumns*another.mColumns < 1000) {
			return multSlow(another);
		}
		else {
			QMatrix result = new QMatrix(mRows, another.mColumns);

			Allocation op1Mat = Allocation.createSized(mRS, Element.F32(mRS), mRows*mColumns);
			Allocation op2Mat = Allocation.createSized(mRS, Element.F32(mRS), another.mRows*another.mColumns);
			Allocation resultMat = Allocation.createSized(mRS, Element.F32(mRS), mRows*another.mColumns);
			Allocation index = Allocation.createTyped(mRS, mTB.setX(another.mColumns).setY(mRows).create());
			
			op1Mat.copyFrom(mData);
			op2Mat.copyFrom(another.mData);
			
			mScript.bind_opMat1(op1Mat);
			mScript.bind_opMat2(op2Mat);
			mScript.bind_resultMat(resultMat);
			mScript.set_numRow(mRows);
			mScript.set_numColumn(another.mColumns);
			mScript.set_dim(mColumns);
			
			mScript.forEach_muliply(index);
			
			resultMat.copyTo(result.mData);
			
			return result;
		}
	}
	
	private QMatrix resolveSlow(QMatrix result) {
		for (int i=0; i<mColumns; i++) {
			for (int j=0; j<mRows; j++) {
				float sum = 0;
				for (int k=0; k<j; k++) {
					sum += mLUMat[j*mColumns+k] * result.mData[k*mColumns+i];
				}
				result.mData[j*mColumns+i] = result.mData[j*mColumns+i] - sum;
			}
			
			for (int j=mRows-1; j>=0; j--) {
				float sum = 0;
				for (int k=mRows-1; k>j; k--) {
					sum += mLUMat[j*mColumns+k] * result.mData[k*mColumns+i];
				}
				result.mData[j*mColumns+i] = (result.mData[j*mColumns+i] - sum)/mLUMat[j*mColumns+j];
			}
		}
		return result;
	}
	
	public QMatrix invert() {
		if (STRICT_MODE && mRows!=mColumns) {
			Log.e(TAG, "Exception: Size error inversing QMatrix");
			return null;
		}
		
		luDecomp();
		
		QMatrix result = QMatrix.zeros(mRows, mRows);
		
		for (int i=0; i<mRows; i++) {
			result.set(i*mColumns + mPMat[i], 1);
		}
		
		if (mRows <= 5) { 
			result = resolveSlow(result);
		}
		else {
			//use renderscript if the matrix is "big"
			Allocation op1Mat = Allocation.createSized(mRS, Element.F32(mRS), mRows*mColumns);
			Allocation resultMat = Allocation.createSized(mRS, Element.F32(mRS), mRows*mColumns);
			Allocation index = Allocation.createTyped(mRS, mTB.setX(mColumns).setY(1).create());
					
			op1Mat.copyFrom(mLUMat);
			resultMat.copyFrom(result.mData);
			
			mScript.bind_opMat1(op1Mat);
			mScript.bind_resultMat(resultMat);
			mScript.set_numRow(mRows);
			mScript.set_numColumn(mColumns);
			mScript.set_dim(mColumns);
			
			mScript.forEach_resolve(index);
			
			resultMat.copyTo(result.mData);
		}
		
		return result;
	}
	
	public float sum() {
		float result = 0;
		
		for (int i=0; i<mData.length; i++){
			result += mData[i];
		}
		
		return result;
	}
	
	public float innerProduct() {
		if (STRICT_MODE && mColumns>1 && mRows>1) {
			Log.e(TAG, "Exception: Size error on calculating inner-product QMatrix");
			return 0;			
		}
		
		if (mColumns == 1){
			return this.transpose().mult(this).sum();
		}
		else {
			return mult(this.transpose()).sum();
		}
	}
	
	public boolean verify(SimpleMatrix ref){
		final float EPSILON = 0.001f;
		
		if (ref.numCols() != mColumns || ref.numRows() != mRows){
			Log.i(TAG, "Ref:"+ref.numRows()+"*"+ref.numCols()+" QMatrix:"+mRows+"*"+mColumns);
			return false;
		}
		
		if (Float.isNaN(mData[0]) || Math.abs(ref.get(0, 0)-mData[0]) > EPSILON) {
			Log.i(TAG, "Ref[0,0]:"+ref.get(0, 0)+" QMatrix[0,0]:"+mData[0]);
			return false;
		}

		if (Float.isNaN(mData[mRows*mColumns-1]) || Math.abs(ref.get(mRows-1, mColumns-1) - mData[mRows*mColumns-1]) > EPSILON) {
			Log.i(TAG, "Ref[end,end]:"+ref.get(mRows-1, mColumns-1)+" QMatrix[end,end]:"+mData[mRows*mColumns-1]);
			return false;
		}

		if (Float.isNaN(mData[(mRows/2)*mColumns+mColumns/2]) || Math.abs(ref.get(mRows/2, mColumns/2) - mData[(mRows/2)*mColumns+mColumns/2]) > EPSILON) {
			Log.i(TAG, "Ref[mid,mid]:"+ref.get(mRows/2, mColumns/2)+" QMatrix[mid,mid]:"+mData[(mRows/2)*mColumns+mColumns/2]);
			return false;
		}
		
		Log.i(TAG, "Verified Same!");
		return true;
	}
	
	public void printOut(){
		Log.i(TAG, "-----------------Matrix print out--------------------");
		for (int i=0; i<mRows; i++){
			String text = new String();
			for (int j=0; j<mColumns; j++){
				text += "  "+get(i,j);
			}
			Log.i(TAG, text);
		}
		Log.i(TAG, "-----------------Matrix print end--------------------");
	}
	
	public void printLU(){
		for (int i=0; i<mRows; i++){
			String text = new String();
			for (int j=0; j<mColumns; j++){
				text += "  "+mLUMat[i*mColumns+j];
			}
			Log.i(TAG, text);
		}
	}
	
    public enum RepMode{
		HORIZONTAL_ONLY,
		VERTICAL_ONLY,
		BOTH_DIR
	};
	
	private float[] mData;
	private int mRows;
	private int mColumns;
	
	private float[] mLUMat;	
	private int[] mPMat; //pmat is expressed as a vector
	
	private static RenderScript mRS;
    private static ScriptC_qmat mScript;
    private static Type.Builder mTB;
	
	private static final boolean STRICT_MODE = true;
	private static final String TAG = "QMatrix";
}
