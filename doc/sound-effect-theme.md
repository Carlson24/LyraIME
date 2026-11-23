<!--
SPDX-FileCopyrightText: 2015 - 2026 Rime community
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 音效主题制作指南

音效主题用于自定义按键按下时播放的声音。一个音效主题由两部分组成：

- 一个 `.sound.lua` 描述文件；
- 若干音频文件。

描述文件会在 Lua 沙箱中执行，它的返回值会被转换为音效配置。音效主题放在用户数据目录中即可使用，不需要重新编译 APK。

## 快速开始

1. 打开用户数据目录。

   默认目录是 `Documents/LyraIME`，调试版是 `Documents/LyraIMEDebug`。如果你在设置中修改过“用户文件夹”，请使用修改后的目录。

2. 创建音效目录：

   ```text
   Documents/LyraIME/themes/soundeffect/
   ```

3. 在 `themes/soundeffect/` 下创建描述文件 `my.sound.lua`，并准备音频文件：

   ```text
   Documents/LyraIME/
   └── themes/
       └── soundeffect/
           ├── my.sound.lua
           └── my/
               └── click.ogg
   ```

   描述文件内容：

   ```lua
   return {
       name = "我的音效",
       folder = "my",
       sound = { "click.ogg" },
   }
   ```

4. 在输入法设置中启用：

   - 设置 → 键盘 → 开启“按键声音”；
   - 开启“使用自定义按键音效”；
   - 点击“自定义按键音效”，选择“我的音效”；
   - 确认“按键音量”不为 0。当前版本的默认音量是 10%，必要时可以调高。

如果没有开启“使用自定义按键音效”，输入法会使用系统按键音，而不是你制作的音效。

## 目录与命名

### 推荐目录结构

```text
<用户数据目录>/
└── themes/
    └── soundeffect/
        ├── typewriter.sound.lua
        ├── typewriter/
        │   ├── key_1.ogg
        │   ├── key_2.ogg
        │   ├── key_3.ogg
        │   ├── space.ogg
        │   ├── enter.ogg
        │   └── backspace.ogg
        └── melody.sound.lua
```

`folder` 表示音频所在的子目录，相对于 `themes/soundeffect/`。它不是从描述文件名自动推导出来的；如果音频放在子目录中，需要显式设置 `folder`。

如果不需要子目录，也可以把音频和描述文件放在同一层：

```text
themes/soundeffect/
├── simple.sound.lua
└── click.ogg
```

此时 `folder` 留空或省略即可。

### 文件规则

| 项目 | 规则 |
|---|---|
| 描述文件位置 | `<用户数据目录>/themes/soundeffect/` |
| 扫描方式 | 只扫描该目录的第一层，不会递归扫描子目录 |
| 文件后缀 | 必须是 `.sound.lua` |
| 默认显示名 | 文件名去掉 `.sound.lua`，例如 `my.sound.lua` 默认显示为 `my` |
| 音频位置 | `<用户数据目录>/themes/soundeffect/<folder>/<文件名>` |
| 兼容目录 | `<用户数据目录>/soundeffect/` 仍可作为回退目录读取 |
| 旧版目录 | 旧的 `<用户数据目录>/sound/` 会被尝试迁移到 `soundeffect/` |

注意：

- `my.sound.lua` 可以正常加载；`my.lua`、`my.sound.yaml` 不会被当作音效主题读取。
- 描述文件必须直接放在 `themes/soundeffect/` 下，放在子目录中不会出现在音效选择列表里。
- 音频文件名区分大小写，`Click.ogg` 和 `click.ogg` 是两个不同的文件。
- 建议使用 ASCII 文件名，例如字母、数字和下划线，避免路径和编码问题。
- 请避免多个描述文件使用相同的 `name`。音效选择框按名称查找音效，重名时行为不明确。

## 描述文件格式

`.sound.lua` 不是键盘主题文件，不需要调用 `theme()`、`style()`、`keyboard()` 等全局函数。它只需要在顶层返回一个 Lua table：

```lua
return {
    name = "示例音效",
    folder = "example",
    sound = { "a.ogg", "b.ogg" },
}
```

描述文件在 Lua 沙箱中执行，返回值会先转换为 JSON，再按照 `SoundEffect` 数据结构解析。字段名使用 `snake_case`。

