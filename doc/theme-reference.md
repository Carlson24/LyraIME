# LyraIME 主题 YAML 配置参考

所有可用选项的完整说明。

---

## 文件头

| YAML 键           | 类型       | 必填    | 说明                   |
|------------------|----------|-------|----------------------|
| `config_version` | `String` | 否     | Rime 配置版本号，如 `"3.0"` |
| `name`           | `String` | **是** | 主题名称                 |
| `author`         | `String` | 否     | 作者信息                 |

---

## `style` — GeneralStyle 全局样式

所有字段均有默认值，可选择性覆盖。

### 候选栏

| YAML 键                         | 类型             | 默认值   | 说明                                         |
|--------------------------------|----------------|-------|--------------------------------------------|
| `candidate_border`             | `Int`          | `0`   | 候选区边框宽度                                    |
| `candidate_border_round`       | `Float`        | `0`   | 候选区边框圆角                                    |
| `candidate_corner_radius`      | `Float`        | `5`   | 候选项圆角半径                                    |
| `candidate_font`               | `List<String>` | `[]`  | 候选字体文件列表                                   |
| `candidate_padding`            | `Int`          | `0`   | 候选项内边距                                     |
| `candidate_spacing`            | `Float`        | `0`   | 候选分割线宽度                                    |
| `candidate_text_size`          | `Float`        | `15`  | 候选字号                                       |
| `candidate_text_vertical_bias` | `Float`        | `1.0` | 候选文本垂直偏移（仅 overlay 模式，0.0=上, 0.5=中, 1.0=下） |
| `candidate_view_height`        | `Int`          | `28`  | 候选区高度                                      |

### 编码提示 (comment)

| YAML 键                  | 类型                | 默认值     | 说明                     |
|-------------------------|-------------------|---------|------------------------|
| `comment_font`          | `List<String>`    | `[]`    | 编码提示字体                 |
| `comment_height`        | `Int`             | `12`    | 编码提示区高度                |
| `comment_position`      | `CommentPosition` | `RIGHT` | 编码提示布局位置               |
| `comment_text_size`     | `Float`           | `10`    | 编码提示字号                 |
| `comment_vertical_bias` | `Float`           | `0`     | 编码提示垂直偏移（仅 overlay 模式） |

**`CommentPosition` 枚举值：** `RIGHT` | `TOP` | `OVERLAY`

### 剪贴板

| YAML 键                         | 类型             | 默认值  | 说明        |
|--------------------------------|----------------|------|-----------|
| `clipboard_font`               | `List<String>` | `[]` | 剪贴板字体     |
| `clipboard_category_font`      | `List<String>` | `[]` | 剪贴板分类标签字体 |
| `clipboard_category_text_size` | `Float`        | `13` | 剪贴板分类标签字号 |
| `clipboard_text_size`          | `Float`        | `14` | 剪贴板条目字号   |

### 键盘通用

| YAML 键                             | 类型        | 默认值     | 说明               |
|------------------------------------|-----------|---------|------------------|
| `keyboard_height`                  | `Int`     | `0`     | 竖屏键盘高度锁定值        |
| `keyboard_height_land`             | `Int`     | `0`     | 横屏键盘高度锁定值        |
| `horizontal_gap`                   | `Int`     | `0`     | 按键水平间距           |
| `vertical_gap`                     | `Int`     | `0`     | 按键垂直间距（行距）       |
| `round_corner`                     | `Float`   | `0`     | 按键圆角半径           |
| `shadow_radius`                    | `Float`   | `0`     | 按键阴影半径           |
| `key_border`                       | `Int`     | `0`     | 按键边框宽度           |
| `keyboard_padding`                 | `Int`     | `0`     | 竖屏键盘左右边距         |
| `keyboard_padding_bottom`          | `Int`     | `0`     | 竖屏键盘下边距          |
| `keyboard_padding_top`             | `Int`     | `0`     | 竖屏键盘上边距          |
| `keyboard_padding_land`            | `Int`     | `0`     | 横屏键盘左右边距         |
| `keyboard_padding_land_bottom`     | `Int`     | `0`     | 横屏键盘下边距          |
| `auto_caps`                        | `Boolean` | `false` | 自动句首大写           |
| `reset_ascii_mode_on_focus_change` | `Boolean` | `false` | 焦点变更时重置 ASCII 模式 |

### 按键字体

