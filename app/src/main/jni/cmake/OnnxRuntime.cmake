# Download pre-built onnxruntime shared libraries for Android.
# Uses SHERPA_ONNX_ONNXRUNTIME_VERSION from outer scope (default 1.27.0).
# Downloads the shared package which includes the full set of headers
# (onnxruntime_cxx_api.h, nnapi_provider_factory.h, etc.).

if(NOT DEFINED SHERPA_ONNX_ONNXRUNTIME_VERSION)
  set(SHERPA_ONNX_ONNXRUNTIME_VERSION "1.29.0")
endif()

message(STATUS "ONNXRUNTIME_VERSION: ${SHERPA_ONNX_ONNXRUNTIME_VERSION}")

if(CMAKE_ANDROID_ARCH_ABI STREQUAL arm64-v8a)
  set(ONNXRUNTIME_ANDROID_ABI "arm64-v8a")
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL x86_64)
  set(ONNXRUNTIME_ANDROID_ABI "x86_64")
else()
  message(FATAL_ERROR "Unsupported Android ABI: ${CMAKE_ANDROID_ARCH_ABI}")
endif()

set(ONNXRUNTIME_URL "https://github.com/csukuangfj/onnxruntime-libs/releases/download/v${SHERPA_ONNX_ONNXRUNTIME_VERSION}/onnxruntime-android-${SHERPA_ONNX_ONNXRUNTIME_VERSION}.zip")
set(ONNXRUNTIME_DIR "${CMAKE_CURRENT_BINARY_DIR}/onnxruntime-android-${SHERPA_ONNX_ONNXRUNTIME_VERSION}")
set(ONNXRUNTIME_ZIP "onnxruntime-android-${SHERPA_ONNX_ONNXRUNTIME_VERSION}.zip")

if(NOT EXISTS "${ONNXRUNTIME_ZIP}")
  message(STATUS "Downloading onnxruntime from ${ONNXRUNTIME_URL}")
  file(DOWNLOAD "${ONNXRUNTIME_URL}" "${ONNXRUNTIME_ZIP}"
       EXPECTED_HASH
          SHA256=a78f303a26b5e75c84c8b2a97fa2ddb400b2d1b5e069bec19aa229ccd3597fdb
       SHOW_PROGRESS STATUS download_status)
  list(GET download_status 0 status_code)
  if(NOT status_code EQUAL 0)
    file(REMOVE "${ONNXRUNTIME_ZIP}")
    message(FATAL_ERROR "Failed to download onnxruntime: ${ONNXRUNTIME_URL}")
  endif()
endif()

if(NOT EXISTS "${ONNXRUNTIME_DIR}/jni/${ONNXRUNTIME_ANDROID_ABI}/libonnxruntime.so")
  message(STATUS "Extracting onnxruntime from cached zip")
  file(ARCHIVE_EXTRACT INPUT "${ONNXRUNTIME_ZIP}" DESTINATION "${ONNXRUNTIME_DIR}")

  if(NOT EXISTS "${ONNXRUNTIME_DIR}/jni/${ONNXRUNTIME_ANDROID_ABI}/libonnxruntime.so")
    file(GLOB_RECURSE found_libs "${ONNXRUNTIME_DIR}/libonnxruntime.so")
    list(LENGTH found_libs count)
    if(count EQUAL 0)
      message(FATAL_ERROR "libonnxruntime.so not found in ${ONNXRUNTIME_DIR}")
    endif()
  endif()

  message(STATUS "onnxruntime extracted to ${ONNXRUNTIME_DIR}")
endif()

set(SHERPA_ONNXRUNTIME_LIB_DIR "${ONNXRUNTIME_DIR}/jni/${ONNXRUNTIME_ANDROID_ABI}" CACHE PATH "" FORCE)
set(SHERPA_ONNXRUNTIME_INCLUDE_DIR "${ONNXRUNTIME_DIR}/headers" CACHE PATH "" FORCE)
