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
	int patchID = ((*v_in)>>16) & 0xff;
	int lineResp = ((*v_in)>>8) & 0xff;
	int rowResp = (*v_in) & 0xff;
	float result = 0;
	
	float minn = 0;
	float maxn = 256;
	float filterTemp = 0;
	
	int patchOffset = patchID*patchSize;
	int weightOffset = patchID*weightSize;
	
	for (int i=0; i<weightHeight; i++) {
	    for (int j=0; j<weightWidth; j++) {
	        uchar patchValue = gPatchList[patchOffset + (i+lineResp)*patchWidth + j+rowResp];
	        float filterValue = gWeightsList[weightOffset + i*weightWidth + j];
	        
	        minn = fmin(minn, gPatchList[patchOffset + (i+lineResp)*patchWidth + j+rowResp]);
	        maxn = fmax(maxn, gWeightsList[weightOffset + i*weightWidth + j]);
	        
	        result += patchValue*filterValue;
	        filterTemp += filterValue; 
	    }
	} 
	result = (result - (minn*filterTemp))/(maxn-minn);
	result += gBiasList[patchID];
	gResponseList[patchID*respSize + respWidth*lineResp + rowResp] = 1/(1+exp(-result));
}
