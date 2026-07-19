-- SPDX-FileCopyrightText: 2015 - 2024 Rime community
--
-- SPDX-License-Identifier: GPL-3.0-or-later

--- LyraIME 主题 Lua API 类型定义文件
---
--- 此文件仅为 IDE 提供语法检查、自动补全和类型提示，不影响实际运行。
--- 沙箱环境已预注入所有 API 函数和受限库，无需 require 任何模块。
---
--- 使用方法：将此文件放在主题目录中，或配置 IDE 的 Lua Language Server workspace。

-- ============================================================================
-- 沙箱环境说明
-- ============================================================================

--- safe_require 函数（受限模块加载）
---
--- 搜索路径：themes/lib/ 及其全部子目录
--- 模块名中的 '.' 替换为 '/'，尝试 ?.lua 和 ?/init.lua
--- 禁止 '..' 路径穿越，禁止符号链接逃逸
---
--- 注意：原 require 已禁用。使用 require 会导致主题加载失败。
---
---@param module string 模块名，如 "colors.dark" 对应 themes/lib/colors/dark.lua
---@return table
function safe_require(module)
  return {}
end

-- ============================================================================
-- 注入的 API 函数
-- ============================================================================

--- 创建主题（API 函数，已注入全局作用域）
---
--- 校验顶层 key，返回输入 table
---
---@param t table @主题配置 table
---@return table
function theme(t)
  return {}
end

--- 校验全局样式 key（API 函数）
---@param t table @样式配置
---@return table
function style(t)
  return {}
end

--- 创建配色方案（API 函数）
---
--- 返回 { id = name, colors = colors } 的 table
---
---@param name   string                @配色方案 ID，如 "latte"、"mocha"
---@param colors table<string, string> @颜色键值对，hex 字符串如 "0x4c4f69"
---@return ColorScheme
function scheme(name, colors)
  ---@type ColorScheme
  local result = { id = name, colors = colors }
  return result
end

--- 校验键盘 key（API 函数）
---@param t table @键盘配置
---@return table
function keyboard(t)
  return {}
end

--- 校验行 key（API 函数）
---@param t table @行配置
---@return table
function row(t)
  return {}
end

--- 校验按键 key（API 函数）
---@param t table @按键配置
---@return table
function key(t)
  return {}
end

--- PresetKey 包装器（API 函数）
---@param id string @预设键 ID
---@param t  table  @预设键配置
---@return PresetKey
function pk(id, t)
  ---@type PresetKey
  local result = {
    label = t.label or id,
    command = t.command or "",
    option = t.option or "",
    select = t.select or "",
    toggle = t.toggle or "",
    send = t.send or "",
    states = t.states or {},
    shift_lock = t.shift_lock or "",
    repeatable = t.repeatable or false,
    functional = t.functional or false,
    sticky = t.sticky or false,
    commit = t.commit or "",
    text = t.text or "",
    preview = t.preview,
    slide_cursor = t.slide_cursor or false,
    slide_delete = t.slide_delete or false
  }
  return result
end

--- 校验回退颜色引用（API 函数）
---@param t table<string, string> @颜色引用映射
---@return table
function fallback(t)
  return {}
end

--- 预编辑区配置（API 函数）
---@param t table @Preedit 配置
---@return table
function preedit(t)
  return {}
end

--- 候选窗配置（API 函数）
---@param t table @Window 配置
---@return table
function window(t)
  return {}
end

--- 工具栏配置（API 函数）
---@param t table @ToolBar 配置
---@return table
function toolbar(t)
  return {}
end

--- 工具栏按钮配置（API 函数）
---@param t table @Button 配置
---@return table
function btn(t)
  return {}
end

--- 工具栏按钮背景配置（API 函数）
---@param t table @Background 配置
---@return table
function bg(t)
  return {}
end

--- 工具栏按钮前景配置（API 函数）
---@param t table @Foreground 配置
---@return table
function fg(t)
  return {}
end

