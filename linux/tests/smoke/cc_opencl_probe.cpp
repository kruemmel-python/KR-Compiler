#include <iostream>

extern "C" int initialize_gpu(int gpu_index);
extern "C" void shutdown_gpu(int gpu_index);
extern "C" const char* cc_get_last_error(void);

int main() {
    const int result = initialize_gpu(0);
    if (result != 0) {
        shutdown_gpu(0);
        std::cout << "KR-CC-OPENCL-GPU-OK\n";
        return 0;
    }
    const char* error = cc_get_last_error();
    std::cout << "KR-CC-OPENCL-NO-DEVICE: " << (error ? error : "unknown") << "\n";
    return 0;
}
