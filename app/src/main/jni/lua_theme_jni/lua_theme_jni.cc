// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include <jni.h>

#include <string>

#include "lua_sandbox.h"

#define PACKAGE "com/osfans/trime/data/theme/LuaThemeBridge"

static void throwRuntime(JNIEnv* env, const std::string& msg) {
  jclass exClass = env->FindClass("java/lang/RuntimeException");
  if (exClass != nullptr) {
    env->ThrowNew(exClass, msg.c_str());
  }
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_osfans_trime_data_theme_LuaThemeBridge_nativeInit(
    JNIEnv* env, jclass, jstring themesDir, jstring userThemesDir) {
  const char* dir = env->GetStringUTFChars(themesDir, nullptr);
  const char* userDir = env->GetStringUTFChars(userThemesDir, nullptr);
  bool ok = lua_theme::LuaThemeEngine::instance().init(dir, userDir);
  env->ReleaseStringUTFChars(userThemesDir, userDir);
  env->ReleaseStringUTFChars(themesDir, dir);
  if (!ok) {
    throwRuntime(env, "Failed to initialize Lua sandbox");
  }
}

JNIEXPORT jstring JNICALL
Java_com_osfans_trime_data_theme_LuaThemeBridge_nativeLoadTheme(JNIEnv* env,
                                                                jclass,
                                                                jstring path) {
  const char* p = env->GetStringUTFChars(path, nullptr);
  std::string json = lua_theme::LuaThemeEngine::instance().loadTheme(p);
  env->ReleaseStringUTFChars(path, p);

  if (json.find("{\"error\":") == 0) {
    throwRuntime(env, json);
    return nullptr;
  }
  return env->NewStringUTF(json.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_osfans_trime_data_theme_LuaThemeBridge_nativeLoadSoundEffect(
    JNIEnv* env, jclass, jstring path) {
  const char* p = env->GetStringUTFChars(path, nullptr);
  std::string json = lua_theme::LuaThemeEngine::instance().loadSoundEffect(p);
  env->ReleaseStringUTFChars(path, p);

  if (json.find("{\"error\":") == 0) {
    throwRuntime(env, json);
    return nullptr;
  }
  return env->NewStringUTF(json.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_osfans_trime_data_theme_LuaThemeBridge_nativeParseYaml(
    JNIEnv* env, jclass, jstring path, jstring keyPath) {
  const char* p = env->GetStringUTFChars(path, nullptr);
  const char* kp = env->GetStringUTFChars(keyPath, nullptr);
  std::string json = lua_theme::LuaThemeEngine::instance().parseYaml(p, kp);
  env->ReleaseStringUTFChars(keyPath, kp);
  env->ReleaseStringUTFChars(path, p);

  if (json.find("{\"error\":") == 0) {
    throwRuntime(env, json);
    return nullptr;
  }
  return env->NewStringUTF(json.c_str());
}

JNIEXPORT void JNICALL
Java_com_osfans_trime_data_theme_LuaThemeBridge_nativeDestroy(JNIEnv*, jclass) {
  lua_theme::LuaThemeEngine::instance().destroy();
}

}  // extern "C"
