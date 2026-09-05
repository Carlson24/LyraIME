# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

aux_source_directory(. LUA_THEME_JNI_SOURCES)
add_library(lua_theme_jni SHARED ${LUA_THEME_JNI_SOURCES})
target_link_libraries(lua_theme_jni lua55 yaml-cpp)
target_include_directories(lua_theme_jni PRIVATE
  "${CMAKE_SOURCE_DIR}/lua55"
  "${CMAKE_SOURCE_DIR}/yaml-cpp/include"
)
