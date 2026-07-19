// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include "lua_sandbox.h"

#include <dirent.h>
#include <sys/stat.h>

#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "lua_json.h"

extern "C" {
#include <lauxlib.h>
#include <lualib.h>
}

namespace lua_theme {

// ---------------------------------------------------------------------------
// Singleton
// ---------------------------------------------------------------------------

LuaThemeEngine& LuaThemeEngine::instance() {
  static LuaThemeEngine engine;
  return engine;
}

LuaThemeEngine::~LuaThemeEngine() { destroy(); }

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

bool LuaThemeEngine::init(const std::string& themes_dir) {
  std::lock_guard<std::mutex> lock(mutex_);
  if (L != nullptr) return true;

  L = luaL_newstate();
  if (L == nullptr) return false;

  setupSandbox(themes_dir);
  injectApi();
  buildSearchPath(themes_dir);
  return true;
}

void LuaThemeEngine::destroy() {
  std::lock_guard<std::mutex> lock(mutex_);
  if (L != nullptr) {
    lua_close(L);
    L = nullptr;
  }
}

// ---------------------------------------------------------------------------
// Sandbox setup
// ---------------------------------------------------------------------------

void LuaThemeEngine::setupSandbox(const std::string& themes_dir) {
  luaL_openlibs(L);

  // --- Remove entire unsafe libraries ---
  lua_pushnil(L);
  lua_setglobal(L, "io");
  lua_pushnil(L);
  lua_setglobal(L, "debug");

  // --- Remove unsafe global functions ---
  lua_pushnil(L);
  lua_setglobal(L, "dofile");
  lua_pushnil(L);
  lua_setglobal(L, "loadfile");

  // --- Sanitize os library ---
  lua_getglobal(L, "os");
  if (lua_istable(L, -1)) {
    const char* unsafe[] = {"execute", "exit",    "remove",
                            "rename",  "tmpname", nullptr};
    for (int i = 0; unsafe[i] != nullptr; i++) {
      lua_pushnil(L);
      lua_setfield(L, -2, unsafe[i]);
    }
  }
  lua_pop(L, 1);

  // --- Sanitize package library ---
  lua_getglobal(L, "package");
  if (lua_istable(L, -1)) {
    lua_pushnil(L);
    lua_setfield(L, -2, "loadlib");
    // Keep package.preload and package.searchers for module loading
  }
  lua_pop(L, 1);

  lua_getglobal(L, "require");
  lua_setglobal(L, "safe_require");
  lua_pushnil(L);
  lua_setglobal(L, "require");
}

// ---------------------------------------------------------------------------
// Functional API injection
// ---------------------------------------------------------------------------

namespace {

int l_identity(lua_State* L) {
  luaL_checktype(L, 1, LUA_TTABLE);
  return 1;
}

int l_scheme(lua_State* L) {
  const char* id = luaL_checkstring(L, 1);
  luaL_checktype(L, 2, LUA_TTABLE);
  lua_newtable(L);
  lua_pushstring(L, id);
  lua_setfield(L, -2, "id");
  lua_pushvalue(L, 2);
  lua_setfield(L, -2, "colors");
  return 1;
}

int l_pk(lua_State* L) {
  luaL_checkstring(L, 1);
  luaL_checktype(L, 2, LUA_TTABLE);
  lua_pushvalue(L, 2);
  return 1;
}

int l_merge(lua_State* L) {
  luaL_checktype(L, 1, LUA_TTABLE);
  luaL_checktype(L, 2, LUA_TTABLE);
  lua_newtable(L);

  lua_pushnil(L);
  while (lua_next(L, 1)) {
    lua_pushvalue(L, -2);
    lua_pushvalue(L, -2);
    lua_settable(L, -5);
    lua_pop(L, 1);
  }

  lua_pushnil(L);
  while (lua_next(L, 2)) {
    lua_pushvalue(L, -2);
    lua_gettable(L, 3);
    int both_tables = lua_istable(L, -1) && lua_istable(L, -2);
    lua_pop(L, 1);

    lua_pushvalue(L, -2);
    if (both_tables) {
      lua_getglobal(L, "merge");
      lua_pushvalue(L, -3);
      lua_gettable(L, 3);
      lua_pushvalue(L, -4);
      lua_call(L, 2, 1);
    } else {
      lua_pushvalue(L, -2);
    }
    lua_settable(L, 3);
    lua_pop(L, 1);
  }

  return 1;
}

int l_insert(lua_State* L) {
  luaL_checktype(L, 1, LUA_TTABLE);
  int pos = static_cast<int>(luaL_checkinteger(L, 2));
  luaL_checkany(L, 3);

  int n = 0;
  lua_pushnil(L);
  while (lua_next(L, 1)) {
    n++;
    lua_pop(L, 1);
  }

  lua_newtable(L);
  for (int i = 1; i < pos && i <= n; i++) {
    lua_geti(L, 1, i);
    lua_seti(L, -2, i);
  }
  lua_pushvalue(L, 3);
  lua_seti(L, -2, pos);
  for (int i = pos; i <= n; i++) {
    lua_geti(L, 1, i);
    lua_seti(L, -2, i + 1);
  }
  return 1;
}

}  // namespace

void LuaThemeEngine::injectApi() {
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "style");
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "theme");
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "keyboard");
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "row");
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "key");
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "fallback");
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "preedit");
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "window");
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "toolbar");
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "btn");
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "bg");
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "fg");
  lua_pushcfunction(L, l_identity);
  lua_setglobal(L, "liquid");
  lua_pushcfunction(L, l_scheme);
  lua_setglobal(L, "scheme");
  lua_pushcfunction(L, l_pk);
  lua_setglobal(L, "pk");
  lua_pushcfunction(L, l_merge);
  lua_setglobal(L, "merge");
  lua_pushcfunction(L, l_insert);
  lua_setglobal(L, "insert");
}

