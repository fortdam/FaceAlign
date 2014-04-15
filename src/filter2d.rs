#pragma version(1)
#pragma rs java_package_name(com.tangzm.imagefacedetector)

int patchNum;
int patchWidth;
int patchHeight;
int patchSize;
int weightWidth;
int weightHeight;
int weightSize;
int respWidth;
int respHeight;
int respSize;

float *gWeightsList;
float *gBiasList;
uchar *gPatchList;
float *gResponseList;

void root(const uint32_t *v_in, uint32_t *v_out, const void *usrData, uint32_t x, uint32_t y) {
	int patchID = (*v_in)/respSize;
	int lineResp = ((*v_in)%respSize)/respWidth;
	int rowResp = (*v_in)%respWidth;
	float result = 0;
	
	float minn = 256;
	float maxn = 0;
	float filterTemp = 0;
	
	int patchOffset = patchID*patchSize;
	int weightOffset = patchID*weightSize;
	
	for (int i=0; i<weightHeight; i++) {
	    for (int j=0; j<weightWidth; j++) {
	        float patchValue = (float)(gPatchList[patchOffset + (i+lineResp)*patchWidth + j+rowResp]);
	        float filterValue = gWeightsList[weightOffset + i*weightWidth + j];
	        
	        minn = fmin(minn, gPatchList[patchOffset + (i+lineResp)*patchWidth + j+rowResp]);
	        maxn = fmax(maxn, gPatchList[patchOffset + (i+lineResp)*patchWidth + j+rowResp]);
	        

	        result += patchValue*filterValue;
	        filterTemp += filterValue; 
	    }
	} 
	
	result = (result - (minn*filterTemp))/(maxn-minn);
	result += gBiasList[patchID];
	gResponseList[*v_in] = 1/(1+exp(-result));
}
