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

float *gProcResponseList;

uint8_t regularized;
uint8_t normalized;

void __attribute__((kernel)) regNorm(uint32_t in) {
	float maxn = 0;
	float minn = 1.0;
	float scale = 1.0;
	float sum = 0;

	//Find the upper-lower bounds
	for (int i=0; i<respHeight; i++) {
		for (int j=0; j<respWidth; j++) {
			float curr = gResponseList[respSize*in+i*respWidth+j];
			maxn = fmax(maxn, curr);
			minn = fmin(minn, curr);
		}
	}  
	
	if (1 == regularized) {
		scale = maxn-minn;
		
		//Do regulariztion for each point
		for (int i=0; i<respHeight; i++) {
			for (int j=0; j<respWidth; j++) {
				gProcResponseList[respSize*in+i*respWidth+j] = 
					(gResponseList[respSize*in+i*respWidth+j] - minn)/scale;
			}
		} 	
	}
	
	if (1 == normalized) {
	    sum = 0;
	    
		for (int i=0; i<respHeight; i++) {
			for (int j=0; j<respWidth; j++) {
				sum += gProcResponseList[respSize*in+i*respWidth+j];
			}
		} 
		
		for (int i=0; i<respHeight; i++) {
			for (int j=0; j<respWidth; j++) {
				gProcResponseList[respSize*in+i*respWidth+j] = gProcResponseList[respSize*in+i*respWidth+j]/sum;
			}
		} 		
	}
	


	
} 

void __attribute__((kernel)) filter(uint32_t in) {
	int patchID = in/respSize;
	int lineResp = (in%respSize)/respWidth;
	int rowResp = in%respWidth;
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
	gResponseList[in] = 1/(1+exp(-result));
}
