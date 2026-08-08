-- SPDX-FileCopyrightText: 2015 - 2024 Rime community
--
-- SPDX-License-Identifier: GPL-3.0-or-later

-- Catppuccin 示例主题
-- 亮色模式：Catppuccin Latte  暗色模式：Catppuccin Mocha
-- 26 键 QWERTY 布局，覆盖 Lua 主题系统全部可配置项

local latte = {
  rosewater = "0xdc8a78",
  flamingo = "0xdd7878",
  pink = "0xea76cb",
  mauve = "0x8839ef",
  red = "0xd20f39",
  maroon = "0xe64553",
  peach = "0xfe640b",
  yellow = "0xdf8e1d",
  green = "0x40a02b",
  teal = "0x179299",
  sky = "0x04a5e5",
  sapphire = "0x209fb5",
  blue = "0x1e66f5",
  lavender = "0x7287fd",
  text = "0x4c4f69",
  subtext1 = "0x5c5f77",
  subtext0 = "0x6c6f85",
  overlay2 = "0x7c7f93",
  overlay1 = "0x8c8fa1",
  overlay0 = "0x9ca0b0",
  surface2 = "0xacb0be",
  surface1 = "0xbcc0cc",
  surface0 = "0xccd0da",
  base = "0xeff1f5",
  mantle = "0xe6e9ef",
  crust = "0xdce0e8"
}

local mocha = {
  rosewater = "0xf5e0dc",
  flamingo = "0xf2cdcd",
  pink = "0xf5c2e7",
  mauve = "0xcba6f7",
  red = "0xf38ba8",
  maroon = "0xeba0ac",
  peach = "0xfab387",
  yellow = "0xf9e2af",
  green = "0xa6e3a1",
  teal = "0x94e2d5",
  sky = "0x89dceb",
  sapphire = "0x74c7ec",
  blue = "0x89b4fa",
  lavender = "0xb4befe",
  text = "0xcdd6f4",
  subtext1 = "0xbac2de",
  subtext0 = "0xa6adc8",
  overlay2 = "0x9399b2",
  overlay1 = "0x7f849c",
  overlay0 = "0x6c7086",
  surface2 = "0x585b70",
  surface1 = "0x45475a",
  surface0 = "0x313244",
  base = "0x1e1e2e",
  mantle = "0x181825",
  crust = "0x11111b"
}

-- 26 键 QWERTY 布局（共享，中文/英文模式复用）
local qwerty_rows = {
  -- 第 1 行：q w e r t  y u i o p
  row {
    keys = {
      key { click = "q", long_click = "1" },
      key { click = "w", long_click = "2" },
      key { click = "e", long_click = "3" },
      key { click = "r", long_click = "4" },
      key { click = "t", long_click = "5" },
      key { click = "y", long_click = "6" },
      key { click = "u", long_click = "7" },
      key { click = "i", long_click = "8" },
      key { click = "o", long_click = "9" },
      key { click = "p", long_click = "0" }
    }
  },
  -- 第 2 行：a s d f g  h j k l
  row {
    keys = {
      key { width = 0.05, click = "", spacer = true },
      key { click = "a", long_click = "@" },
      key { click = "s", long_click = "#" },
      key { click = "d", long_click = "$" },
      key { click = "f", long_click = "%" },
      key { click = "g", long_click = "!" },
      key { click = "h", long_click = "&" },
      key { click = "j", long_click = "*" },
      key { click = "k", long_click = "_" },
      key { click = "l", long_click = "=" },
      key { width = 0.05, click = "", spacer = true }
    }
  },
  -- 第 3 行：⇧ z x c v b  n m ⌫
  row {
    keys = {
      key { click = "Shift_L", width = 0.15 },
      key { click = "z", long_click = "`" },
      key { click = "x", long_click = "Cut" },
      key { click = "c", long_click = "Copy" },
      key { click = "v", long_click = "Paste" },
      key { click = "b", long_click = ";" },
      key { click = "n", long_click = ":" },
      key { click = "m", long_click = "?" },
      key { click = "BackSpace", width = 0.15 }
    }
  },
  -- 第 4 行：符 中/En ␣ ␣ ␣  ↩
  row {
    keys = {
      key { click = "Keyboard_symbols", long_click = "Keyboard_number", width = 0.12 },
      key { click = "Mode_switch", long_click = "Menu", width = 0.12 },
      key { click = ",", long_click = "<" },
      key {
        click = "space",
        width = 0.32,
        long_click = "liquid_keyboard_switch",
        swipe_left = "Left",
        swipe_right = "Right",
        swipe_up = "Up",
        swipe_down = "Down"
      },
      key { click = ".", long_click = ">" },
      key { click = "/", long_click = "?" },
      key { click = "Return", long_click = "Return", width = 0.15 }
    }
  }
}

