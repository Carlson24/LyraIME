// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <string>

extern "C" {
#include <lua.h>
}

#include <yaml-cpp/yaml.h>

namespace lua_theme {

std::string lua_to_json(lua_State* L, int index);

std::string yaml_to_json(const YAML::Node& node);

}  // namespace lua_theme
