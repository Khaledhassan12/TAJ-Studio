# Install script for directory: C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml

# Set the install prefix
if(NOT DEFINED CMAKE_INSTALL_PREFIX)
  set(CMAKE_INSTALL_PREFIX "C:/Program Files (x86)/taj_ai_native")
endif()
string(REGEX REPLACE "/$" "" CMAKE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}")

# Set the install configuration name.
if(NOT DEFINED CMAKE_INSTALL_CONFIG_NAME)
  if(BUILD_TYPE)
    string(REGEX REPLACE "^[^A-Za-z0-9_]+" ""
           CMAKE_INSTALL_CONFIG_NAME "${BUILD_TYPE}")
  else()
    set(CMAKE_INSTALL_CONFIG_NAME "Debug")
  endif()
  message(STATUS "Install configuration: \"${CMAKE_INSTALL_CONFIG_NAME}\"")
endif()

# Set the component getting installed.
if(NOT CMAKE_INSTALL_COMPONENT)
  if(COMPONENT)
    message(STATUS "Install component: \"${COMPONENT}\"")
    set(CMAKE_INSTALL_COMPONENT "${COMPONENT}")
  else()
    set(CMAKE_INSTALL_COMPONENT)
  endif()
endif()

# Install shared libraries without execute permission?
if(NOT DEFINED CMAKE_INSTALL_SO_NO_EXE)
  set(CMAKE_INSTALL_SO_NO_EXE "0")
endif()

# Is this installation the result of a crosscompile?
if(NOT DEFINED CMAKE_CROSSCOMPILING)
  set(CMAKE_CROSSCOMPILING "TRUE")
endif()

# Set default install directory permissions.
if(NOT DEFINED CMAKE_OBJDUMP)
  set(CMAKE_OBJDUMP "C:/Users/khale/AppData/Local/Android/Sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-objdump.exe")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("C:/Users/khale/StudioProjects/TAJ-Studio/app/.cxx/Debug/164cr2w5/arm64-v8a/llama_cpp/ggml/src/cmake_install.cmake")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE STATIC_LIBRARY FILES "C:/Users/khale/StudioProjects/TAJ-Studio/app/.cxx/Debug/164cr2w5/arm64-v8a/llama_cpp/ggml/src/libggml.a")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include" TYPE FILE FILES
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml-cpu.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml-alloc.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml-backend.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml-blas.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml-cann.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml-cuda.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml-kompute.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml-opt.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml-metal.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml-rpc.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml-sycl.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/ggml-vulkan.h"
    "C:/Users/khale/StudioProjects/TAJ-Studio/app/src/main/cpp/llama_cpp/ggml/include/gguf.h"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE STATIC_LIBRARY FILES "C:/Users/khale/StudioProjects/TAJ-Studio/app/.cxx/Debug/164cr2w5/arm64-v8a/llama_cpp/ggml/src/libggml-base.a")
endif()

