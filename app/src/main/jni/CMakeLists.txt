# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

cmake_minimum_required(VERSION 3.18.0)

project(lyraime VERSION 5.5.0)

# for reproducible build
add_link_options("LINKER:--hash-style=both,--build-id=none")

set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake" ${CMAKE_MODULE_PATH})

# general options
option(BUILD_SHARED_LIBS "" OFF)
option(BUILD_TESTING "" OFF)

option(SKIP_RIME_TARGETS "Skip building Rime native targets (provided by cache)" OFF)
option(SKIP_LUA_THEME_TARGETS "Skip building Lua theme native targets (provided by cache)" OFF)
option(SKIP_SHERPA_TARGETS "Skip building Sherpa native targets (provided by cache)" OFF)

set(_NATIVE_CACHE_BT "")
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
  set(_NATIVE_CACHE_BT "debug")
elseif(CMAKE_BUILD_TYPE STREQUAL "Release" OR CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo" OR CMAKE_BUILD_TYPE STREQUAL "MinSizeRel")
  set(_NATIVE_CACHE_BT "release")
endif()

if(NOT _NATIVE_CACHE_BT STREQUAL "")
  set(_NCS "${CMAKE_CURRENT_SOURCE_DIR}/.native-cache/${_NATIVE_CACHE_BT}/NativeCacheState.cmake")
  if(EXISTS "${_NCS}")
    include("${_NCS}")
  endif()
endif()

# ---- shared: yaml-cpp (used by both rime and lua theme) ----
if(NOT SKIP_RIME_TARGETS OR NOT SKIP_LUA_THEME_TARGETS)
  option(YAML_CPP_BUILD_TOOLS "" OFF)
  option(YAML_CPP_BUILD_TESTS "" OFF)
  option(YAML_CPP_INSTALL "" OFF)
  add_subdirectory(yaml-cpp)
endif()

# ---- lua55 (lua theme) ----
if(NOT SKIP_LUA_THEME_TARGETS)
  file(GLOB LUA55_SOURCES "${CMAKE_SOURCE_DIR}/lua55/*.c")
  list(FILTER LUA55_SOURCES EXCLUDE REGEX "/(lua|luac)\\.c$")
  add_library(lua55 STATIC ${LUA55_SOURCES})
  target_include_directories(lua55 PUBLIC "${CMAKE_SOURCE_DIR}/lua55")
endif()

# ---- rime (glog, snappy, leveldb, marisa, opencc, boost, rime, librime_jni) ----
if(NOT SKIP_RIME_TARGETS)
  include(Boost)

  option(WITH_GFLAGS "Use gflags" OFF)
  option(WITH_GTEST "Use googletest" OFF)
  option(WITH_UNWIND "Enable libunwind support" OFF)
  # Force-disable unwind for NDK r29 compatibility.
  set(WITH_UNWIND none CACHE STRING "" FORCE)
  add_subdirectory(librime/deps/glog)
  target_compile_options(
    glog_internal PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")

  # enable compress for leveldb
  option(SNAPPY_BUILD_TESTS "" OFF)
  option(SNAPPY_BUILD_BENCHMARKS "" OFF)
  option(SNAPPY_INSTALL "" OFF)
  add_subdirectory(librime-plugins/snappy)

  set(HAVE_SNAPPY TRUE)
  option(LEVELDB_BUILD_TESTS "" OFF)
  option(LEVELDB_BUILD_BENCHMARKS "" OFF)
  option(LEVELDB_INSTALL "" OFF)
  add_subdirectory(librime-plugins/leveldb)

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
endif()

# ---- lua_theme_jni ----
if(NOT SKIP_LUA_THEME_TARGETS)
  add_subdirectory(lua_theme_jni)
endif()

# ---- sherpa-onnx (voice ASR with optional QNN) ----
# Download the onnxruntime shared libs up front (outside the sherpa guard) so
# that libonnxruntime.so is always available locally for pre-packaging into the
# APK, even when the sherpa native targets are skipped by the native cache.
include(OnnxRuntime)

# Point sherpa-onnx's cmake module to the downloaded onnxruntime shared libs
set(ENV{SHERPA_ONNXRUNTIME_LIB_DIR} "${SHERPA_ONNXRUNTIME_LIB_DIR}")
set(ENV{SHERPA_ONNXRUNTIME_INCLUDE_DIR} "${SHERPA_ONNXRUNTIME_INCLUDE_DIR}")

if(NOT SKIP_SHERPA_TARGETS)
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
endif()
