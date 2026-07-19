// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <mutex>
#include <string>

extern "C" {
#include <lua.h>
}

namespace lua_theme {

class LuaThemeEngine {
 public:
  static LuaThemeEngine& instance();

  LuaThemeEngine(LuaThemeEngine const&) = delete;
  void operator=(LuaThemeEngine const&) = delete;

  bool init(const std::string& themes_dir);
  void destroy();

  std::string loadTheme(const std::string& path);
  std::string loadSoundEffect(const std::string& path);
  std::string parseYaml(const std::string& path, const std::string& key_path);

  bool isInitialized() const { return L != nullptr; }

 private:
  LuaThemeEngine() = default;
  ~LuaThemeEngine();

  void setupSandbox(const std::string& themes_dir);
  void injectApi();
  void buildSearchPath(const std::string& themes_dir);

  lua_State* L = nullptr;
  mutable std::mutex mutex_;
};

}  // namespace lua_theme
