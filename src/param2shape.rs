#pragma version(1)
#pragma rs java_package_name(com.tangzm.imagefacedetector)

float transX;
float transY;
float alphaCos;
float alphaSin;

int numParams;
int numPts;

float* params;
float* positions;
float* meanShape;
float* eigenVectors;

void __attribute__((kernel)) param2shape(uint32_t in) {
    int i=0;
    float a = 0;
    float b = 0;
    
    for (i=0; i<numParams; i++){
        a += eigenVectors[in*numParams*2+i] * params[i];
        b += eigenVectors[(in*2+1)*numParams+i] * params[i];
    }

	positions[i*2] = alphaCos*a - alphaSin*b + transX;
	positions[i*2+1] = alphaSin*a + alphaCos*b + transY;
} 