Lua 沙箱提供基础的 Lua 库，但禁用了 `io`、`debug`、`dofile` 和 `loadfile`。如果需要复用自己写的 Lua 模块，可以把模块放在 `themes/lib/` 下，并使用 `safe_require` 加载；普通的 `require` 在沙箱中不可用。描述文件没有返回值或返回 `nil` 时会被当作空配置，不会产生声音。

### 顶层字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `name` | string | `""` | 显示名称。为空时使用文件名去掉 `.sound.lua` 后的部分 |
| `sound` | string[] | `[]` | 音频文件名列表，索引从 0 开始 |
| `folder` | string | `""` | 音频子目录，相对于 `themes/soundeffect/` |
| `melody` | string[] | `[]` | 按键旋律，按顺序循环播放，优先级高于 `keyset` |
| `keyset` | table[] | `[]` | 按键到音效的映射规则列表 |

### `keyset` 字段

`keyset` 是一个规则列表，每一项描述一组按键应该播放哪些声音。

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `min` | string | `"UNKNOWN"` | Android 键名，范围下限 |
| `max` | string | `"UNKNOWN"` | Android 键名，范围上限 |
| `keys` | string[] | `[]` | 明确指定一组按键，优先于 `min` 和 `max` |
| `in_order` | boolean | `false` | 是否按顺序使用 `sounds`；为 `false` 时随机选择 |
| `sounds` | int[] | `[]` | 指向 `sound` 列表的索引，索引从 0 开始 |

### 字段名注意事项

`in_order` 必须写成下划线形式。内部使用 SnakeCase 命名策略，写成 `inOrder` 会被忽略，并回退为默认值 `false`：

```lua
-- 正确
local correct = { keys = { "KEYCODE_A" }, in_order = true, sounds = { 0, 1, 2 } }

-- 错误：inOrder 不会生效
local wrong = { keys = { "KEYCODE_A" }, inOrder = true, sounds = { 0, 1, 2 } }
```

## 音效匹配与播放规则

### 基本流程

每次按键按下时，当前音效按照以下顺序选择要播放的声音：

1. 如果 `sound` 为空，不播放声音；
2. 如果 `melody` 非空，按 `melody` 的顺序播放；
3. 否则，从上到下遍历 `keyset`，使用第一个匹配成功的规则；
4. 如果没有任何规则匹配，播放 `sound[0]`。

`melody` 的优先级高于 `keyset`。只要设置了 `melody`，`keyset` 就不会参与选择，除非 `melody` 为空。

### `melody`：按键旋律

`melody` 是一个音频文件名列表，每次按键播放其中一项，然后前进到下一项。播放进度在多次按键之间保持，输入视图关闭时重置。

```lua
return {
    name = "小星星",
    folder = "twinkle",
    sound = { "do.ogg", "re.ogg", "mi.ogg", "fa.ogg", "sol.ogg", "la.ogg" },
    melody = {
        "do.ogg", "do.ogg", "sol.ogg", "sol.ogg",
        "la.ogg", "la.ogg", "sol.ogg",
    },
}
```

注意：

- `melody` 中写的是文件名，不是 `sound` 的索引；
- 每个条目必须和 `sound` 中的某个字符串完全一致，否则该次按键不会发声，但进度仍会继续前进；
- 如果 `melody` 只有一项，每次按键都会播放同一个声音。

### `keyset`：按键映射

`keyset` 中的规则按书写顺序匹配，第一个匹配成功的规则生效。因此，特殊按键规则应放在通用规则前面。

```lua
return {
    name = "打字机",
    folder = "typewriter",
    sound = { "key.ogg", "space.ogg", "enter.ogg", "backspace.ogg" },
    keyset = {
        { keys = { "KEYCODE_SPACE" }, sounds = { 1 } },
        { keys = { "KEYCODE_ENTER" }, sounds = { 2 } },
        { keys = { "KEYCODE_DEL" }, sounds = { 3 } },
        { min = "KEYCODE_A", max = "KEYCODE_Z", in_order = true, sounds = { 0 } },
    },
}
```

#### 键名格式

