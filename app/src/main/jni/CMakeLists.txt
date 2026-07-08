# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

cmake_minimum_required(VERSION 3.18.0)

project(trime VERSION 3.0.0)

# for reproducible build
add_link_options("LINKER:--hash-style=both,--build-id=none")

set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake" ${CMAKE_MODULE_PATH})

# general options
option(BUILD_SHARED_LIBS "" OFF)
option(BUILD_TESTING "" OFF)

include(Boost)

option(WITH_GFLAGS "Use gflags" OFF)
option(WITH_GTEST "Use googletest" OFF)
option(WITH_UNWIND "Enable libunwind support" OFF)
# Force-disable unwind for NDK r29 compatibility.
# glog's FindUnwind.cmake picks up the host libunwind.so on NDK r29,
# causing "incompatible with aarch64linux" linker errors.
# Setting WITH_UNWIND=none tells glog to skip find_package(Unwind).
set(WITH_UNWIND none CACHE STRING "" FORCE)
add_subdirectory(librime/deps/glog)
target_compile_options(
  glog_internal PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")

option(YAML_CPP_BUILD_TOOLS "" OFF)
option(YAML_CPP_BUILD_TESTS "" OFF)
option(YAML_CPP_INSTALL "" OFF)
add_subdirectory(yaml-cpp)

# enable compress for leveldb
option(SNAPPY_BUILD_TESTS "" OFF)
option(SNAPPY_BUILD_BENCHMARKS "" OFF)
option(SNAPPY_INSTALL "" OFF)
add_subdirectory(snappy)

set(HAVE_SNAPPY TRUE)
option(LEVELDB_BUILD_TESTS "" OFF)
option(LEVELDB_BUILD_BENCHMARKS "" OFF)
option(LEVELDB_INSTALL "" OFF)
add_subdirectory(leveldb)

add_subdirectory(librime/deps/marisa-trie)
target_compile_options(
  marisa PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")

set(LIBMARISA marisa)
option(USE_SYSTEM_MARISA "" ON)
add_subdirectory(OpenCC)
include(OpenccWorkarounds)

set(X11Keysym "${CMAKE_SOURCE_DIR}/librime/include")
include_directories("${X11Keysym}")

include(Rime)

add_subdirectory(librime_jni)

# ---- sherpa-onnx (voice ASR with optional QNN) ----
include(DownloadOnnxRuntime)

# Point sherpa-onnx's cmake module to the downloaded onnxruntime shared libs
set(ENV{SHERPA_ONNXRUNTIME_LIB_DIR} "${SHERPA_ONNXRUNTIME_LIB_DIR}")
set(ENV{SHERPA_ONNXRUNTIME_INCLUDE_DIR} "${SHERPA_ONNXRUNTIME_INCLUDE_DIR}")

set(SHERPA_ONNX_ENABLE_PYTHON OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_CHECK OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_PORTAUDIO OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_WEBSOCKET OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_GPU OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_BINARY OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_TTS OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_JNI ON CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_C_API ON CACHE BOOL "" FORCE)
set(SHERPA_ONNX_LINK_LIBSTDCPP_STATICALLY ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)

if(CMAKE_ANDROID_ARCH_ABI STREQUAL arm64-v8a AND (DEFINED ENV{QNN_SDK_ROOT} OR NOT QNN_SDK_ROOT STREQUAL ""))
  if(NOT DEFINED ENV{QNN_SDK_ROOT} AND NOT QNN_SDK_ROOT STREQUAL "")
    set(ENV{QNN_SDK_ROOT} "${QNN_SDK_ROOT}")
  endif()
  set(SHERPA_ONNX_ENABLE_QNN ON CACHE BOOL "" FORCE)
else()
  set(SHERPA_ONNX_ENABLE_QNN OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(sherpa-onnx)
