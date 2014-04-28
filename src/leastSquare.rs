#pragma version(1)
#pragma rs java_package_name(com.tangzm.imagefacedetector)

int numParam;
int numPts;

float* deltaPos;
float* deltaParams;

float* meanShape;
float* eigenVectors;

float* jacobian;
float* jtj;

float* Ljtj;
float* Ujtj;
float* invJtj;

float* totalParams;
 
void __attribute__((kernel)) createJacob(uint32_t in) {
	int i;
	float j0=0;
	float j1=0;

	//1
	j0 = meanShape[in*2];
	j1 = meanShape[in*2+1];
			
	for (i=0; i<numParam; i++){
		j0 += totalParams[i+4]*eigenVectors[in*2*numParam+i];
		j1 += totalParams[i+4]*eigenVectors[(in*2+1)*numParam+i];
	}
	jacobian[in*2*numParam] = j0;
	jacobian[(in*2+1)*numParam] = j1;

			
	//2
	j0 = meanShape[in*2+1];
	j1 = meanShape[in*2];	
			
	for (i=0; i<numParam; i++){
		j0 += totalParams[i+4]*eigenVectors[(in*2+1)*numParam+i];
		j1 += totalParams[i+4]*eigenVectors[in*2*numParam+i];
	}
	jacobian[in*2*numParam+1] = -j0;
	jacobian[(in*2+1)*numParam+1] = j1;

			
	//3
	jacobian[in*2*numParam+2] = 1;
	jacobian[(in*2+1)*numParam+2] = 0;
	
			
	//4
	jacobian[in*2*numParam+3] = 0;
	jacobian[(in*2+1)*numParam+3] = 1;
			
	for (i=0; i<numParam; i++){
		j0 = totalParams[0]*eigenVectors[in*2*numParam+i] - totalParams[1]*eigenVectors[(in*2+1)*numParam+i];
		j1 = totalParams[0]*eigenVectors[(in*2+1)*numParam+i] + totalParams[1]*eigenVectors[in*2*numParam+i];
		jacobian[in*2*numParam+4+i] = j0;
		jacobian[(in*2+1)*numParam+4+i] = j1;
	}

}

void __attribute__((kernel)) calcJtj(uint32_t in, uint32_t x, uint32_t y) {
	int i=0; 
	float result = 0;
		
	for (i=0; i<numPts*2; i++) {
		result += jacobian[i*numParam+x]*jacobian[i*numParam+y];		
	}
	
	jtj[x*numParam+y] = result;
}

void LUDecomp(){
	int i=0;
	int j=0;
	int k=0;
	
	for (i=0; i<numParam; i++) {
		for (j=0; j<numParam; j++) {
			Ujtj[i*numParam+j] = jtj[i*numParam+j];
			if (i==j){
				Ljtj[i*numParam+j] = 1;
			}
		}
	}
	
	for (i=0; i<numParam-1; i++){
		for (j=(i+1); j<numParam; j++) {
			float factor = Ujtj[j*numParam+i] / Ujtj[i*numParam+i];
			Ljtj[j*numParam+i] = factor;
			Ujtj[j*numParam+i] = 0;
			
			for (k=j; k<numParam; k++) {
				Ujtj[j*numParam+k] -= Ujtj[i*numParam+k]*factor;
			}
		}
	}
}


void __attribute__((kernel)) calcResult(uint32_t in) {
	int i=0;
	int j=0;
	float result = 0;
	
	for (i=0; i<numPts*2; i++) {
		float partial = 0;
		for (j=0; j<numParam; j++) {
			partial += invJtj[in*numParam+j]*jacobian[i*numPts*2+j];
		}
		
		result += partial*deltaPos[i];
	}
	
	deltaParams[in] = result;
}