`keys`、`min` 和 `max` 使用 Android `KeyEvent` 的键名，必须带有 `KEYCODE_` 前缀。

| 按键 | Android 键名 |
|---|---|
| 字母 A | `KEYCODE_A` |
| 数字 0 | `KEYCODE_0` |
| 空格 | `KEYCODE_SPACE` |
| 回车 | `KEYCODE_ENTER` |
| 退格 | `KEYCODE_DEL` |
| 删除 | `KEYCODE_FORWARD_DEL` |
| 制表 | `KEYCODE_TAB` |
| 左 Shift | `KEYCODE_SHIFT_LEFT` |
| 右 Shift | `KEYCODE_SHIFT_RIGHT` |
| 方向上 | `KEYCODE_DPAD_UP` |
| 方向下 | `KEYCODE_DPAD_DOWN` |
| 方向左 | `KEYCODE_DPAD_LEFT` |
| 方向右 | `KEYCODE_DPAD_RIGHT` |

当前实现会先把键名转换为大写，因此大小写不敏感；但建议统一使用大写。使用 `space`、`Return`、`BackSpace` 等 Rime 键名不会匹配，必须写成对应的 Android 键名，例如 `KEYCODE_SPACE`、`KEYCODE_ENTER`、`KEYCODE_DEL`。

