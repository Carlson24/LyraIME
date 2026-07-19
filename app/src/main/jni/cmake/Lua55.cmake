# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

set(LUA_VERSION 5.5.0)

if(NOT EXISTS "lua-${LUA_VERSION}.tar.gz")
  message(STATUS "Downloading Lua ${LUA_VERSION} ......")
  file(
    DOWNLOAD
    "https://lua.org/ftp/lua-${LUA_VERSION}.tar.gz"
    lua-${LUA_VERSION}.tar.gz
    EXPECTED_HASH
      SHA256=57ccc32bbbd005cab75bcc52444052535af691789dba2b9016d5c50640d68b3d
    SHOW_PROGRESS)

  message(STATUS "Remove older Lua build tree")
  file(REMOVE_RECURSE "${CMAKE_SOURCE_DIR}/lua55")
endif()

if(NOT EXISTS "${CMAKE_SOURCE_DIR}/lua55")
  message(STATUS "Extracting Lua ${LUA_VERSION} ......")
  file(ARCHIVE_EXTRACT INPUT lua-${LUA_VERSION}.tar.gz DESTINATION
       ${CMAKE_SOURCE_DIR})
  file(RENAME "lua-${LUA_VERSION}" lua55)
endif()