--- 液态键盘配置（API 函数）
---@param t table @LiquidKeyboard 配置
---@return table
function liquid(t)
  return {}
end

--- 递归深合并两个 table（__patch 等效函数）
---
--- 将 base 和 overrides 递归合并为一个新 table。
--- 对于同名键：若两者均为 table，递归继续合并；否则 overrides 覆盖 base。
--- 等效于 Rime YAML 的 __patch 深度合并语义。
---
--- 示例 1 — 覆盖顶层字段:
---   local base = { a = 1, b = 2 }
---   local r = merge(base, { b = 99 })  -- r = { a = 1, b = 99 }
---
--- 示例 2 — 修改嵌套行中的一个按键 (rows[2].keys[3]):
---   local base = keyboard { name = "26键", rows = { ... } }
---   local letter = merge(base, {
---     ascii_mode = true,
---     rows = {
---       [2] = {
---         keys = {
---           [3] = { long_click = "overridden" },
---         },
---       },
---     },
---   })
---
---@param base      table @基础 table
---@param overrides table @覆盖 table
---@return table @递归合并后的新 table
function merge(base, overrides)
  local result = {}
  for k, v in pairs(base) do
    result[k] = v
  end
  for k, v in pairs(overrides) do
    if type(v) == "table" and type(result[k]) == "table" then
      result[k] = merge(result[k], v)
    else
      result[k] = v
    end
  end
  return result
end