完整的键名列表可以参考 Android 官方文档中的 [`KeyEvent`](https://developer.android.com/reference/android/view/KeyEvent) 常量。

#### `keys` 与 `min` / `max`

- `keys` 非空时，只使用 `keys` 精确匹配，`min` 和 `max` 会被忽略。
- `keys` 为空时，使用 `min` 到 `max` 的闭区间匹配。
- `min` 和 `max` 也必须是合法的 Android 键名。
- 如果 `min` 大于 `max`，该规则不会匹配任何按键。
- 键名无效时会被解析为 `KEYCODE_UNKNOWN`（0），通常不会匹配真实按键。

当按键范围在 Android 键码中是连续的时候，可以使用范围写法。例如字母 A 到 Z、数字 0 到 9 都是连续的：

```lua
local rule = { min = "KEYCODE_A", max = "KEYCODE_Z", in_order = true, sounds = { 0, 1, 2 } }
```

如果按键组在键码上不连续，建议改用 `keys` 明确列出，避免匹配到无关按键。

#### `sounds` 索引

`sounds` 中的数字是 `sound` 列表的索引，从 0 开始：

```lua
local sound = { "a.ogg", "b.ogg", "c.ogg" }
-- sounds = { 0, 2 } 表示使用 a.ogg 和 c.ogg
```

注意：

- `sounds` 为空时，该规则会被跳过；
- 索引必须大于等于 0，并且小于 `sound` 的长度，不能越界；越界可能导致按键时出现运行时异常；
- 如果需要多个声音，重复写索引即可，例如 `{ 0, 0, 1 }`。

#### `in_order`：顺序还是随机

- `in_order = true`：按下 `keys` 中的某个键时，使用 `sounds[该键在 keys 中的位置(从 0 开始) % #sounds]`；
- `in_order = true` 且使用 `min`/`max` 范围时，使用 `sounds[(键码 - min) % #sounds]`；
- `in_order = false` 或省略：每次从 `sounds` 中随机选择一个。

例如，下面的规则让 A、B、C 依次使用三种声音：

```lua
local rule = {
    keys = { "KEYCODE_A", "KEYCODE_B", "KEYCODE_C" },
    in_order = true,
    sounds = { 0, 1, 2 },
}
```

### 播放条件与缓存

自定义音效只有在以下条件同时满足时才会播放：

- 输入法设置中的“按键声音”已开启；
- “使用自定义按键音效”已开启；
- “按键音量”大于 0；
- 当前选择了一个有效的音效主题。

播放使用 Android `SoundPool`，最多同时播放 3 个音频流。快速连续按键时，声音可能互相截断或只播放其中一部分，因此建议使用短音效。

音频文件会按路径缓存。如果只替换了同名的音频文件而路径没有变化，需要重启应用或让输入法服务重新创建后才能刷新；仅重新选择音效通常不会重新读取同路径的音频内容。新增描述文件后，通常重新打开音效选择框即可看到；如果仍未出现，请检查文件后缀和日志。

## 示例

### 示例一：所有按键使用同一个声音

```lua
-- themes/soundeffect/simple.sound.lua
return {
    name = "单音",
    folder = "simple",
    sound = { "click.ogg" },
}
```

没有 `melody` 和 `keyset` 时，所有按键都会播放 `sound[0]`，也就是这里的 `click.ogg`。

对应的目录：

```text
themes/soundeffect/
├── simple.sound.lua
└── simple/
    └── click.ogg
```

### 示例二：不同按键使用不同声音

```lua
-- themes/soundeffect/typewriter.sound.lua
local letters = {
    "KEYCODE_A", "KEYCODE_B", "KEYCODE_C", "KEYCODE_D",
    "KEYCODE_E", "KEYCODE_F", "KEYCODE_G", "KEYCODE_H",
    "KEYCODE_I", "KEYCODE_J", "KEYCODE_K", "KEYCODE_L",
    "KEYCODE_M", "KEYCODE_N", "KEYCODE_O", "KEYCODE_P",
    "KEYCODE_Q", "KEYCODE_R", "KEYCODE_S", "KEYCODE_T",
    "KEYCODE_U", "KEYCODE_V", "KEYCODE_W", "KEYCODE_X",
    "KEYCODE_Y", "KEYCODE_Z",
}

return {
    name = "打字机",
    folder = "typewriter",
    sound = {
        "key_1.ogg",
        "key_2.ogg",
        "key_3.ogg",
        "space.ogg",
        "enter.ogg",
        "backspace.ogg",
    },
    keyset = {
        { keys = { "KEYCODE_SPACE" }, sounds = { 3 } },
        { keys = { "KEYCODE_ENTER" }, sounds = { 4 } },
        { keys = { "KEYCODE_DEL" }, sounds = { 5 } },
        {
            keys = letters,
            in_order = true,
            sounds = { 0, 1, 2 },
        },
    },
}
```

这段配置的效果是：

- 空格播放 `space.ogg`；
- 回车播放 `enter.ogg`；
- 退格播放 `backspace.ogg`；
- 字母 A 到 Z 按顺序在 `key_1.ogg`、`key_2.ogg`、`key_3.ogg` 之间循环；
- 其他没有匹配的按键播放 `sound[0]`，也就是 `key_1.ogg`。

### 示例三：按键旋律

```lua
-- themes/soundeffect/twinkle.sound.lua
return {
    name = "小星星",
    folder = "twinkle",
    sound = {
        "do.ogg",
        "re.ogg",
        "mi.ogg",
        "fa.ogg",
        "sol.ogg",
        "la.ogg",
    },
    melody = {
        "do.ogg", "do.ogg", "sol.ogg", "sol.ogg",
        "la.ogg", "la.ogg", "sol.ogg",
    },
}
```

每按一次键播放旋律中的下一个音，输入视图关闭后从头开始。

### 示例四：用 Lua 生成声音列表

```lua
-- themes/soundeffect/scale.sound.lua
local sound = {}
for i = 1, 8 do
    sound[i] = string.format("tone_%d.ogg", i)
end

return {
    name = "音阶",
    folder = "scale",
    sound = sound,
    melody = sound,
}
```

文件对应关系：

```text
themes/soundeffect/
├── scale.sound.lua
└── scale/
    ├── tone_1.ogg
    ├── tone_2.ogg
    ├── tone_3.ogg
    ├── tone_4.ogg
    ├── tone_5.ogg
    ├── tone_6.ogg
    ├── tone_7.ogg
    └── tone_8.ogg
```

## 音频文件建议

- 推荐使用 OGG 或 WAV；MP3、AAC 等 Android 常见格式通常也可以使用。
- 推荐单声道、44.1 kHz、16 bit。
- 单个音效尽量控制在 1 秒以内，避免快速输入时声音重叠严重。
- 文件不要过大。`SoundPool` 会把音频加载到内存中，过大的文件可能导致加载失败。
- 音频内容建议在开头直接进入声音主体，减少静音前奏。
- 确保 `sound` 和 `melody` 中引用的每个文件都真实存在，并且路径大小写正确。

## 启用与调试

### 启用音效主题

1. 打开输入法设置；
2. 进入“键盘”；
3. 开启“按键声音”；
4. 开启“使用自定义按键音效”；
5. 点击“自定义按键音效”，选择你制作的音效；
6. 确认“按键音量”不是 0。

如果当前键盘主题的工具栏绑定了 `sound` 动作，也可以直接通过该按钮打开音效选择框。

### 查看日志

描述文件解析失败时，该音效会被跳过，不会出现在选择列表中。可以通过日志查看原因：

```bash
adb logcat | grep -i SoundEffectManager
```

常见日志包括 Lua 语法错误、返回值不是 table、JSON 解析失败等。

### 常见问题

| 现象 | 排查方向 |
|---|---|
| 音效选择列表中没有新主题 | 确认文件以 `.sound.lua` 结尾，且直接位于 `themes/soundeffect/` 下；检查 Lua 语法和日志 |
| 列表中有主题但按键无声 | 检查是否开启“使用自定义按键音效”、音量是否为 0、音频文件路径和文件名是否正确 |
| 只有部分按键有声音 | 检查 `keyset` 规则顺序、`keys` 键名是否带 `KEYCODE_` 前缀，以及 `min`/`max` 是否覆盖目标按键 |
| 播放的声音和预期不一致 | 记住 `melody` 优先于 `keyset`；检查是否有更靠前的规则先匹配了该按键 |
| 修改 `in_order` 后没有变化 | 确认写的是 `in_order`，而不是 `inOrder` |
| 修改音频文件后声音没变 | 重启应用或让输入法服务重新创建；路径相同的音频会使用旧缓存，仅重新选择音效通常无效 |
| 之前选择过的音效突然失效 | 检查是否修改了 `name`；当前选择保存的是名称字符串，改名后需要重新选择 |
| 按键时应用异常 | 检查 `sounds` 中是否存在超出 `sound` 长度的索引 |
| 选择框中有重复项 | 检查多个描述文件是否使用了相同的 `name`，建议保持名称唯一 |

## 兼容性与实现说明

- 推荐使用 `<用户数据目录>/themes/soundeffect/`。
- 旧的 `<用户数据目录>/soundeffect/` 仍会作为回退目录读取。
- 旧的 `<用户数据目录>/sound/` 会被尝试迁移到 `soundeffect/`；如果仍有旧文件，建议手动整理到新目录。
- 搜索音频时，会先找 `themes/soundeffect/<folder>/<文件名>`，找不到时再找 `soundeffect/<folder>/<文件名>`。
- 当前实现只扫描 `.sound.lua`，旧版 YAML 音效配置不会再被加载。
- `name` 会作为当前选择的持久化值使用；修改名称后，需要在设置中重新选择。

相关源码：

- [`SoundEffect.kt`](../app/src/main/java/com/osfans/trime/data/soundeffect/SoundEffect.kt)：音效数据结构与按键匹配逻辑
- [`SoundEffectManager.kt`](../app/src/main/java/com/osfans/trime/data/soundeffect/SoundEffectManager.kt)：扫描、解析、选择音效
- [`InputFeedbackManager.kt`](../app/src/main/java/com/osfans/trime/ime/keyboard/InputFeedbackManager.kt)：`SoundPool` 加载与播放
- [`lua_sandbox.cc`](../app/src/main/jni/lua_theme_jni/lua_sandbox.cc)：Lua 沙箱和描述文件执行

## 制作检查清单

- [ ] 描述文件以 `.sound.lua` 结尾，并且位于 `themes/soundeffect/` 第一层
- [ ] 顶层使用了 `return { ... }`
- [ ] `name` 唯一，且没有和已有音效重名
- [ ] `folder` 指向正确的音频子目录
- [ ] `sound` 中的每个文件名都能找到对应音频
- [ ] `melody` 中的每个名称都出现在 `sound` 中
- [ ] `keyset` 中的 `sounds` 索引都在有效范围内
- [ ] 按键名使用 `KEYCODE_` 前缀
- [ ] 顺序播放使用 `in_order`，没有写成 `inOrder`
- [ ] 已在设置中开启“按键声音”和“使用自定义按键音效”
- [ ] 已在音效选择框中选中该主题
- [ ] 日志中没有 `SoundEffectManager` 相关的解析错误