| YAML 键        | 类型             | 默认值  | 说明     |
|---------------|----------------|------|--------|
| `key_font`    | `List<String>` | `[]` | 按键主字体  |
| `hanb_font`   | `List<String>` | `[]` | 扩展汉字字体 |
| `latin_font`  | `List<String>` | `[]` | 西文字体   |
| `symbol_font` | `List<String>` | `[]` | 符号字体   |
| `label_font`  | `List<String>` | `[]` | 标签字体   |
| `hint_font`   | `List<String>` | `[]` | 助记提示字体 |
| `text_font`   | `List<String>` | `[]` | 编码文本字体 |

### 按键字号

| YAML 键               | 类型      | 默认值  | 说明      |
|----------------------|---------|------|---------|
| `key_text_size`      | `Float` | `15` | 按键文字字号  |
| `key_long_text_size` | `Float` | `15` | 长标签按键字号 |
| `symbol_text_size`   | `Float` | `0`  | 符号字号    |
| `label_text_size`    | `Float` | `0`  | 标签字号    |
| `hint_text_size`     | `Float` | `0`  | 助记提示字号  |

### 按键文字/符号/提示偏移

| YAML 键                | 类型      | 默认值 | 说明        |
|-----------------------|---------|-----|-----------|
| `key_text_offset_x`   | `Float` | `0` | 按键文字横向偏移  |
| `key_text_offset_y`   | `Float` | `0` | 按键文字纵向偏移  |
| `key_symbol_offset_x` | `Float` | `0` | 按键符号横向偏移  |
| `key_symbol_offset_y` | `Float` | `0` | 按键符号纵向偏移  |
| `key_hint_offset_x`   | `Float` | `0` | 按键提示横向偏移  |
| `key_hint_offset_y`   | `Float` | `0` | 按键提示纵向偏移  |
| `key_press_offset_x`  | `Float` | `0` | 按键按下时横向偏移 |
| `key_press_offset_y`  | `Float` | `0` | 按键按下时纵向偏移 |

### 弹窗 (popup)

| YAML 键                | 类型             | 默认值  | 说明     |
|-----------------------|----------------|------|--------|
| `popup_bottom_margin` | `Int`          | `0`  | 弹窗下边距  |
| `popup_width`         | `Int`          | `0`  | 弹窗宽度   |
| `popup_height`        | `Int`          | `0`  | 弹窗高度   |
| `popup_key_height`    | `Int`          | `0`  | 弹窗按键高度 |
| `popup_font`          | `List<String>` | `[]` | 弹窗字体   |
| `popup_text_size`     | `Float`        | `0`  | 弹窗字号   |

### Enter 键标签

| YAML 键             | 类型           | 默认值  | 说明                                    |
|--------------------|--------------|------|---------------------------------------|
| `enter_label_mode` | `Int`        | `0`  | Enter 键标签模式：0=不使用，1=仅使用，2=优先使用，3=回退使用 |
| `enter_labels`     | `EnterLabel` | (见下) | 自定义 Enter 键各模式文本                      |

**`enter_labels` 子字段：**

| YAML 键    | 类型       | 默认值         |
|-----------|----------|-------------|
| `go`      | `String` | `"go"`      |
| `done`    | `String` | `"done"`    |
| `next`    | `String` | `"next"`    |
| `pre`     | `String` | `"pre"`     |
| `search`  | `String` | `"search"`  |
| `send`    | `String` | `"send"`    |
| `default` | `String` | `"default"` |

### T9 键盘

| YAML 键                 | 类型             | 默认值  | 说明      |
|------------------------|----------------|------|---------|
| `t9_side_font`         | `List<String>` | `[]` | T9 侧栏字体 |
| `t9_side_text_size`    | `Float`        | `-1` | T9 侧栏字号 |
| `t9_side_round_corner` | `Float`        | `-1` | T9 侧栏圆角 |

### 其他

| YAML 键              | 类型                     | 默认值             | 说明       |
|---------------------|------------------------|-----------------|----------|
| `background_folder` | `String`               | `"backgrounds"` | 背景图所在子目录 |
| `font_variations`   | `Map<String, Boolean>` | `{}`            | 字体变体开关   |
| `display_variants`  | `Map<String, String>`  | `{}`            | 显示变体映射   |

---

## `preedit` — 预编辑视图

| YAML 键               | 类型      | 默认值   | 说明       |
|----------------------|---------|-------|----------|
| `horizontal_padding` | `Int`   | `8`   | 横向内边距    |
| `top_end_radius`     | `Float` | `0`   | 末上端圆角    |
| `alpha`              | `Float` | `0.8` | 透明度（0~1） |

### `preedit.foreground` — 前景样式

| YAML 键      | 类型      | 默认值  | 说明      |
|-------------|---------|------|---------|
| `font_size` | `Float` | `16` | 预编辑字体大小 |

