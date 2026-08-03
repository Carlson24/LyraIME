// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include "lua_json.h"

#include <sstream>
#include <unordered_set>

extern "C" {
#include <lauxlib.h>
#include <lualib.h>
}

namespace lua_theme {

namespace {

constexpr int kMaxDepth = 128;

void json_escape(std::ostream& os, const std::string& s) {
  os << '"';
  for (unsigned char c : s) {
    switch (c) {
      case '"':
        os << "\\\"";
        break;
      case '\\':
        os << "\\\\";
        break;
      case '\b':
        os << "\\b";
        break;
      case '\f':
        os << "\\f";
        break;
      case '\n':
        os << "\\n";
        break;
      case '\r':
        os << "\\r";
        break;
      case '\t':
        os << "\\t";
        break;
      default:
        if (c < 0x20) {
          os << "\\u00" << std::hex << static_cast<int>(c) << std::dec;
        } else {
          os << c;
        }
        break;
    }
  }
  os << '"';
}

void lua_value_to_json(lua_State* L, int index, std::ostream& os,
                       int depth,
                       std::unordered_set<const void*>& visited) {
  if (depth > kMaxDepth) {
    os << "null";
    return;
  }

  int t = lua_type(L, index);
  switch (t) {
    case LUA_TNIL:
      os << "null";
      break;

    case LUA_TBOOLEAN:
      os << (lua_toboolean(L, index) ? "true" : "false");
      break;

    case LUA_TNUMBER:
      if (lua_isinteger(L, index)) {
        os << lua_tointeger(L, index);
      } else {
        os << lua_tonumber(L, index);
      }
      break;

    case LUA_TSTRING: {
      size_t len;
      const char* s = lua_tolstring(L, index, &len);
      json_escape(os, std::string(s, len));
      break;
    }

    case LUA_TTABLE: {
      const void* ptr = lua_topointer(L, index);
      if (!visited.insert(ptr).second) {
        os << "null";
        return;
      }

      bool is_array = true;
      int max_key = 0;

      lua_pushnil(L);
      while (lua_next(L, index < 0 ? index - 1 : index)) {
        if (lua_type(L, -2) == LUA_TNUMBER && lua_isinteger(L, -2)) {
          lua_Integer k = lua_tointeger(L, -2);
          if (k >= 1 && k > max_key) {
            max_key = static_cast<int>(k);
          }
        } else {
          is_array = false;
        }
        lua_pop(L, 1);

        if (!is_array && max_key > 0) break;
      }

      int count = 0;
      lua_pushnil(L);
      while (lua_next(L, index < 0 ? index - 1 : index)) {
        count++;
        lua_pop(L, 1);
      }

      if (is_array && count == max_key) {
        os << '[';
        for (int i = 1; i <= max_key; i++) {
          if (i > 1) os << ',';
          lua_geti(L, index, i);
          lua_value_to_json(L, -1, os, depth + 1, visited);
          lua_pop(L, 1);
        }
        os << ']';
      } else {
        os << '{';
        bool first = true;
        lua_pushnil(L);
        while (lua_next(L, index < 0 ? index - 1 : index)) {
          if (!first) os << ',';
          first = false;

          if (lua_type(L, -2) == LUA_TSTRING) {
            json_escape(os, lua_tostring(L, -2));
          } else if (lua_type(L, -2) == LUA_TNUMBER && lua_isinteger(L, -2)) {
            os << '"' << lua_tointeger(L, -2) << '"';
          } else {
            os << "\"\"";
          }
          os << ':';
          lua_value_to_json(L, -1, os, depth + 1, visited);
          lua_pop(L, 1);
        }
        os << '}';
      }

      visited.erase(ptr);
      break;
    }

    default:
      os << "null";
      break;
  }
}

void yaml_node_to_json(const YAML::Node& node, std::ostream& os,
                       int depth = 0) {
  if (depth > kMaxDepth) {
    os << "null";
    return;
  }

  switch (node.Type()) {
    case YAML::NodeType::Null:
      os << "null";
      break;

    case YAML::NodeType::Scalar: {
      std::string val;
      try {
        if (node.Tag() == "?") {
          val = node.as<std::string>();
          json_escape(os, val);
        } else {
          bool b;
          int i;
          double d;
          if (YAML::convert<bool>::decode(node, b)) {
            os << (b ? "true" : "false");
          } else if (YAML::convert<int>::decode(node, i)) {
            os << i;
          } else if (YAML::convert<double>::decode(node, d)) {
            os << d;
          } else {
            val = node.as<std::string>();
            json_escape(os, val);
          }
        }
      } catch (...) {
        val = node.as<std::string>();
        json_escape(os, val);
      }
      break;
    }

    case YAML::NodeType::Sequence: {
      os << '[';
      bool first = true;
      for (const auto& item : node) {
        if (!first) os << ',';
        first = false;
        yaml_node_to_json(item, os, depth + 1);
      }
      os << ']';
      break;
    }

    case YAML::NodeType::Map: {
      os << '{';
      bool first = true;
      for (const auto& kv : node) {
        if (!first) os << ',';
        first = false;
        json_escape(os, kv.first.as<std::string>());
        os << ':';
        yaml_node_to_json(kv.second, os, depth + 1);
      }
      os << '}';
      break;
    }

    case YAML::NodeType::Undefined:
      os << "null";
      break;
  }
}
}  // namespace

std::string lua_to_json(lua_State* L, int index) {
  std::ostringstream os;
  os << std::fixed;
  std::unordered_set<const void*> visited;
  lua_value_to_json(L, index, os, 0, visited);
  return os.str();
}

std::string yaml_to_json(const YAML::Node& node) {
  std::ostringstream os;
  os << std::fixed;
  yaml_node_to_json(node, os);
  return os.str();
}

}  // namespace lua_theme
