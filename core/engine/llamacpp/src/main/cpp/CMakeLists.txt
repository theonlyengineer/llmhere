cmake_minimum_required(VERSION 3.22.1)
project(edgellm_jni CXX)

set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../third_party/llama.cpp")

# Build llama.cpp as a subdirectory (skip common/examples/tests/tools)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)

# OpenCL backend (Qualcomm Adreno GPU) dependencies.
# llama.cpp's ggml-opencl calls find_package(OpenCL REQUIRED). The NDK ships no
# OpenCL headers and no link library, so we provide both:
#   - headers via Khronos OpenCL-Headers (FetchContent)
#   - link library via the device's own libOpenCL.so (vendored under opencl/).
# At runtime the app loads the system /vendor/lib64/libOpenCL.so (it is listed in
# the device's public.libraries.txt), so this .so is a link-time reference only
# and is NOT packaged into the APK.
if(GGML_OPENCL)
    include(FetchContent)
    FetchContent_Declare(
        opencl_headers
        GIT_REPOSITORY https://github.com/KhronosGroup/OpenCL-Headers.git
        GIT_TAG        v2024.10.24
        GIT_SHALLOW    TRUE
    )
    FetchContent_MakeAvailable(opencl_headers)
    FetchContent_GetProperties(opencl_headers SOURCE_DIR _opencl_headers_src)

    # Satisfy find_package(OpenCL): the include dir must contain CL/cl.h
    set(OpenCL_INCLUDE_DIR "${_opencl_headers_src}" CACHE PATH "" FORCE)
    set(OpenCL_LIBRARY "${CMAKE_CURRENT_SOURCE_DIR}/opencl/libOpenCL.so" CACHE FILEPATH "" FORCE)
endif()

add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

# Our JNI bridge library
add_library(edgellm_jni SHARED edgellm_jni.cpp)

target_include_directories(edgellm_jni PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
)

target_link_libraries(edgellm_jni
    llama
    log
)

target_compile_options(edgellm_jni PRIVATE
    -Wall -Wextra -Wpedantic
    -fexceptions
)
