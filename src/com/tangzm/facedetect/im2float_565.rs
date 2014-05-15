#pragma version(1)
#pragma rs java_package_name(com.tangzm.facedetect)

void root(const uchar2 *v_in, uchar *v_out, const void *usrData, uint32_t x, uint32_t y) {
	uchar r = v_in->y & 0xf8;
	uchar g = (v_in->y & 0x07) << 5 + (v_in->x >> 3) & 0x1c;
	uchar b = (v_in->x << 3) & 0xf8;
	*v_out = r*0.3+g*0.59+b*0.11;
}