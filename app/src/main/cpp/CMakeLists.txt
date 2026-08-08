cmake_minimum_required(VERSION 3.22.1)
project("taj_ai_native")

add_library(llama-jni SHARED llama_jni.cpp)

find_library(log-lib log)
target_link_libraries(llama-jni ${log-lib})
