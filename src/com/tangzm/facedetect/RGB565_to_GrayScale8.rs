#pragma version(1)
#pragma rs java_package_name(com.tangzm.facedetect)

int rotate; //counter-clockwise rotate
int mirror;

int oWidth;  //of original byte buffer
int oHeight;  //of original byte buffer

int mWidth;   //of mirrored byte buffer
int mHeight;   //of mirrored byte buffer

int sWidth; //of scaled width (result)
int sHeight;  //of scaled height (result)

uchar *input;
uchar *output;

//Process: Original byte buffer=>Rotate=>Mirror=>Scale(result)

void __attribute__((kernel)) process(uint32_t sY) {

	//indexed by height
	int sX=0;	
	
		 
	for (sX=0; sX<sWidth; sX++) {
	
		//Back-propagation to mX & mY
		int mX = (mWidth*sX)/sWidth;
		int mY = (mHeight*sY)/sHeight;
		
		//Back-propagation to rX & rY
		int rX = mX;
		
		if (mirror){
			rX = mWidth - mX; //should be (rWidth-mX)
		}
		int rY = mY; //initial value of rY
		
		//Back-propagation to oX & oY
		int rRotate = rotate;
		int rWidth = mWidth;
		int rHeight = mHeight;
		
	    while (rRotate >= 90) {
	    	int temp = rY;
	    	rY = rX;
	    	rX= rHeight - temp;
	    	
	    	temp = rWidth;
	    	rWidth = rHeight;
	    	rHeight = temp;
	    	
	    	rRotate -= 90;
	    }
	    
	    int oX = rX;
	    int oY = rY;
	    int inIndex = oX + oY*oWidth;
		
		uchar r = input[inIndex*2+1] & 0xf8;
		uchar g = (input[inIndex*2+1] & 0x07) << 5 + (input[inIndex*2] >> 3) & 0x1c;
		uchar b = (input[inIndex*2] << 3) & 0xf8;
			
		output[sY*sWidth+sX] = r*0.3+g*0.59+b*0.11;
	}
}