**示例：**

```yaml
preedit:
  horizontal_padding: 8
  top_end_radius: 4
  alpha: 0.8
  foreground:
    font_size: 16
```

---

## `window` — 候选悬浮窗

| YAML 键          | 类型      | 默认值   | 说明       |
|-----------------|---------|-------|----------|
| `min_width`     | `Int`   | `0`   | 最小宽度     |
| `corner_radius` | `Float` | `0`   | 圆角半径     |
| `border`        | `Int`   | `0`   | 边框宽度     |
| `shadow`        | `Float` | `0`   | 阴影半径     |
| `alpha`         | `Float` | `1.0` | 透明度（0~1） |

### `window.insets` / `window.item_padding` — 边距

| YAML 键       | 类型    | 默认值 | 说明   |
|--------------|-------|-----|------|
| `vertical`   | `Int` | `0` | 纵向边距 |
| `horizontal` | `Int` | `0` | 横向边距 |

### `window.foreground` — 前景样式

| YAML 键              | 类型      | 默认值  | 说明     |
|---------------------|---------|------|--------|
| `label_font_size`   | `Float` | `20` | 序号字体大小 |
| `text_font_size`    | `Float` | `20` | 文字字体大小 |
| `comment_font_size` | `Float` | `20` | 注释字体大小 |

**示例：**

```yaml
window:
  insets:
    vertical: 4
    horizontal: 4
  item_padding:
    horizontal: 4
  corner_radius: 4
  border: 1
  alpha: 1.0
  foreground:
    label_font_size: 20
    text_font_size: 20
    comment_font_size: 16
```

---

## `liquid_keyboard` — 液态键盘

| YAML 键            | 类型               | 默认值  | 说明            |
|-------------------|------------------|------|---------------|
| `single_width`    | `Int`            | `0`  | SINGLE 类型按键宽度 |
| `key_height`      | `Int`            | `0`  | 按键高度          |
| `key_height_land` | `Int`            | `0`  | 横屏按键高度        |
| `row`             | `Int`            | `6`  | 每屏最多显示行数      |
| `row_land`        | `Int`            | `5`  | 横屏每屏最多显示行数    |
| `vertical_gap`    | `Int`            | `1`  | 纵向按键间隙        |
| `margin_x`        | `Float`          | `0`  | 左右按键间隙的 1/2   |
| `fixed_key_bar`   | `KeyBar`         | (见下) | 固定按键条         |
| `keyboards`       | `List<Keyboard>` | `[]` | 按键分类列表        |

### `fixed_key_bar` — 固定按键条

| YAML 键     | 类型                   | 默认值      | 说明   |
|------------|----------------------|----------|------|
| `position` | `Position`           | `BOTTOM` | 摆放位置 |
| `keys`     | `List<FixedKeyItem>` | `[]`     | 按键列表 |

**`Position` 枚举值：** `TOP` | `LEFT` | `BOTTOM` | `RIGHT` | `NAVBAR`

**`keys[]` 每个按键（可为字符串或映射）：**

| YAML 键   | 类型       | 默认值    | 说明   |
|----------|----------|--------|------|
| `click`  | `String` | `""`   | 点击行为 |
| `label`  | `String` | `""`   | 显示标签 |
| `width`  | `Float`  | `null` | 宽度   |
| `height` | `Float`  | `null` | 高度   |

边距/内边距（`margin` / `padding`）支持两种格式：

- 单个数值：四边相同
- 数组 `[left, top, right, bottom]` 或 `[horizontal, vertical]`

### `keyboards[]` — 按键分类

| YAML 键 | 类型              | 必填    | 说明           |
|--------|-----------------|-------|--------------|
| (映射键名) | `String`        | **是** | 分类 ID        |
| `type` | `Type`          | **是** | 分类类型         |
| `name` | `String`        | 否     | 显示名称（默认取 ID） |
| `keys` | `List<KeyItem>` | 否     | 按键列表         |

**`Type` 枚举值：** `SINGLE` | `SYMBOL` | `TABS` | `HISTORY` | `VAR_LENGTH`

**`keys[]` 每个按键：**

| YAML 键  | 类型       | 默认值  | 说明   |
|---------|----------|------|------|
| `click` | `String` | `""` | 点击输出 |
| `label` | `String` | `""` | 显示文本 |

**示例：**

