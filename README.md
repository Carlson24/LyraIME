<!--
SPDX-FileCopyrightText: 2015 - 2026 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# LyraIME — 灵韵输入法

> Rime IME for Android

[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub release](https://img.shields.io/github/release/Carlson24/LyraIME.svg)](https://github.com/Carlson24/LyraIME/releases)
[![Latest build](https://img.shields.io/github/last-commit/Carlson24/LyraIME.svg)](https://github.com/Carlson24/LyraIME)

## 关于

灵韵输入法（LyraIME）是一款基于 [RIME] 输入法框架的 Android 输入法平台，使用 JNI 的 C 语言和
Android 的 Java/Kotlin 语言开发。Fork 自[同文输入法（Trime）](https://github.com/osfans/trime)，
旨在保护汉语各地方言母语，音码、形码通用的输入法平台。

[主题 DSL 文档](doc/theme-dsl.lua)

## 特色功能

### 离线语音输入

基于 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 的本地语音识别引擎：

- 完全离线运行，无需网络连接，保护输入隐私
- 支持中文、英文等多种语言的语音识别
- 低延迟流式识别，边说边出字

### Qualcomm NPU 加速

在 Qualcomm Snapdragon 平台上，自动利用 NPU（QNN DSP）硬件加速语音识别：

- 运行时自动检测设备 SoC 型号，按需下载对应的 QNN HTP 运行时库
- 支持以下平台：

| HTP 版本 | SoC |
|----------|-----|
| V81 | SM8850 (Snapdragon 8 Gen 5) |
| V79 | SM8750 (Snapdragon 8 Gen 4) |
| V75 | SM8650 (Snapdragon 8 Gen 3) |
| V73 | SM8550 (Snapdragon 8 Gen 2) |
| V69 | SM8450 / SM8475 (Snapdragon 8 Gen 1 / 8+ Gen 1) |
| V68 | SM8350 (Snapdragon 888) |

- 非高通设备或 x86_64 模拟器自动回退至 CPU 推理，无需额外配置

### 强大自定义

- 高度可自定义的键盘布局与主题系统
- 灵活的多点触控手势操作
- 丰富的按键映射与快捷键支持
- 通过外部方案包按需导入 Rime 输入方案，无需预装大量码表

## 下载

> **注意**：APK 不内置 QNN DSP 库，首次使用 QNN 语音输入功能时会自动从 GitHub
> Releases 下载对应平台的 HTP 库。

- 稳定版 <br>
  [<img alt='Get it on F-Droid'
  src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png'
  height='80px'/>](https://f-droid.org/packages/com.carlson.lyraime)
  [<img alt='Google Play 立即下载'
  src='https://play.google.com/intl/en_us/badges/images/generic/zh-cn_badge_web_generic.png'
  height='80px'/>](https://play.google.com/store/apps/details?id=com.carlson.lyraime)

- 每夜版 [点击下载](https://github.com/Carlson24/LyraIME/releases)

## 开发入门

### 准备

#### 开发环境要求

- Android SDK 和 Android NDK 29.0.14206865
  - 如果还不熟悉 Android 开发，建议安装
    [Android Studio](https://developer.android.google.cn/studio)
- JDK（OpenJDK）17
- CMake 4.1.2
- Python 3（用于 OpenCC 生成词典文本文件）

#### Windows 上的前提条件

当前构建过程会创建符号链接，开发者需要：

- 启用[开发者模式](https://learn.microsoft.com/zh-cn/windows/apps/get-started/enable-your-device-for-development)
- 启用 `git` 的符号链接支持：

  ```powershell
  git config --global core.symlinks true
  ```

如果无法启用上述设置，构建系统会自动使用复制替代。

#### QNN SDK（可选，用于 NPU 加速）

如需编译带 Qualcomm NPU 加速的版本，需设置 QNN SDK 路径：

```bash
export QNN_SDK_ROOT=$HOME/.local/share/Android/qairt/2.48.40.260702
```

或在 `gradle.properties` 中设置：

```properties
qnnSdkRoot=/path/to/qairt/version
```

未设置时自动回退至 CPU-only 构建。QNN 仅支持 arm64-v8a。

### 构建

#### 1. 克隆并拉取子模块

```sh
git clone git@github.com:Carlson24/LyraIME.git
git submodule update --init --recursive
# 可使用部分克隆节约时间
git submodule update --init --recursive --filter=blob:none
```

#### 2. 编译调试版本

```sh
make debug
```

默认仅构建 arm64-v8a。如需构建 x86_64：

```sh
BUILD_ABI=x86_64 make debug
```

#### 3. 编译正式版本

创建 `keystore.properties` 文件，包含[签名信息](https://developer.android.com/studio/publish/app-signing.html)：

```properties
storePassword=myStorePassword
keyPassword=myKeyPassword
keyAlias=myKeyAlias
storeFile=myStoreFileLocation
# 或使用 Base64 编码的密钥文件
keyBase64=<base64-encoded-keystore>
```

然后执行：

```sh
make release
```

#### 4. 原生代码缓存

如果 `app/prebuilt/` 目录存在，构建时会复用预编译的 `.so`
文件，跳过原生编译。如需强制完整构建，删除该目录即可。

### 代码格式化

```bash
make style-lint    # 检查格式（Spotless + clang-format）
make style-apply   # 应用格式化
```

### 测试

```bash
./gradlew :app:test
```

### 故障排除

```
Target "boost_log_setup" links to target "Boost::coroutine" but the target was not found.
```

执行 `make clean`。

其他问题：

1. 首先尝试 `make clean`
2. 确保仓库与最新版本一致。修改了子模块，请确保它们兼容当前版本
3. 如果问题依然存在，尝试一次新的克隆
4. 检查是否有相关 issue/PR
5. 以上方法无效，可以提 issue 寻求帮助

## 鸣谢

本项目 Fork 自[同文输入法（Trime）](https://github.com/osfans/trime)，感谢上游项目的所有贡献者。

- 原项目开发：[osfans](https://github.com/osfans)
- 原项目贡献：
  [boboIqiqi](https://github.com/boboIqiqi)、
  [Bambooin](https://github.com/Bambooin)、
  [senchi96](https://github.com/senchi96)、
  [heiher](https://github.com/heiher)、
  [abay](https://github.com/a342191555)、
  [iovxw](https://github.com/iovxw)、
  [huyz-git](https://github.com/huyz-git)、
  [tumuyan](https://github.com/tumuyan)、
  [WhiredPlanck](https://github.com/WhiredPlanck)、
  [nopdan](https://github.com/nopdan)……
- [维基](https://github.com/osfans/trime/wiki)：
  [xiaoqun2016](https://github.com/xiaoqun2016)、
  [boboIqiqi](https://github.com/boboIqiqi)……
- 翻译：天真可爱的满满（繁体中文）、点解（英文）……
- 键盘设计：天真可爱的满满、皛筱晓小笨鱼、吴琛 11、熊猫阿 Bo、默默ㄇㄛ ˋ……
- 社区：在 [Issues](https://github.com/osfans/trime/issues)、
  [QQ 群 (811142286)](https://jq.qq.com/?_wv=1027&k=AXdR80HN)、
  [贴吧](http://tieba.baidu.com/f?kw=rime)、
  [Telegram](https://t.me/trime_dev)
  中反馈意见的网友
- 项目：[RIME]、[OpenCC]、
  [靓企鹅输入法](https://github.com/fxliang/fcitx5-android)（参考了文本编辑、悬浮键盘）、
  [简意输入法](https://github.com/danjian/fcitx5-android)（参考了 T9 输入、语音动画）
  等开源项目

## 第三方库

- [RIME](https://rime.im) (BSD License)
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache License 2.0)
- [onnxruntime](https://github.com/microsoft/onnxruntime) (MIT License)
- [OpenCC](https://github.com/BYVoid/OpenCC) (Apache License 2.0)
- [Boost C++ Libraries](https://www.boost.org/) (Boost Software License)
- [LevelDB](https://github.com/google/leveldb) (New BSD License)
- [marisa-trie](https://github.com/s-yata/marisa-trie) (BSD License)
- [snappy](https://github.com/google/snappy) (BSD License)
- [yaml-cpp](https://github.com/jbeder/yaml-cpp) (MIT License)
- [darts-clone](https://github.com/s-yata/darts-clone) (New BSD License)
- [glog](https://github.com/google/glog) (New BSD License)
- [utfcpp](https://github.com/nemtrif/utfcpp) (Boost Software License)

[RIME]: https://rime.im
[OpenCC]: https://github.com/BYVoid/OpenCC
