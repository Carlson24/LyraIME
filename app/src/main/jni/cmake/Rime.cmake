# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

# if you want to add some new plugins, add them to librime_jni/rime_jni.cc too
set(RIME_PLUGINS librime-lua librime-witogram librime-user-predict librime-calculator)

# symlink plugins
foreach(plugin ${RIME_PLUGINS})
  if(NOT EXISTS "${CMAKE_SOURCE_DIR}/librime/plugins/${plugin}")
    file(CREATE_LINK "${CMAKE_SOURCE_DIR}/librime-plugins/${plugin}"
         "${CMAKE_SOURCE_DIR}/librime/plugins/${plugin}" COPY_ON_ERROR SYMBOLIC)
  endif()
endforeach()

# librime-lua
if(NOT EXISTS "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/thirdparty")
  file(CREATE_LINK "${CMAKE_SOURCE_DIR}/librime-plugins/librime-lua-deps"
       "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/thirdparty"
       COPY_ON_ERROR SYMBOLIC)
endif()

# abseil-cpp
if(NOT EXISTS "${CMAKE_SOURCE_DIR}/librime-plugins/librime-witogram/third_party/sentencepiece/third_party/abseil-cpp")
  file(CREATE_LINK "${CMAKE_SOURCE_DIR}/librime-plugins/abseil-cpp"
       "${CMAKE_SOURCE_DIR}/librime-plugins/librime-witogram/third_party/sentencepiece/third_party/abseil-cpp"
       COPY_ON_ERROR SYMBOLIC)
endif()
if(NOT EXISTS "${CMAKE_SOURCE_DIR}/librime-plugins/librime-witogram/third_party/sentencepiece/third_party/absl")
  file(CREATE_LINK "${CMAKE_SOURCE_DIR}/librime-plugins/abseil-cpp/absl"
       "${CMAKE_SOURCE_DIR}/librime-plugins/librime-witogram/third_party/sentencepiece/third_party/absl"
       COPY_ON_ERROR SYMBOLIC)
endif()
set(SPM_ABSL_PROVIDER "lyraime" CACHE STRING "Provided by LyraIME" FORCE)
set(ABSL_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(ABSL_PROPAGATE_CXX_STD ON CACHE BOOL "" FORCE)
add_subdirectory("${CMAKE_SOURCE_DIR}/librime-plugins/abseil-cpp"
                 "${CMAKE_BINARY_DIR}/abseil-cpp-build")


option(BUILD_TEST "" OFF)
option(BUILD_STATIC "" ON)
set(BUILD_TOOLS OFF CACHE BOOL "" FORCE)
add_compile_definitions(KENLM_MAX_ORDER=16)
add_subdirectory(librime)
target_compile_options(
  rime-static PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=." "-Wno-deprecated-declarations")

target_compile_options(
  rime-lua-objs PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")

target_compile_options(
  rime-witogram-objs PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
if(TARGET sentencepiece-static)
  file(STRINGS "${CMAKE_SOURCE_DIR}/librime-plugins/librime-witogram/third_party/sentencepiece/VERSION.txt" _spm_ver)
  target_compile_definitions(sentencepiece-static PRIVATE
                             PACKAGE_STRING="sentencepiece"
                             VERSION="${_spm_ver}"
                             INSTALL_DATADIR="/")
endif()

target_compile_options(
  rime-user-predict-objs PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")

target_compile_options(
  rime-calculator-objs PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