--- 向序列指定位置插入元素
---
--- 返回新 table，原序列不受影响。1-based 索引（Lua 原生）。
---
--- 注意: Lua 序列从 1 开始计数，YAML 从 0 开始。
---   YAML rows[0] → Lua rows[1]; YAML keys[2] → Lua keys[3]
---
--- 示例 — 在第 2 行和第 3 行之间插入新行:
---   insert(base.rows, 3, row { keys = { ... } })
---
--- 示例 — 在第 1 行第 2 键前插入新按键:
---   insert(base.rows[1].keys, 2, key { click = "new" })
---
---@param seq   table   @源序列（1-based 整数键）
---@param pos   integer @插入位置 (1 ≤ pos ≤ #seq + 1)
---@param value any     @要插入的值
---@return table @包含插入元素的新序列
function insert(seq, pos, value)
  local result = {}
  for i = 1, #seq do
    if i == pos then result[#result + 1] = value end
    result[#result + 1] = seq[i]
  end
  if pos > #seq then result[#result + 1] = value end
  return result
end

-- ============================================================================
-- 数据类定义
-- ============================================================================

---@class Theme @主题配置（顶层）
---@field name                 string                      @主题名称，如 "Catppuccin"
---@field version              string?                     @版本号，如 "1.0"
---@field author               string?                     @作者
---@field style                GeneralStyle                @全局样式（style map）
---@field preedit              Preedit?                    @预编辑区配置，默认 Preedit()
---@field window               Window?                     @候选窗配置，默认 Window()
---@field preset_color_schemes ColorScheme[]               @配色方案列表
---@field preset_keys          table<string, PresetKey>    @预设按键映射，键名为按键 ID
---@field preset_keyboards     table<string, TextKeyboard> @预设键盘映射，键名为键盘 ID
---@field fallback_colors      table<string, string>?      @回退颜色引用
---@field tool_bar             ToolBar?                    @工具栏配置，默认 ToolBar()
---@field liquid_keyboard      LiquidKeyboard?             @液态键盘配置
---@field candidates_tool      CandidatesTool?             @候选工具栏配置，可选

---@class GeneralStyle @全局样式
---@field auto_caps                        boolean                @自动句首大写，默认 false
---@field candidate_border                 integer                @候选边框宽度，默认 0
---@field candidate_border_round           number                 @候选边框圆角，默认 0
---@field candidate_padding                integer                @候选项内边距 (px)，默认 0
---@field candidate_spacing                number                 @候选间距，默认 0
---@field candidate_text_vertical_bias     number                 @候选文本垂直偏移 (0.0=top, 1.0=bottom)，默认 1.0
---@field candidate_view_height            integer                @候选区高度 (px)，默认 28
---@field candidate_corner_radius          number                 @候选项圆角半径，默认 5
---@field comment_height                   integer                @编码提示区高度，默认 12
---@field comment_position                 CommentPosition        @注释位置，默认 "RIGHT"
---@field comment_vertical_bias            number                 @注释垂直偏移 (仅 overlay 模式)，默认 0.0
---@field fonts                            FontStyle              @字体/字号/变体，默认 FontStyle()
---@field horizontal_gap                   integer                @键水平间距 (px)，默认 0
---@field keyboard_padding                 integer                @竖屏左右边距 (px)，默认 0
---@field keyboard_padding_left            integer                @左手模式左侧边距 (px)，默认 0
---@field keyboard_padding_right           integer                @左手模式右侧边距 (px)，默认 40
---@field keyboard_padding_bottom          integer                @竖屏底部边距 (px)，默认 0
---@field keyboard_padding_land            integer                @横屏左右边距 (px)，默认 0
---@field keyboard_padding_land_bottom     integer                @横屏底部边距 (px)，默认 0
---@field keyboard_padding_top             integer                @键盘顶部边距 (px)，默认 0
---@field key_border                       integer                @按键边框宽度，默认 0
---@field key_text_offset_x                number                 @键文本 X 偏移，默认 0
---@field key_text_offset_y                number                 @键文本 Y 偏移，默认 0
---@field key_symbol_offset_x              number                 @键符号 X 偏移，默认 0
---@field key_symbol_offset_y              number                 @键符号 Y 偏移，默认 0
---@field key_hint_offset_x                number                 @键提示 X 偏移，默认 0
---@field key_hint_offset_y                number                 @键提示 Y 偏移，默认 0
---@field key_press_offset_x               number                 @按下 X 偏移，默认 0
---@field key_press_offset_y               number                 @按下 Y 偏移，默认 0
---@field keyboard_height                  integer                @竖屏键盘高度 (px)，默认 0
---@field keyboard_height_land             integer                @横屏键盘高度 (px)，默认 0
---@field popup_bottom_margin              integer                @悬浮提示底部边距，默认 0
---@field popup_width                      integer                @悬浮提示宽度，默认 0
---@field popup_height                     integer                @悬浮提示高度，默认 0
---@field popup_key_height                 integer                @悬浮提示按键高度，默认 0
---@field reset_ascii_mode_on_focus_change boolean                @焦点变更时重置 ascii 模式，默认 false
---@field round_corner                     number                 @按键圆角半径，默认 0
---@field shadow_radius                    number                 @按键阴影半径，默认 0
---@field vertical_gap                     integer                @键盘行距 (px)，默认 0
---@field background_folder                string                 @背景图存放子目录，默认 "backgrounds"
---@field enter_label_mode                 integer                @回车键 ActionLabel 模式: 0=不使用 1=仅action 2=优先 3=fallback，默认 0
---@field enter_labels                     EnterLabel             @回车键文本定义
---@field t9_side_round_corner             number                 @T9 侧栏圆角 (-1=跟随 round_corner)，默认 -1

---@class FontStyle @字体/字号/变体（GeneralStyle.fonts）
---@field candidate              string[]               @候选字体
---@field comment                string[]               @注释字体
---@field key                    string[]               @键盘字体
---@field label                  string[]               @标签字体
---@field latin                  string[]               @西文字体
---@field symbol                 string[]               @符号字体
---@field text                   string[]               @编码区字体
---@field hint                   string[]               @提示字体
---@field hanb                   string[]               @扩展汉字字体
---@field popup                  string[]               @悬浮提示字体
---@field t9_side                string[]               @T9 侧栏字体
---@field clipboard              string[]               @剪贴板字体
---@field clipboard_category     string[]               @剪贴板分类字体
---@field candidate_size         number                 @候选字号，默认 15
---@field comment_size           number                 @注释字号，默认 10
---@field key_size               number                 @键标签字号，默认 15
---@field key_long_size          number                 @长标签字号，默认 15
---@field label_size             number                 @标签字号，默认 0
---@field symbol_size            number                 @符号字号，默认 0
---@field hint_size              number                 @提示字号，默认 0
---@field popup_size             number                 @悬浮提示字号，默认 0
---@field clipboard_category_size number                @剪贴板分类字号，默认 13
---@field clipboard_size          number                @剪贴板文字字号，默认 14
---@field t9_side_size            number                @T9 侧栏字号 (-1=跟随 key_size)，默认 -1
---@field variations             table<string, boolean> @字体变体开关
---@field display                table<string, string>  @显示变体映射

---@class EnterLabel @回车键文本
---@field go      string @前往，默认 "go"
---@field done    string @完成，默认 "done"
---@field next    string @下个，默认 "next"
---@field pre     string @上个，默认 "pre"
---@field search  string @搜索，默认 "search"
---@field send    string @发送，默认 "send"
---@field default string @默认，默认 "default"

---@alias CommentPosition
--- | "RIGHT"   # 右侧
--- | "TOP"     # 顶部
--- | "OVERLAY" # 覆盖

---@class ColorScheme @配色方案
---@field id     string                @方案 ID，如 "latte"
---@field colors table<string, string> @颜色键值对，hex 字符串如 "0x4c4f69"

-- 配色方案中常用的颜色键（定义在 BuiltinFallbackColors 中）:
--
-- 基础色:
--   text_color         编码文字        → 最终需要是 hex color string
--   back_color          候选区背景      → 最终需要是 hex color string
--   border_color        边框            → 最终需要是 hex color string
--
-- 候选区:
--   candidate_text_color              candidate_separator_color
--   hilited_candidate_text_color       hilited_candidate_back_color
--   hilited_label_color               hilited_comment_text_color
--   comment_text_color                label_color
--
-- 高亮:
--   hilited_text_color                hilited_back_color
--   hilited_key_back_color            hilited_key_text_color
--   hilited_key_symbol_color
--
-- 按键:
--   key_back_color                    key_text_color
--   key_symbol_color                  key_border_color
--   off_key_back_color                off_key_text_color
--   on_key_back_color                 on_key_text_color
--   hilited_off_key_back_color        hilited_off_key_text_color
--   hilited_on_key_back_color         hilited_on_key_text_color
--
-- 键盘/背景:
--   keyboard_back_color               text_back_color
--   root_background
--
-- 预览/阴影:
--   preview_back_color                preview_text_color
--   shadow_color
--
-- 模式切换:
--   light_scheme  = "latte"          亮色模式关联的方案
--   dark_scheme   = "mocha"          暗色模式关联的方案
--
-- 其他:
--   long_text_back_color              clipboard_checkbox_color
--   hilited_candidate_button_color
--   t9_side_back_color                t9_side_hilited_back_color
--   t9_side_text_color                t9_side_border_color
--   t9_side_spacing_color

---@class PresetKey @预设按键
---@field label        string   @按键标签文本，默认 ""
---@field command      string   @命令: 空=send "function"=函数 "toggle"=切换 "date"=日期 "run"=运行等，默认 ""
---@field option       string   @命令参数，默认 ""
---@field select       string   @键盘切换目标 ID 或 .last/.next/.default，默认 ""
---@field toggle       string   @切换的 Rime 选项名，如 "ascii_mode"，默认 ""
---@field send         string   @发送的按键名，如 "BackSpace"、"Shift_L"、"Control+x"，默认 ""
---@field states       string[] @toggle 状态时的标签列表，如 {"中文", "西文"}
---@field shift_lock   string   @Shift 锁定行为: "ascii_long" | "long" | "click"，默认 ""
---@field repeatable   boolean  @是否允许连续按键，默认 false
---@field functional   boolean  @是否为功能键，默认 false
---@field sticky       boolean  @是否粘滞，默认 false
---@field commit       string   @直接上屏文本，默认 ""
---@field text         string   @插入文本，默认 ""
---@field preview      string?  @预览图标 URL，默认 nil
---@field slide_cursor boolean  @是否滑动移光标，默认 false
---@field slide_delete boolean  @是否滑动删除，默认 false

---@class TextKeyboard @预设键盘布局
---@field name                    string         @键盘名称，如 "26键"
---@field author                  string         @作者
---@field keyboard_height         integer        @高度覆盖 (0=使用全局)，默认 0
---@field keyboard_height_land    integer        @横屏高度覆盖，默认 0
---@field auto_height_index       integer        @自动高度索引 (-1=禁用)，默认 -1
---@field horizontal_gap          integer        @水平间距覆盖 (0=使用全局)，默认 0
---@field vertical_gap            integer        @垂直间距覆盖，默认 0
---@field round_corner            number         @圆角覆盖 (-1=使用全局)，默认 -1
---@field key_border              integer        @边框覆盖 (-1=使用全局)，默认 -1
---@field ascii_mode              boolean        @是否西文模式 (true=英文, false=中文)，默认 true
---@field reset_ascii_mode        boolean        @显示键盘时重置 ascii 状态，默认 false
---@field label_transform         LabelTransform @字母标变换，默认 "NONE"
---@field lock                    boolean        @切换 app 时记忆键盘，默认 false
---@field ascii_keyboard          string         @西文模式下引用的键盘 ID，默认 ""
---@field landscape_keyboard      string         @横屏模式下引用的键盘 ID，默认 ""
---@field landscape_split_percent integer        @横屏分割百分比，默认 0
---@field key_text_offset_x       number         @键文本 X 偏移覆盖，默认 0
---@field key_text_offset_y       number         @键文本 Y 偏移覆盖，默认 0
---@field key_symbol_offset_x     number         @符号 X 偏移覆盖，默认 0
---@field key_symbol_offset_y     number         @符号 Y 偏移覆盖，默认 0
---@field key_hint_offset_x       number         @提示 X 偏移覆盖，默认 0
---@field key_hint_offset_y       number         @提示 Y 偏移覆盖，默认 0
---@field key_press_offset_x      number         @按下 X 偏移覆盖，默认 0
---@field key_press_offset_y      number         @按下 Y 偏移覆盖，默认 0
---@field import_preset           string         @继承的预设键盘 ID，默认 ""
---@field keyboard_padding_top    integer        @顶部边距覆盖，默认 0
---@field navbar                  boolean        @是否显示导航栏，默认 false
---@field t9_mode                 boolean        @是否 T9 模式，默认 false
---@field t9_sidebar_width        number         @T9 侧栏宽度，默认 0.15
---@field t9_sidebar_position     string         @T9 侧栏位置: "left"|"right"，默认 "left"
---@field t9_sidebar_span_rows    integer        @T9 侧栏跨行数，默认 3
---@field t9_sidebar_show_items   integer        @T9 侧栏显示项数，默认 4
---@field t9_sidebar_symbols      string[]       @T9 侧栏符号集合
---@field dynamic_mode            boolean        @是否动态模式，默认 false
---@field dynamic_original        string         @动态原始键盘 ID，默认 ""
---@field rows                    KeyboardRow[]  @键盘行定义

---@alias LabelTransform
--- | "NONE"      # 不转换
--- | "UPPERCASE" # 大写

---@class KeyboardRow @键盘行
---@field height number    @行高覆盖 (0=自动)，默认 0
---@field split  boolean   @是否分割行，默认 false
---@field keys   TextKey[] @本行按键列表

---@class TextKey @单个按键定义
---@field width                    number   @键宽比例 [0, 1]，默认 0
---@field spacer                   boolean  @是否为占位符，默认 false
---@field round_corner             number   @圆角覆盖 (-1=使用全局)，默认 -1
---@field key_border               integer  @边框覆盖 (-1=使用全局)，默认 -1
---@field key_border_color         string   @按键边框颜色 (hex)，默认 ""
---@field label                    string   @按键标签文本，默认 ""
---@field label_symbol             string   @按键符号标签，默认 ""
---@field hint                     string   @按键提示文本，默认 ""
---@field click                    string   @点击行为 (预设键名或按键名)，默认 ""
---@field send_bindings            boolean  @是否发送按键绑定，默认 true
---@field key_text_size            number   @键文本字号覆盖 (0=使用全局)，默认 0
---@field symbol_text_size         number   @符号字号覆盖，默认 0
---@field hint_text_size           number   @提示字号覆盖，默认 0
---@field key_text_offset_x        number   @键文本 X 偏移覆盖，默认 0
---@field key_text_offset_y        number   @键文本 Y 偏移覆盖，默认 0
---@field key_symbol_offset_x      number   @符号 X 偏移覆盖，默认 0
---@field key_symbol_offset_y      number   @符号 Y 偏移覆盖，默认 0
---@field key_hint_offset_x        number   @提示 X 偏移覆盖，默认 0
---@field key_hint_offset_y        number   @提示 Y 偏移覆盖，默认 0
---@field key_press_offset_x       number   @按下 X 偏移覆盖，默认 0
---@field key_press_offset_y       number   @按下 Y 偏移覆盖，默认 0
---@field key_text_color           string   @按键文字颜色覆盖 (hex)，默认 ""
---@field key_back_color           string   @按键背景颜色覆盖，默认 ""
---@field key_symbol_color         string   @按键符号颜色覆盖，默认 ""
---@field hilited_key_text_color   string   @高亮按键文字颜色覆盖，默认 ""
---@field hilited_key_back_color   string   @高亮按键背景颜色覆盖，默认 ""
---@field hilited_key_symbol_color string   @高亮按键符号颜色覆盖，默认 ""
---@field popup                    string[] @弹出选项列表
---@field dynamic                  string   @动态目标键盘 ID，默认 ""
---@field long_click               string   @长按行为 (预设键名/按键名)
---@field swipe_up                 string   @上滑行为
---@field swipe_down               string   @下滑行为
---@field swipe_left               string   @左滑行为
---@field swipe_right              string   @右滑行为
---@field composing                string   @输入状态下的点击行为
---@field double_click             string   @双击行为
---@field lazy_double_click        string   @惰性双击行为
---@field paging                   string   @翻页状态下的标签
---@field has_menu                 string   @有菜单状态下的标签
---@field combo                    string   @并击行为
---@field ascii                    string   @西文模式下的点击行为
---@field extra                    string   @额外行为

---@class Preedit @预编辑区
---@field horizontal_padding   integer @横向内边距 (px)，默认 8
---@field top_end_radius       number  @上端圆角，默认 0
---@field alpha                number  @透明度 (0.0~1.0)，默认 0.8
---@field foreground           table   @前景样式
---@field foreground.font_size number  @字体大小，默认 16

---@class Window @候选窗口
---@field insets        WindowPadding    @窗口内边距，默认 {vertical=4, horizontal=4}
---@field item_padding  WindowPadding    @候选项内边距，默认 {horizontal=4}
---@field min_width     integer          @最小宽度 (px)，默认 0
---@field corner_radius number           @窗口圆角，默认 0
---@field border        integer          @边框宽度，默认 0
---@field shadow        number           @阴影半径，默认 0
---@field alpha         number           @透明度 (0.0~1.0)，默认 1.0
---@field foreground    WindowForeground @前景样式

---@class WindowPadding @内边距
---@field vertical   integer @纵向内边距 (px)，默认 0
---@field horizontal integer @横向内边距 (px)，默认 0

---@class WindowForeground @前景样式
---@field label_font_size   number @序号字体大小，默认 20
---@field text_font_size    number @文本字体大小，默认 20
---@field comment_font_size number @注释字体大小，默认 16

---@class ToolBar @工具栏
---@field primary_button ToolBarButton?  @主按钮，默认 nil
---@field buttons        ToolBarButton[] @按钮列表
---@field button_font    string[]        @按钮字体
---@field back_style     string          @返回按钮图标 ID，默认 "ic@arrow-left"

---@class ToolBarButton @工具栏按钮
---@field background        ToolBarBackground @背景样式，默认 Background()
---@field foreground        ToolBarForeground @前景样式，默认 Foreground()
---@field action            string            @按钮动作，默认 ""
---@field long_press_action string            @长按动作，默认 ""
---@field size              integer[]         @按钮尺寸 [width, height]

---@class ToolBarBackground @工具栏按钮背景
---@field type             string  @背景类型: "rectangle"，默认 "rectangle"
---@field corner_radius    number  @圆角半径，默认 10
---@field normal           string  @正常态颜色 (hex)，默认 ""
---@field highlight        string  @高亮态颜色 (hex)，默认 ""
---@field vertical_inset   integer @纵向内缩 (px)，默认 4
---@field horizontal_inset integer @横向内缩 (px)，默认 4

---@class ToolBarForeground @工具栏按钮前景
---@field style         string   @样式名，默认 ""
---@field option_styles string[] @可选的样式列表
---@field normal        string   @正常态颜色 (hex)，默认 ""
---@field highlight     string   @高亮态颜色 (hex)，默认 ""
---@field font_size     number   @字体大小，默认 18
---@field padding       integer  @内边距 (px)，默认 4

---@class CandidatesTool @候选工具栏
---@field nav_width              integer         @导航按钮宽度，默认 44
---@field popup_width            integer         @弹出窗口宽度，默认 0
---@field popup_text_size        number          @弹出文字大小，默认 0
---@field popup_text_color       string          @弹出文字颜色 (hex)，默认 ""
---@field popup_background_color string          @弹出背景颜色 (hex)，默认 ""
---@field popup_font             string[]        @弹出字体
---@field background             string          @背景样式，默认 ""
---@field separator_color        string          @分隔线颜色 (hex)，默认 ""
---@field button_font            string[]        @按钮字体
---@field buttons                ToolBarButton[] @按钮列表
---@field popup                  table[]         @弹出动作列表

---@class LiquidKeyboard @液态键盘
---@field single_width  integer    @单键宽度 (px)，默认 0
---@field key_height    integer    @键高度 (px)，默认 0
---@field margin_x      number     @水平边距，默认 0
---@field fixed_key_bar KeyBar     @固定按键栏
---@field keyboards     Keyboard[] @键盘列表

---@class KeyBar @固定按键栏
---@field keys     FixedKeyItem[] @固定按键列表
---@field position Position       @位置

---@alias Position
--- | "TOP"    # 顶部
--- | "LEFT"   # 左侧
--- | "BOTTOM" # 底部
--- | "RIGHT"  # 右侧
--- | "NAVBAR" # 导航栏

---@class FixedKeyItem @固定按键项
---@field click            string      @点击动作，默认 ""
---@field label            string      @标签文本，默认 ""
---@field width            number?     @宽度 (px)，默认 nil
---@field height           number?     @高度 (px)，默认 nil
---@field margin           EdgeInsets? @外边距
---@field padding          EdgeInsets? @内边距
---@field is_string_format boolean     @是否字符串格式，默认 false

---@class Keyboard @液态键盘定义
---@field id   string     @键盘 ID，默认 ""
---@field type LiquidType @键盘类型
---@field name string     @键盘名称，默认 ""
---@field keys KeyItem[]  @按键列表

---@alias LiquidType
--- | "SINGLE"     # 单键模式
--- | "SYMBOL"     # 符号模式
--- | "TABS"       # 标签页模式
--- | "HISTORY"    # 历史模式
--- | "VAR_LENGTH" # 变长模式

---@class KeyItem @液态键盘按键
---@field text     string @提交文本，默认 ""
---@field alt_text string @显示文本，默认 ""

---@class EdgeInsets @边距
---@field left   number @左 (px)，默认 0
---@field top    number @上 (px)，默认 0
---@field right  number @右 (px)，默认 0
---@field bottom number @下 (px)，默认 0