// ---------------------------------------------------------------------------
// Search path builder (recursive walk of themes/lib/)
// ---------------------------------------------------------------------------

namespace {

void collect_search_paths(const std::string& abs_dir,
                          std::vector<std::string>& paths) {
  paths.push_back(abs_dir);

  DIR* d = opendir(abs_dir.c_str());
  if (!d) return;

  struct dirent* entry;
  while ((entry = readdir(d)) != nullptr) {
    if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0)
      continue;
    std::string full = abs_dir + "/" + entry->d_name;
    struct stat st;
    if (stat(full.c_str(), &st) != 0) continue;
    if (S_ISDIR(st.st_mode)) {
      collect_search_paths(full, paths);
    }
  }
  closedir(d);
}

}  // namespace

void LuaThemeEngine::buildSearchPath(const std::string& themes_dir) {
  std::string lib_dir = themes_dir + "/lib";

  std::vector<std::string> dirs;
  collect_search_paths(lib_dir, dirs);

  std::string path;
  for (const auto& d : dirs) {
    if (!path.empty()) path += ';';
    path += d + "/?.lua";
    path += ';';
    path += d + "/?/init.lua";
  }

  if (!path.empty()) {
    lua_getglobal(L, "package");
    lua_pushstring(L, path.c_str());
    lua_setfield(L, -2, "path");
    lua_pop(L, 1);
  }
}

// ---------------------------------------------------------------------------
// Load & execute Lua file → JSON
// ---------------------------------------------------------------------------

namespace {

std::string load_and_serialize(lua_State* L, const std::string& path) {
  int status = luaL_loadfile(L, path.c_str());
  if (status != LUA_OK) {
    std::string err = lua_tostring(L, -1);
    lua_pop(L, 1);
    return "{\"error\":\"load: " + err + "\"}";
  }

  status = lua_pcall(L, 0, 1, 0);
  if (status != LUA_OK) {
    std::string err = lua_tostring(L, -1);
    lua_pop(L, 1);
    return "{\"error\":\"exec: " + err + "\"}";
  }

  if (lua_gettop(L) < 1 || lua_isnil(L, -1)) {
    return "{}";
  }

  std::string json = lua_to_json(L, -1);
  lua_pop(L, 1);
  return json;
}

}  // namespace

std::string LuaThemeEngine::loadTheme(const std::string& path) {
  std::lock_guard<std::mutex> lock(mutex_);
  if (L == nullptr) return "{\"error\":\"not initialized\"}";
  return load_and_serialize(L, path);
}

std::string LuaThemeEngine::loadSoundEffect(const std::string& path) {
  std::lock_guard<std::mutex> lock(mutex_);
  if (L == nullptr) return "{\"error\":\"not initialized\"}";
  return load_and_serialize(L, path);
}

// ---------------------------------------------------------------------------
// YAML parsing via yaml-cpp
// ---------------------------------------------------------------------------

std::string LuaThemeEngine::parseYaml(const std::string& path,
                                      const std::string& key_path) {
  std::lock_guard<std::mutex> lock(mutex_);
  try {
    YAML::Node root = YAML::LoadFile(path);
    if (!root) return "null";

    YAML::Node current = root;
    if (!key_path.empty()) {
      std::string remaining = key_path;
      size_t pos;
      while ((pos = remaining.find('.')) != std::string::npos) {
        std::string key = remaining.substr(0, pos);
        remaining = remaining.substr(pos + 1);
        if (!current[key]) return "null";
        current = current[key];
      }
      if (!current[remaining]) return "null";
      current = current[remaining];
    }

    return yaml_to_json(current);
  } catch (const YAML::Exception& e) {
    return "{\"error\":\"yaml: " + std::string(e.what()) + "\"}";
  }
}

}  // namespace lua_theme
