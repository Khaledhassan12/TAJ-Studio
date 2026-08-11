cmake_minimum_required(VERSION 3.22.1)
project("taj_ai_native")

# Disable llama.cpp extras we don't need
set(BUILD_SHARED_LIBS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_SERVER OFF)
set(GGML_BUILD_TESTS OFF)
set(GGML_BUILD_EXAMPLES OFF)

# Include vendored llama.cpp (tag b4539)
add_subdirectory(llama_cpp)

# Our JNI library
add_library(llama-jni SHARED llama_jni.cpp)
find_library(log-lib log)

target_include_directories(llama-jni PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/llama_cpp/include
        ${CMAKE_CURRENT_SOURCE_DIR}/llama_cpp/common
)

target_link_libraries(llama-jni
        llama
        ggml
        ${log-lib}
)