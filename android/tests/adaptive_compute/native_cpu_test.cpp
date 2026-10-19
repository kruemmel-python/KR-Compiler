#include "kr_adaptive_compute.h"
#include <cmath>
#include <cstdio>
#include <string>

static bool near(float a,float b){return std::fabs(a-b)<1e-5f;}
int main(int argc,char**argv){
  kr_compute_set_mode(argc>2 ? KR_COMPUTE_VERIFY : KR_COMPUTE_CPU_ONLY);
  if(argc>1) kr_compute_set_telemetry_path(argv[1]);
  float a[4]={1,2,3,4},b[4]={5,6,7,8},o[4]={};
  if(!kr_compute_add_f32(a,b,o,4)||!near(o[0],6)||!near(o[3],12)) return 1;
  size_t shape[2]={2,2};
  if(!kr_compute_tensor_mul_f32(a,b,o,shape,2)||!near(o[2],21)) return 2;
  float m1[6]={1,2,3,4,5,6},m2[6]={7,8,9,10,11,12},mm[4]={};
  if(!kr_compute_matmul_f32(m1,m2,mm,1,2,2,3)||!near(mm[0],58)||!near(mm[3],154)) return 3;
  float sums[3]={};
  if(!kr_compute_reduce_sum_f32(m1,sums,1,2,3)||!near(sums[0],5)||!near(sums[1],7)||!near(sums[2],9)) return 4;
  float image[4]={1,2,3,4},scaled[4]={},scale[4]={2,3,4,5};
  if(!kr_compute_image_rgba_scale_f32(image,scaled,1,1,scale)||!near(scaled[3],20)) return 5;
  float input[9]={1,2,3,4,5,6,7,8,9},kernel[4]={1,0,0,1},conv[4]={};
  if(!kr_compute_conv2d_f32(input,kernel,nullptr,conv,1,1,3,3,1,2,2,1,1)||!near(conv[0],6)||!near(conv[3],14)) return 6;
  const kr_compute_telemetry* t=kr_compute_last_telemetry();
  if(!t||t->error_code) return 7;
  const std::string backend=t->backend?t->backend:"";
  if(argc>2 ? backend!="gpu" : backend!="cpu") {
    std::fprintf(stderr,"Unexpected final backend: %s\n",backend.c_str());
    return 7;
  }
  std::puts("Adaptive Compute CPU validation: PASS");
  return 0;
}
