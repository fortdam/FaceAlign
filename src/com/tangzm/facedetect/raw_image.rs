#pragma version(1)
#pragma rs java_package_name(com.tangzm.facedetect)

int rotate; //counter-clockwise rotate
int mirror;

int oWidth;  //of original byte buffer
int oHeight;  //of original byte buffer
int oSize; //Mandatory for NV21

int mWidth;   //of mirrored byte buffer
int mHeight;   //of mirrored byte buffer

int sWidth; //of scaled width (result)
int sHeight;  //of scaled height (result)



//The type of buffer, currently support input of RGB888,RGB565,GRAYSCALE8,NV21, 
// output of RGB888,RGB565,GRAYSCALE8
// 8888: ARGB8888
// 888:  RGB888
// 565:  RGB565
// 8:    GrayScale8
// 21:   NV21

int inType;
int outType;

uchar *input;
uchar *output;

//Process: Original byte buffer=>Rotate=>Mirror=>Scale(result)

static uchar4 rgb565_to_argb(uchar2 in) {
	uchar4 out;
	
	out.r = in.y & 0xf8;
	out.g = (in.y & 0x07) << 5 + (in.x >> 3) & 0x1c;
	out.b = (in.x << 3) & 0xf8;
	out.a = 0xff;
	
	return out;
}

static uchar2 argb_to_rgb565(uchar4 in) {
    uchar2 out;
    
    out.x = ((in.b >> 3) & 0x1f) | ((in.g << 3) & 0xe0);
    out.y = (in.r & 0xf8) | ((in.g >> 5) & 0x07);
    
    return out;
}

static uchar argb_to_grayscale(uchar4 in) {
	return (in.r*0.3 + in.g*0.59 + in.b*0.11);
}

static uchar4 yuv_to_argb(uchar y, char u, char v){
	uchar4 out;
	
	int r = y + (1.772f*v);
    int g = y - (0.344f*v + 0.714f*u);
    int b = y + (1.402f*u);
    
    out.r = r>255? 255 : r<0 ? 0 : r;
    out.g = g>255? 255 : g<0 ? 0 : g;
    out.b = b>255? 255 : b<0 ? 0 : b;
    out.a = 0xff;
    
    return out;
}

void __attribute__((kernel)) process(uint32_t sY) {

	//indexed by height
	int sX=0;	
	
		 
	for (sX=0; sX<sWidth; sX++) {
	
	    //The sequence of coordinte transform is Orig=>Rotate*=>Mirror=>Scale
	    //So the back-propagation is Scaled=>Mirrored=>Rotated*=>Orinate
	
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
	    int outIndex = sY*sWidth+sX;
		
		if (21 == inType) {
			if (8 == outType){
				output[outIndex] = input[inIndex];
			}
			else {
				char3 yuv;
				uchar4 argb;
				
				int uvoffset = ((oY/2) * ((oWidth+1)/2) + oX/2)*2;
				
				argb = yuv_to_argb(input[inIndex], 
				                    input[uvoffset + oSize + 1] - 128,
				                    input[uvoffset + oSize] - 128);
				
				if (565 == outType) {
				    uchar2 out = argb_to_rgb565(argb);
				
				    output[outIndex*2] = out.x;
				    output[outIndex*2+1] = out.y;
				}
				else if (888 == outType) {
					output[outIndex*3] = argb.r;
					output[outIndex*3+1] = argb.g;
					output[outIndex*3+2] = argb.b;
				}
				else if (8888 == outType) {
					output[outIndex*4] = argb.a;
					output[outIndex*4+1] = argb.r;
					output[outIndex*4+2] = argb.g;
					output[outIndex*4+3] = argb.b;
				}
			}

		}
		else if (8888 == inType) {
			uchar4 argb;
			
			argb.a = input[inIndex*4];
			argb.r = input[inIndex*4+1];
			argb.g = input[inIndex*4+2];
			argb.b = input[inIndex*4+3];
			
			if (8 == outType){
				output[outIndex] = argb_to_grayscale(argb);
			}
			else if (565 == outType){
				uchar2 out = argb_to_rgb565(argb);
				output[outIndex*2] = out.x;
				output[outIndex*2+1] = out.y;
			}
			else if (888 == outType){
				output[outIndex*3] = argb.r;
				output[outIndex*3+1] = argb.g;
				output[outIndex*3+2] = argb.b;
			}
			else if (8888 == outType) {
				output[outIndex*4] = argb.a;
				output[outIndex*4+1] = argb.r;
				output[outIndex*4+2] = argb.g;
				output[outIndex*4+3] = argb.b;			}
		}		
		else if (888 == inType) {
			uchar4 argb;
			
			argb.a = 0xff;
			argb.r = input[inIndex*3];
			argb.g = input[inIndex*3+1];
			argb.b = input[inIndex*3+2];
			
			if (8 == outType){
				output[outIndex] = argb_to_grayscale(argb);
			}
			else if (565 == outType){
				uchar2 out = argb_to_rgb565(argb);
				output[outIndex*2] = out.x;
				output[outIndex*2+1] = out.y;
			}
			else if (888 == outType){
				output[outIndex*3] = argb.r;
				output[outIndex*3+1] = argb.g;
				output[outIndex*3+2] = argb.b;
			}
			else if (8888 == outType) {
				output[outIndex*4] = argb.a;
				output[outIndex*4+1] = argb.r;
				output[outIndex*4+2] = argb.g;
				output[outIndex*4+3] = argb.b;			}
		}
		else if (565 == inType) {
			uchar2 rgb565;
			rgb565.x = input[inIndex*2];
			rgb565.y = input[inIndex*2+1];
			
			if (8 == outType){
				output[outIndex] = argb_to_grayscale(rgb565_to_argb(rgb565));
			}
			else if (565 == outType){
				output[outIndex*2] = rgb565.x;
				output[outIndex*2+1] = rgb565.y;
			}
			else if (888 == outType){
				uchar4 argb = rgb565_to_argb(rgb565);
				output[outIndex*3] = argb.r;
				output[outIndex*3+1] = argb.g;
				output[outIndex*3+2] = argb.b;
			}			
			else if (8888 == outType){
				uchar4 argb = rgb565_to_argb(rgb565);
				output[outIndex*4] = argb.a;
				output[outIndex*4+1] = argb.r;
				output[outIndex*4+2] = argb.g;
				output[outIndex*4+3] = argb.b;
			}
		}
		else if (8 == inType) {
			if (8 == outType){
				output[outIndex] = input[inIndex];
			}
			else if (565 == outType){
				uchar4 argb;
				uchar2 out;
				argb.r = argb.g = argb.b = input[inIndex];
				argb.a = 0xff;
				out = argb_to_rgb565(argb);
				output[outIndex*2] = out.x;
				output[outIndex*2+1] = out.y;
			}
			else if (888 == outType){
				output[outIndex*3] = input[inIndex];
				output[outIndex*3 + 1] = input[inIndex];
				output[outIndex*3 + 2] = input[inIndex];
			}
			else if (8888 == outType){
				output[outIndex*4] = input[inIndex];
				output[outIndex*4+1] = input[inIndex];
				output[outIndex*4+2] = input[inIndex];
				output[outIndex*4+3] = input[inIndex];
			}
		}
	}
}