-- 基础键盘定义（中文模式）
local base_keyboard = {
  name = "26键 · Catppuccin",
  author = "LyraIME",
  ascii_mode = false,
  lock = true,
  rows = qwerty_rows
}

return theme {
  -- ========================================================================
  -- 元数据
  -- ========================================================================
  name = "Catppuccin",
  version = "1.0",
  author = "LyraIME",

  -- ========================================================================
  -- 全局样式 (GeneralStyle)
  -- 每个属性后注释说明其类型和用途，可注释掉使用默认值
  -- ========================================================================
  style = style {
    -- ── 字体（统一定义在 fonts 子表中）──
    fonts = {
      key = { "symbol.ttf" },
      label = { "label.ttf" },
      latin = { "latin.ttf" },
      hanb = { "hanb.ttf" },
      symbol = { "symbol.ttf" },
      text = { "latin.ttf" },
      hint = { "latin.ttf" },
      candidate = { "han.ttf" },
      comment = { "comment.ttf" },
      popup = { "latin.ttf" },
      clipboard = { "comment.ttf" },
      key_size = 22,
      key_long_size = 14,
      label_size = 22,
      symbol_size = 10,
      hint_size = 12,
      candidate_size = 22,
      candidate_label_size = 14,
      comment_size = 10,
      popup_size = 0,
      clipboard_category_size = 13,
      clipboard_size = 14,
      t9_side_size = -1
    },

    -- 键盘
    keyboard_height = 240,                   -- [int] 竖屏键盘高度 (px)
    keyboard_height_land = 200,              -- [int] 横屏键盘高度 (px)
    horizontal_gap = 1,                      -- [int] 键水平间距 (px)
    vertical_gap = 1,                        -- [int] 键盘行距 (px)
    round_corner = 8,                        -- [float] 按键圆角半径
    shadow_radius = 0.0,                     -- [float] 按键阴影半径
    key_border = 0,                          -- [int] 按键边框宽度

    -- 键盘边距（竖屏）
    keyboard_padding = 0,                    -- [int] 左右与屏幕的距离
    keyboard_padding_bottom = 0,             -- [int] 底部距离（避免触发全面屏手势）
    keyboard_padding_top = 0,                -- [int] 顶部距离

    -- 键盘边距（横屏）
    keyboard_padding_land = 40,              -- [int] 横屏左右距离
    keyboard_padding_land_bottom = 0,        -- [int] 横屏底部距离

    -- 按键文本偏移（微调定位）
    key_text_offset_x = 0,                   -- [float] X 偏移
    key_text_offset_y = 0,                   -- [float] Y 偏移
    key_symbol_offset_x = 0,                 -- [float] 符号 X 偏移
    key_symbol_offset_y = 0,                 -- [float] 符号 Y 偏移
    key_hint_offset_x = 0,                   -- [float] 提示 X 偏移
    key_hint_offset_y = 0,                   -- [float] 提示 Y 偏移
    key_press_offset_x = 0,                  -- [float] 按下时 X 偏移
    key_press_offset_y = 0,                  -- [float] 按下时 Y 偏移

    -- 候选栏
    candidate_view_height = 28,              -- [int] 候选区高度
    candidate_padding = 5,                   -- [int] 候选项内边距
    candidate_spacing = 0.0,                 -- [float] 候选间距
    candidate_text_vertical_bias = 1.0,      -- [float] 候选文本垂直偏移
    candidate_border = 0,                    -- [int] 候选边框
    candidate_border_round = 0,              -- [float] 候选边框圆角
    candidate_corner_radius = 5,             -- [float] 候选项圆角半径

    -- 编码注释
    comment_height = 12,                     -- [int] 编码提示区高度
    comment_vertical_bias = 0.0,             -- [float] 注释垂直偏移 (overlay 模式)
    comment_position = "RIGHT",              -- [CommentPosition] 位置: RIGHT | TOP | OVERLAY
    candidate_label = false,                 -- [bool] 候選序號顯示

    -- 悬浮提示
    popup_bottom_margin = 0,                 -- [int] 底部边距
    popup_width = 0,                         -- [int] 宽度
    popup_height = 0,                        -- [int] 高度
    popup_key_height = 0,                    -- [int] 键高度

    -- 剪贴板
    -- 字体/字号在 fonts 子表中

    -- 回车键文本
    enter_label_mode = 0,                    -- [int] ActionLabel 模式: 0=不使用 1=仅action 2=优先 3=fallback
    enter_labels = { -- [EnterLabel] 回车键文本
      go = "前往",
      done = "完成",
      next = "下个",
      pre = "上个",
      search = "搜索",
      send = "发送",
      default = "Enter"
    },

    -- T9 侧栏
    t9_side_round_corner = -1,               -- [float] T9 侧栏圆角 (-1 = 跟随 round_corner)

    -- 其他
    auto_caps = false,                       -- [bool] 自动句首大写
    background_folder = "backgrounds",       -- [string] 背景图存放子目录
    reset_ascii_mode_on_focus_change = false -- [bool] 焦点变更时重置 ascii 模式

    -- 字体变体
  },

  -- ========================================================================
  -- 预编辑区 (Preedit)
  -- ========================================================================
  preedit = preedit {
    horizontal_padding = 8, -- [int] 横向内边距
    top_end_radius = 4,     -- [float] 上端圆角
    alpha = 0.8,            -- [float] 透明度 (0.0～1.0)
    foreground = { -- [Foreground] 前景样式
      font_size = 16 -- [float] 字号
    }
  },

  -- ========================================================================
  -- 候选窗口 / 悬浮窗 (Window)
  -- ========================================================================
  window = window {
    insets = { vertical = 4, horizontal = 4 }, -- [Padding] 窗口内边距
    item_padding = { horizontal = 4 },         -- [Padding] 候选项内边距
    min_width = 0,                             -- [int] 最小宽度
    corner_radius = 8,                         -- [float] 窗口圆角
    border = 0,                                -- [int] 边框宽度
    shadow = 0.0,                              -- [float] 阴影半径
    alpha = 1.0,                               -- [float] 透明度 (0.0～1.0)
    foreground = { -- [Foreground] 前景样式
      label_font_size = 20,  -- [float] 序号字号
      text_font_size = 20,   -- [float] 文本字号
      comment_font_size = 16 -- [float] 注释字号
    }
  },

  -- ========================================================================
  -- 工具栏 (ToolBar)
  -- ========================================================================
  tool_bar = toolbar {
    back_style = "ic@arrow-left" -- [string] 返回按钮图标
    -- primary_button = btn { ... },        -- 可自定义主按钮
    -- buttons = { btn { ... }, ... },      -- 可添加更多按钮
  },

  -- ========================================================================
  -- 候选工具栏 (CandidatesTool)
  -- ========================================================================
  -- candidates_tool = { ... },             -- 可选，不配置时隐藏

  -- ========================================================================
  -- 液态键盘 (LiquidKeyboard)
  -- ========================================================================
  -- liquid_keyboard = liquid { ... },      -- 可选，不配置时使用默认值

  -- ========================================================================
  -- 回退颜色 (FallbackColors)
  -- 当配色方案中缺少某个颜色键时，按此表重定向到其他颜色键
  -- ========================================================================
  fallback_colors = fallback { candidate_text_color = "text_color" },

  -- ========================================================================
  -- 配色方案 (ColorScheme)
  -- 使用 Catppuccin 调色板：Latte (亮色) + Mocha (暗色)
  -- ========================================================================
  preset_color_schemes = {
    -- 亮色模式（Catppuccin Latte）
    scheme("latte", {
      -- 基础色
      text_color = latte.text,                     -- 编码文字
      back_color = latte.base,                     -- 候选区背景
      border_color = latte.crust,                  -- 边框
      candidate_separator_color = latte.crust,     -- 候选分割线
      candidate_text_color = latte.text,           -- 候选文字
      comment_text_color = latte.overlay0,         -- 编码注释
      label_color = latte.mauve,                   -- 候选项序号

      -- 高亮色
      hilited_text_color = latte.blue,             -- 标明的编码
      hilited_back_color = latte.surface0,         -- 标明的编码背景
      hilited_candidate_text_color = latte.base,   -- 标明的候选文字
      hilited_candidate_back_color = latte.blue,   -- 标明的候选背景
      hilited_comment_text_color = latte.overlay0, -- 标明的注释

      -- 按键色
      key_back_color = latte.mantle,               -- 按键背景
      key_text_color = latte.text,                 -- 按键文字
      key_symbol_color = latte.overlay0,           -- 按键符号
      key_border_color = latte.crust,              -- 按键边框
      hilited_key_back_color = latte.surface0,     -- 标明的按键背景
      hilited_key_text_color = latte.blue,         -- 标明的按键文字
      hilited_key_symbol_color = latte.overlay0,   -- 标明的按键符号

      -- 开关状态键
      off_key_back_color = latte.surface0,         -- 关闭状态背景
      off_key_text_color = latte.text,             -- 关闭状态文字
      on_key_back_color = latte.blue,              -- 打开状态背景
      on_key_text_color = latte.base,              -- 打开状态文字
      hilited_off_key_back_color = latte.surface0, -- 标明-关闭背景
      hilited_off_key_text_color = latte.blue,     -- 标明-关闭文字
      hilited_on_key_back_color = latte.blue,      -- 标明-打开背景
      hilited_on_key_text_color = latte.base,      -- 标明-打开文字

      -- 预览/阴影
      preview_back_color = latte.surface0,         -- 按键提示背景
      preview_text_color = latte.blue,             -- 按键提示文字
      shadow_color = latte.crust,                  -- 阴影

      -- 键盘/背景
      keyboard_back_color = latte.base,            -- 键盘背景
      text_back_color = latte.mantle,              -- 编码区背景
      root_background = latte.base,                -- 全局背景

      -- 剪贴板
      long_text_back_color = latte.mantle,         -- 长文本/剪贴板背景
      clipboard_checkbox_color = latte.mauve,      -- 剪贴板复选框

      -- 候选栏按钮
      hilited_candidate_button_color = latte.overlay0,

      -- T9 侧栏
      t9_side_back_color = latte.mantle,
      t9_side_hilited_back_color = latte.surface0,
      t9_side_text_color = latte.text,
      t9_side_border_color = latte.crust,
      t9_side_spacing_color = latte.crust,

      -- 上下文
      light_scheme = "latte",                      -- 亮色模式下标记自身
      dark_scheme = "mocha"                        -- 暗色模式切换到 mocha
    }),

    -- 暗色模式（Catppuccin Mocha）
    scheme("mocha", {
      text_color = mocha.text,
      back_color = mocha.base,
      border_color = mocha.crust,
      candidate_separator_color = mocha.crust,
      candidate_text_color = mocha.text,
      comment_text_color = mocha.overlay0,
      label_color = mocha.mauve,

      hilited_text_color = mocha.blue,
      hilited_back_color = mocha.surface0,
      hilited_candidate_text_color = mocha.base,
      hilited_candidate_back_color = mocha.blue,
      hilited_comment_text_color = mocha.overlay0,

      key_back_color = mocha.mantle,
      key_text_color = mocha.text,
      key_symbol_color = mocha.overlay0,
      key_border_color = mocha.crust,
      hilited_key_back_color = mocha.surface0,
      hilited_key_text_color = mocha.blue,
      hilited_key_symbol_color = mocha.overlay0,

      off_key_back_color = mocha.surface0,
      off_key_text_color = mocha.text,
      on_key_back_color = mocha.blue,
      on_key_text_color = mocha.base,
      hilited_off_key_back_color = mocha.surface0,
      hilited_off_key_text_color = mocha.blue,
      hilited_on_key_back_color = mocha.blue,
      hilited_on_key_text_color = mocha.base,

      preview_back_color = mocha.surface0,
      preview_text_color = mocha.blue,
      shadow_color = mocha.crust,

      keyboard_back_color = mocha.base,
      text_back_color = mocha.mantle,
      root_background = mocha.base,

      long_text_back_color = mocha.mantle,
      clipboard_checkbox_color = mocha.mauve,

      hilited_candidate_button_color = mocha.overlay0,

      t9_side_back_color = mocha.mantle,
      t9_side_hilited_back_color = mocha.surface0,
      t9_side_text_color = mocha.text,
      t9_side_border_color = mocha.crust,
      t9_side_spacing_color = mocha.crust,

      dark_scheme = "mocha",
      light_scheme = "latte"
    })
  },

  -- ========================================================================
  -- 预设按键 (PresetKey)
  -- 按键行为定义，键盘布局中通过 click 引用键名
  -- ========================================================================
  preset_keys = {
    -- 编辑键
    Shift_L = { label = "⇧", send = "Shift_L", shift_lock = "ascii_long" },
    BackSpace = { label = "⌫", send = "BackSpace", repeatable = true },
    Return = { label = "↩", send = "Return" },
    space = { label = " ", send = "space", repeatable = false, functional = false },
    Hide = { label = "∨", send = "BACK" },

    -- 键盘切换
    Keyboard_symbols = { label = "符", send = "Eisu_toggle", select = "symbols" },
    Keyboard_number = { label = "数", send = "Eisu_toggle", select = "number" },
    Keyboard_default = { label = "返", send = "Eisu_toggle", select = ".default" },

    -- 模式切换
   Mode_switch = { label = "中/En", toggle = "ascii_mode", send = "SWITCH_CHARSET", states = { "中文", "西文" } },
   Zenkaku_Hankaku = { toggle = "full_shape", send = "SWITCH_CHARSET", states = { "半角", "全角" } },

    -- 文本操作
    Left = { label = "←", send = "Left" },
    Right = { label = "→", send = "Right" },
    Up = { label = "↑", send = "Up" },
    Down = { label = "↓", send = "Down" },
    Home = { label = "⇱", send = "Home" },
    End = { label = "⇲", send = "End" },
    Cut = { label = "ic@content_copy", send = "Control+x" },
    Copy = { label = "ic@content_copy", send = "Control+c" },
    Paste = { label = "ic@content_paste", send = "Control+v" },
    select_all = { label = "全", send = "Control+a" },

    -- Rime 专用
    F4 = { label = "⚙", send = "F4" },
    Menu = { label = "☰", send = "MENU" },

    -- 液态键盘
   liquid_keyboard_switch = { label = "…", send = "FUNCTION", command = "liquid_keyboard", option = "更多" },
   liquid_keyboard_exit = { label = "返", send = "FUNCTION", command = "liquid_keyboard", option = "-1" }
  },

  -- ========================================================================
  -- 预设键盘布局 (TextKeyboard)
  -- 26 键 QWERTY 布局，含中文模式 (default) 与英文模式 (letter)
  -- 两组键盘复用同一套 rows 定义，仅 ascii_mode 等属性不同
  -- ========================================================================
  preset_keyboards = {
    default = keyboard(base_keyboard),

    -- 英文模式键盘（merge 基础定义 + 覆盖 ascii_mode/lock，等效 YAML __patch）
    --
    -- 更多 merge/insert 示例:
    --   添加一整行:       merge(base, { rows = insert(base.rows, 5, row { ... }) })
    --   插入一个按键:     merge(base, { rows = { [2] = { keys = insert(base.rows[2].keys, 3, key { ... }) } } })
    --   修改嵌套字段:     merge(base, { rows = { [3] = { keys = { [2] = { long_click = "x" } } } } })
    letter = keyboard(merge(base_keyboard, {
        name = "26键 (英文) · Catppuccin",
        ascii_mode = true,
        reset_ascii_mode = true,
        lock = false
      }))
  }
}