```yaml
liquid_keyboard:
  row: 6
  key_height: 40
  single_width: 60
  margin_x: 5
  fixed_key_bar:
    position: bottom
    keys:
      - liquid_keyboard_exit
      - BackSpace
      - Return
  keyboards:
    emoji:
      type: SINGLE
      name: Emoji
      keys: ":-) :-( ;-) :D :P"
    math:
      type: SINGLE
      name: Math
      keys: "+ - * / = < >"
```

---

## `preset_keys` — 预设按键

每个按键以唯一 ID 作为映射键名。

| YAML 键         | 类型             | 默认值     | 说明                                  |
|----------------|----------------|---------|-------------------------------------|
| `command`      | `String`       | `""`    | 执行命令（如 `date`, `run`, `web_search`） |
| `option`       | `String`       | `""`    | 命令参数                                |
| `select`       | `String`       | `""`    | 选择键盘/方案                             |
| `toggle`       | `String`       | `""`    | 切换状态                                |
| `label`        | `String`       | `""`    | 按键显示标签                              |
| `preview`      | `String`       | `null`  | 预览标签                                |
| `shift_lock`   | `String`       | `""`    | Shift 锁定模式                          |
| `send`         | `String`       | `""`    | 发送按键事件                              |
| `commit`       | `String`       | `""`    | 直接上屏文本                              |
| `text`         | `String`       | `""`    | 模拟输入文本（可使用按键宏）                      |
| `sticky`       | `Boolean`      | `false` | 粘滞键（切换后保持）                          |
| `repeatable`   | `Boolean`      | `false` | 可连续触发                               |
| `slide_cursor` | `Boolean`      | `false` | 滑动移动光标                              |
| `slide_delete` | `Boolean`      | `false` | 滑动删除                                |
| `functional`   | `Boolean`      | `false` | 功能键标识                               |
| `states`       | `List<String>` | `[]`    | 切换状态标签列表                            |

**示例：**

```yaml
preset_keys:
  BackSpace:
    label: "退格"
    repeatable: true
    send: BackSpace

  Mode_switch:
    toggle: ascii_mode
    send: Mode_switch
    states: [中文, 西文]

  Date:
    label: "日期"
    command: date
    option: "yyyy-MM-dd"

  Google:
    label: "搜索"
    command: web_search
    option: "%4$s"
```

---

## `preset_keyboards` — 预设键盘布局

每个键盘以唯一 ID 作为映射键名。

### 键盘级字段

| YAML 键                    | 类型               | 默认值      | 说明                                      |
|---------------------------|------------------|----------|-----------------------------------------|
| `__include`               | `String`         | —        | 引用其他键盘配置（如 `/preset_keyboards/default`） |
| `name`                    | `String`         | `""`     | 键盘名称                                    |
| `author`                  | `String`         | `""`     | 作者                                      |
| `ascii_mode`              | `Boolean`        | `true`   | 是否为 ASCII 模式（0=false, 1=true）           |
| `reset_ascii_mode`        | `Boolean`        | `false`  | 显示时重置为 ascii_mode 状态                    |
| `lock`                    | `Boolean`        | `false`  | 切换程序时记忆键盘                               |
| `keyboard_height`         | `Int`            | `0`      | 竖屏键盘高度                                  |
| `keyboard_height_land`    | `Int`            | `0`      | 横屏键盘高度                                  |
| `horizontal_gap`          | `Int`            | `0`      | 按键水平间距                                  |
| `vertical_gap`            | `Int`            | `0`      | 按键垂直间距                                  |
| `round_corner`            | `Float`          | `-1`     | 按键圆角半径                                  |
| `key_border`              | `Int`            | `-1`     | 按键边框宽度                                  |
| `auto_height_index`       | `Int`            | `-1`     | 自动高度索引                                  |
| `label_transform`         | `LabelTransform` | `NONE`   | 标签变换                                    |
| `ascii_keyboard`          | `String`         | `""`     | ASCII 模式键盘 ID                           |
| `landscape_keyboard`      | `String`         | `""`     | 横屏键盘 ID                                 |
| `landscape_split_percent` | `Int`            | `0`      | 横屏分割百分比                                 |
| `import_preset`           | `String`         | `""`     | 导入预设                                    |
| `keyboard_padding_top`    | `Int`            | `0`      | 键盘上边距                                   |
| `navbar`                  | `Boolean`        | `false`  | 是否在导航栏中显示                               |
| `t9_mode`                 | `Boolean`        | `false`  | 是否启用 T9 模式                              |
| `t9_sidebar_width`        | `Float`          | `0.15`   | T9 侧栏宽度                                 |
| `t9_sidebar_position`     | `String`         | `"left"` | T9 侧栏位置                                 |
| `t9_sidebar_span_rows`    | `Int`            | `3`      | T9 侧栏跨越行数                               |
| `t9_sidebar_show_items`   | `Int`            | `4`      | T9 侧栏显示数量                               |
| `t9_sidebar_symbols`      | `List<String>`   | `[]`     | T9 侧栏符号                                 |
| `dynamic_mode`            | `Boolean`        | `false`  | 动态键盘模式                                  |
| `dynamic_original`        | `String`         | `""`     | 动态键盘原始键盘 ID                             |

