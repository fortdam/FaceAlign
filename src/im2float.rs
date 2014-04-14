#pragma version(1)
#pragma rs java_package_name(com.tangzm.imagefacedetector)

void root(const uchar4 *v_in, uchar *v_out, const void *usrData, uint32_t x, uint32_t y) {
	*v_out = v_in->r*0.3 + v_in->g*0.59 + v_in->b*0.11;
}