### 按键偏移覆盖

| YAML 键                | 类型      | 默认值 | 说明        |
|-----------------------|---------|-----|-----------|
| `key_text_offset_x`   | `Float` | `0` | 按键文字横向偏移  |
| `key_text_offset_y`   | `Float` | `0` | 按键文字纵向偏移  |
| `key_symbol_offset_x` | `Float` | `0` | 按键符号横向偏移  |
| `key_symbol_offset_y` | `Float` | `0` | 按键符号纵向偏移  |
| `key_hint_offset_x`   | `Float` | `0` | 按键提示横向偏移  |
| `key_hint_offset_y`   | `Float` | `0` | 按键提示纵向偏移  |
| `key_press_offset_x`  | `Float` | `0` | 按键按下时横向偏移 |
| `key_press_offset_y`  | `Float` | `0` | 按键按下时纵向偏移 |

**`LabelTransform` 枚举值：** `NONE` | `UPPERCASE`

### `rows[]` — 行定义

| YAML 键   | 类型        | 默认值     | 说明    |
|----------|-----------|---------|-------|
| `height` | `Float`   | `0`     | 行高度比例 |
| `split`  | `Boolean` | `false` | 是否分割  |

### `rows[].keys[]` — 按键定义

| YAML 键                     | 类型             | 默认值     | 说明             |
|----------------------------|----------------|---------|----------------|
| `click`                    | `String`       | `""`    | 点击行为           |
| `label`                    | `String`       | `""`    | 显示标签           |
| `label_symbol`             | `String`       | `""`    | 符号标签           |
| `hint`                     | `String`       | `""`    | 助记提示           |
| `width`                    | `Float`        | `0`     | 宽度比例（0~1）      |
| `spacer`                   | `Boolean`      | `false` | 是否为占位空白        |
| `send_bindings`            | `Boolean`      | `true`  | 是否发送按键绑定事件     |
| `round_corner`             | `Float`        | `-1`    | 按键圆角（-1=继承全局）  |
| `key_border`               | `Int`          | `-1`    | 按键边框（-1=继承全局）  |
| `key_border_color`         | `String`       | `""`    | 按键边框颜色         |
| `popup`                    | `List<String>` | `[]`    | 弹窗备选字符         |
| `dynamic`                  | `String`       | `""`    | 动态键盘绑定         |
| `key_text_color`           | `String`       | `""`    | 按键文字颜色         |
| `key_back_color`           | `String`       | `""`    | 按键背景颜色         |
| `key_symbol_color`         | `String`       | `""`    | 按键符号颜色         |
| `hilited_key_text_color`   | `String`       | `""`    | 高亮按键文字颜色       |
| `hilited_key_back_color`   | `String`       | `""`    | 高亮按键背景颜色       |
| `hilited_key_symbol_color` | `String`       | `""`    | 高亮按键符号颜色       |
| `key_text_size`            | `Float`        | `0`     | 按键文字字号（0=继承全局） |
| `symbol_text_size`         | `Float`        | `0`     | 按键符号字号（0=继承全局） |
| `hint_text_size`           | `Float`        | `0`     | 按键提示字号（0=继承全局） |
| `key_text_offset_x`        | `Float`        | `0`     | 按键文字横向偏移       |
| `key_text_offset_y`        | `Float`        | `0`     | 按键文字纵向偏移       |
| `key_symbol_offset_x`      | `Float`        | `0`     | 按键符号横向偏移       |
| `key_symbol_offset_y`      | `Float`        | `0`     | 按键符号纵向偏移       |
| `key_hint_offset_x`        | `Float`        | `0`     | 按键提示横向偏移       |
| `key_hint_offset_y`        | `Float`        | `0`     | 按键提示纵向偏移       |
| `key_press_offset_x`       | `Float`        | `0`     | 按键按下时横向偏移      |
| `key_press_offset_y`       | `Float`        | `0`     | 按键按下时纵向偏移      |

### 按键行为 (KeyBehavior)

每个按键可在映射中指定以下行为的触发动作。行为键名使用小写。

| 行为键名                | 说明       |
|---------------------|----------|
| `click`             | 单击       |
| `composing`         | 输入中状态    |
| `has_menu`          | 有菜单时     |
| `paging`            | 翻页时      |
| `combo`             | 并击       |
| `ascii`             | ASCII 模式 |
| `double_click`      | 双击       |
| `lazy_double_click` | 延迟双击     |
| `long_click`        | 长按       |
| `swipe_left`        | 左滑       |
| `swipe_right`       | 右滑       |
| `swipe_up`          | 上滑       |
| `swipe_down`        | 下滑       |
| `extra`             | 额外行为     |

**示例：**

```yaml
preset_keyboards:
  my_kb:
    name: "My Keyboard"
    ascii_mode: 0
    lock: true
    rows:
      - keys:
          - { click: "q", long_click: "_" }
          - { click: "w", long_click: "-" }
          - { click: "e", long_click: "+" }
      - keys:
          - { click: Shift_L, width: 0.15 }
          - { click: space, width: 0.4 }
          - { click: BackSpace, width: 0.15 }
          - {
              click: Return,
              composing: "回车",
              long_click: CommitComment,
              width: 0.15,
            }
```

---

## `preset_color_schemes` — 配色方案

每个配色以唯一 ID 作为映射键名。`name` 和 `author` 为可选元数据，其余键值对为颜色定义。

颜色值使用 `0xAARRGGBB` 十六进制格式。

### 所有颜色键名

#### 基础颜色

| 键名             | 回退链          | 说明    |
|----------------|--------------|-------|
| `back_color`   | —            | 通用背景色 |
| `text_color`   | —            | 通用文字色 |
| `border_color` | `back_color` | 边框颜色  |

#### 候选栏颜色

| 键名                             | 回退链                            | 说明      |
|--------------------------------|--------------------------------|---------|
| `candidate_text_color`         | `text_color`                   | 候选文字颜色  |
| `candidate_background`         | `back_color`                   | 候选栏整体背景 |
| `candidate_separator_color`    | `border_color`                 | 候选分割线颜色 |
| `hilited_candidate_back_color` | —                              | 高亮候选背景  |
| `hilited_candidate_text_color` | —                              | 高亮候选文字  |
| `hilited_text_color`           | —                              | 高亮编码文字  |
| `hilited_back_color`           | —                              | 高亮编码背景  |
| `hilited_label_color`          | `hilited_candidate_text_color` | 高亮候选序号  |
| `hilited_comment_text_color`   | `comment_text_color`           | 高亮编码提示  |
| `comment_text_color`           | `candidate_text_color`         | 编码提示颜色  |
| `label_color`                  | `candidate_text_color`         | 候选序号颜色  |

#### 按键颜色

| 键名                           | 回退链                            | 说明        |
|------------------------------|--------------------------------|-----------|
| `key_back_color`             | `back_color`                   | 按键背景      |
| `key_text_color`             | `candidate_text_color`         | 按键文字      |
| `key_symbol_color`           | `comment_text_color`           | 按键符号      |
| `key_border_color`           | `border_color`                 | 按键边框      |
| `hilited_key_back_color`     | `hilited_candidate_back_color` | 高亮按键背景    |
| `hilited_key_text_color`     | `hilited_candidate_text_color` | 高亮按键文字    |
| `hilited_key_symbol_color`   | `hilited_comment_text_color`   | 高亮按键符号    |
| `off_key_back_color`         | `key_back_color`               | 关状态按键背景   |
| `off_key_text_color`         | `key_text_color`               | 关状态按键文字   |
| `on_key_back_color`          | `hilited_key_back_color`       | 开状态按键背景   |
| `on_key_text_color`          | `hilited_key_text_color`       | 开状态按键文字   |
| `hilited_off_key_back_color` | `hilited_key_back_color`       | 高亮关状态按键背景 |
| `hilited_off_key_text_color` | `hilited_key_text_color`       | 高亮关状态按键文字 |
| `hilited_on_key_back_color`  | `hilited_key_back_color`       | 高亮开状态按键背景 |
| `hilited_on_key_text_color`  | `hilited_key_text_color`       | 高亮开状态按键文字 |

#### 其他颜色

| 键名                                       | 回退链                   | 说明            |
|------------------------------------------|-----------------------|---------------|
| `keyboard_back_color`                    | `border_color`        | 键盘区背景         |
| `text_back_color`                        | `back_color`          | 编码区（悬浮窗）背景    |
| `root_background`                        | `back_color`          | 键盘+候选栏总背景     |
| `preview_back_color`                     | `key_back_color`      | 按键提示背景        |
| `preview_text_color`                     | `key_text_color`      | 按键提示文字        |
| `shadow_color`                           | `border_color`        | 阴影颜色          |
| `liquid_keyboard_background`             | `keyboard_back_color` | 液态键盘背景        |
| `long_text_back_color`                   | `key_back_color`      | 长文本按键背景（剪贴板）  |
| `clipboard_entry_back_color`             | —                     | 剪贴板条目背景       |
| `clipboard_category_back_color`          | —                     | 剪贴板分类按钮背景     |
| `clipboard_category_selected_back_color` | —                     | 剪贴板分类选中按钮背景   |
| `clipboard_category_selected_text_color` | —                     | 剪贴板分类选中按钮文字   |
| `clipboard_checkbox_color`               | `key_text_color`      | 剪贴板多选复选框颜色    |
| `hilited_clipboard_entry_back_color`     | —                     | 高亮剪贴板条目背景     |
| `candidate_view_back_color`              | —                     | 候选视图背景        |
| `candidate_virtual_tab_color`            | —                     | 候选虚拟 Tab 颜色   |
| `candidate_virtual_tab_highlight_color`  | —                     | 候选虚拟 Tab 高亮颜色 |

**示例：**

```yaml
preset_color_schemes:
  my_dark:
    name: "My Dark"
    author: "Me"
    back_color: 0x222222
    border_color: 0x333333
    text_color: 0xeef8ff
    candidate_text_color: 0xeef8ff
    hilited_candidate_back_color: 0x444444
    hilited_candidate_text_color: 0xfffe7f
    key_back_color: 0x2a2a2a
    key_text_color: 0xcccccc
    keyboard_back_color: 0x1a1a1a
```

---

## `fallback_colors` — 颜色回退（可选）

定义颜色键名之间的回退关系。这通常已内置在程序中，此处可覆盖。

```yaml
fallback_colors:
  candidate_text_color: text_color
  comment_text_color: candidate_text_color
  key_text_color: candidate_text_color
  border_color: back_color
```

---

## `tool_bar` — 工具栏

| YAML 键           | 类型             | 默认值               | 说明        |
|------------------|----------------|-------------------|-----------|
| `button_font`    | `List<String>` | `[]`              | 按钮字体      |
| `back_style`     | `String`       | `"ic@arrow-left"` | 返回图标样式    |
| `primary_button` | `Button`       | `null`            | 主按钮（显眼位置） |
| `buttons`        | `List<Button>` | `[]`              | 工具栏按钮列表   |

### `Button` — 按钮定义

| YAML 键              | 类型          | 默认值  | 说明                     |
|---------------------|-------------|------|------------------------|
| `action`            | `String`    | `""` | 点击动作                   |
| `long_press_action` | `String`    | `""` | 长按动作                   |
| `size`              | `List<Int>` | `[]` | 按钮尺寸 `[width, height]` |

#### `Button.background` — 背景

| YAML 键             | 类型       | 默认值           | 说明   |
|--------------------|----------|---------------|------|
| `type`             | `String` | `"rectangle"` | 形状类型 |
| `corner_radius`    | `Float`  | `10`          | 圆角   |
| `normal`           | `String` | `""`          | 正常颜色 |
| `highlight`        | `String` | `""`          | 高亮颜色 |
| `vertical_inset`   | `Int`    | `4`           | 纵向内缩 |
| `horizontal_inset` | `Int`    | `4`           | 横向内缩 |

#### `Button.foreground` — 前景

| YAML 键          | 类型             | 默认值  | 说明                |
|-----------------|----------------|------|-------------------|
| `style`         | `String`       | `""` | 样式（如 `"ic"` 表示图标） |
| `option_styles` | `List<String>` | `[]` | 可选样式              |
| `normal`        | `String`       | `""` | 正常颜色/图标           |
| `highlight`     | `String`       | `""` | 高亮颜色/图标           |
| `font_size`     | `Float`        | `18` | 字号                |
| `padding`       | `Int`          | `4`  | 内边距               |

**示例：**

```yaml
tool_bar:
  button_font: [latin.ttf]
  back_style: "ic@arrow-left"
  primary_button:
    action: "Menu"
    foreground:
      style: "ic"
      normal: "apps"
      font_size: 20
  buttons:
    - action: "Keyboard_default"
      foreground:
        style: "ic"
        normal: "keyboard_return"
        font_size: 20
    - action: "Mode_switch"
      foreground:
        style: "text"
        normal: "中"
        font_size: 18
```

---

## `candidates_tool` — 候选工具栏

| YAML 键             | 类型                  | 默认值  | 说明                    |
|--------------------|---------------------|------|-----------------------|
| `nav_width`        | `Int`               | `44` | 导航栏宽度                 |
| `popup_width`      | `Int`               | `0`  | 长按菜单最大宽度（dp），0=不限制    |
| `popup_text_size`  | `Float`             | `0`  | 长按菜单文字大小（sp），0=使用系统默认 |
| `popup_text_color` | `String`            | `""` | 长按菜单文字颜色键             |
| `popup_font`       | `List<String>`      | `[]` | 长按菜单字体                |
| `background`       | `String`            | `""` | 背景颜色                  |
| `separator_color`  | `String`            | `""` | 分割线颜色                 |
| `button_font`      | `List<String>`      | `[]` | 按钮字体                  |
| `buttons`          | `List<Button>`      | `[]` | 按钮列表                  |
| `popup`            | `List<PopupAction>` | `[]` | 候选长按菜单项列表             |

`Button` 结构与 `tool_bar.buttons[]` 相同。

`PopupAction` 字段：

| 字段       | 类型       | 默认值  | 说明                                   |
|----------|----------|------|--------------------------------------|
| `action` | `String` | `""` | 动作名称，`"DeleteCandidate"` 为删除候选词的专用动作 |
| `label`  | `String` | `""` | 菜单显示文本，留空则使用动作内置标签                   |

**示例：**

```yaml
candidates_tool:
  nav_width: 44
  popup_width: 120
  background: "0xeeeeee"
  separator_color: "0xcccccc"
  buttons:
    - action: "Keyboard_symbols"
      foreground:
        style: "ic"
        normal: "symbols"
    - action: "clipboard_window"
      foreground:
        style: "ic"
        normal: "assignment"
  popup:
    - action: "DeleteCandidate"
      label: "忘记该词"
    - action: "Keyboard_symbols"
    - action: "SwitchAsciiMode"
```

---

## 枚举值速查表

| 枚举类型                  | 所在的 YAML 路径                                | 有效值                                                                                                                                                                             |
|-----------------------|--------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CommentPosition`     | `style.comment_position`                   | `RIGHT`, `TOP`, `OVERLAY`                                                                                                                                                       |
| `KeyBar.Position`     | `liquid_keyboard.fixed_key_bar.position`   | `TOP`, `LEFT`, `BOTTOM`, `RIGHT`, `NAVBAR`                                                                                                                                      |
| `LiquidKeyboard.Type` | `liquid_keyboard.keyboards.<id>.type`      | `SINGLE`, `SYMBOL`, `TABS`, `HISTORY`, `VAR_LENGTH`                                                                                                                             |
| `LabelTransform`      | `preset_keyboards.<id>.label_transform`    | `NONE`, `UPPERCASE`                                                                                                                                                             |
| `KeyBehavior`         | `preset_keyboards.<id>.rows[].keys[]` (键名) | `click`, `composing`, `has_menu`, `paging`, `combo`, `ascii`, `double_click`, `lazy_double_click`, `long_click`, `swipe_left`, `swipe_right`, `swipe_up`, `swipe_down`, `extra` |

---

## 完整示例

一个最小的完整主题文件：

```yaml
config_version: "3.0"
name: "My Theme"
author: "Me"

style:
  candidate_text_size: 20
  candidate_view_height: 28
  comment_position: right
  comment_text_size: 12
  horizontal_gap: 1
  vertical_gap: 1
  round_corner: 8
  keyboard_height: 250
  keyboard_height_land: 200
  key_text_size: 20
  label_text_size: 20
  symbol_text_size: 10
  key_font: [symbol.ttf]
  latin_font: [latin.ttf]

preedit:
  horizontal_padding: 8
  top_end_radius: 4
  alpha: 0.8
  foreground:
    font_size: 16

window:
  insets:
    vertical: 4
    horizontal: 4
  item_padding:
    horizontal: 4
  foreground:
    label_font_size: 18
    text_font_size: 18
    comment_font_size: 14

preset_color_schemes:
  default:
    name: "Default"
    back_color: 0x222222
    border_color: 0x333333
    text_color: 0xeef8ff
    candidate_text_color: 0xeef8ff
    hilited_candidate_back_color: 0x444444
    hilited_candidate_text_color: 0xffff00
    key_back_color: 0x2a2a2a
    key_text_color: 0xcccccc
    keyboard_back_color: 0x1a1a1a
    preview_back_color: 0x333333
    preview_text_color: 0xffffff
```
