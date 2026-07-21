--[[

 ██╗   ██╗   ██╗ ██████╗   █████╗     ██╗ ███╗   ███╗ ███████╗
 ██║   ╚██╗ ██╔╝ ██╔══██╗ ██╔══██╗    ██║ ████╗ ████║ ██╔════╝
 ██║    ╚████╔╝  ██████╔╝ ███████║    ██║ ██╔████╔██║ █████╗
 ██║     ╚██╔╝   ██╔══██╗ ██╔══██║    ██║ ██║╚██╔╝██║ ██╔══╝
 ███████╗ ██║    ██║  ██║ ██║  ██║    ██║ ██║ ╚═╝ ██║ ███████╗
 ╚══════╝ ╚═╝    ╚═╝  ╚═╝ ╚═╝  ╚═╝    ╚═╝ ╚═╝     ╚═╝ ╚══════╝

 键盘主题 Lua DSL · 类型补全与文档
 ===========================================

 本文件为 LyraIME 键盘主题系统提供完整的 VSCode / Lua Language Server 类型补全。
 所有注释使用中文，字段名与 JSON 序列化格式（snake_case）一致。

 用法：
   1. 本文件位于 doc/ 目录，由仓库根目录的 .luarc.json 自动加载
   2. 在 VSCode / Lua 插件中编辑任意 .lua 主题文件即可获得自动补全和悬停提示
   3. 无需在主题文件中 require 本文件——补全由语言服务器自动提供

 主题工作流：
   主题文件 (theme.lua) → Lua 5.5 沙箱执行 → JSON 序列化 → Kotlin Theme 数据类

 可用全局函数（沙箱注入）：
   theme(t)            —— 声明根主题表（顶层必须调用）
   style(t)            —— 声明全局样式
   keyboard(t)         —— 声明键盘布局
   row(t)              —— 声明键盘行
   key(t)              —— 声明按键
   preedit(t)          —— 声明预编辑区样式
   window(t)           —— 声明候选窗口样式
   toolbar(t)          —— 声明工具栏
   btn(t)              —— 声明工具栏按钮
   bg(t)               —— 声明按钮背景
   fg(t)               —— 声明按钮前景
   liquid(t)           —— 声明液态键盘
   fallback(t)         —— 声明回退颜色映射
   scheme(id, t)       —— 声明配色方案
   pk(id, t)           —— 声明预设按键
   merge(a, b)         —— 深度合并两个表
   insert(t, pos, val) —- 在数组指定位置插入值

 颜色值格式：
   字符串类型。支持 0xAARRGGBB、0xRRGGBB、#AARRGGBB、#RRGGBB、颜色名（如 "red"），
   或图片文件路径（如 "background.png"）。

 目录结构：
   themes/
   ├── catppuccin.lua       (内建主题示例)
   ├── lib/                 (可递归搜索的 Lua 模块)
   ├── fonts/               (自定义字体)
   ├── backgrounds/         (背景图片)
   └── build/               (缓存 JSON 输出)
   doc/
   └── theme-dsl.lua        (本文件——类型补全)

 参考：
   模型代码：app/src/main/java/com/osfans/trime/data/theme/model/
   沙箱代码：app/src/main/jni/lua_theme_jni/lua_sandbox.cc
--]]

-- ============================================================================
-- 颜色类型与枚举
-- ============================================================================

--- 颜色值。支持 0xAARRGGBB、0xRRGGBB、#RRGGBB、颜色名、图片路径
---@alias Color string

--- 已知按键名。包含 Android KeyEvent 键码、Rime 功能键、预设按键名。
--- 直接字符键（如 "a"、"1"）和 preset_keys 中自定义的键名可任意使用。
---@alias KeyName
---| '"a"'
---| '"b"'
---| '"c"'
---| '"d"'
---| '"e"'
---| '"f"'
---| '"g"'
---| '"h"'
---| '"i"'
---| '"j"'
---| '"k"'
---| '"l"'
---| '"m"'
---| '"n"'
---| '"o"'
---| '"p"'
---| '"q"'
---| '"r"'
---| '"s"'
---| '"t"'
---| '"u"'
---| '"v"'
---| '"w"'
---| '"x"'
---| '"y"'
---| '"z"'
---| '"0"'
---| '"1"'
---| '"2"'
---| '"3"'
---| '"4"'
---| '"5"'
---| '"6"'
---| '"7"'
---| '"8"'
---| '"9"'
---| '","'
---| '"."'
---| '"?"'
---| '"!"'
---| '"@"'
---| '"#"'
---| '"$"'
---| '"%"'
---| '"&"'
---| '"*"'
---| '"_"'
---| '"="'
---| '"`"'
---| '"~"'
---| '"<"'
---| '">"'
---| '"\\"'
---| '"/"'
---| '"("'
---| '")"'
---| '"["'
---| '"]"'
---| '"{"'
---| '"}"'
---| '";"'
---| '":"'
---| '"\'"'
---| '"|"'
---| '"^"'
---| '"Return"'
---| '"BackSpace"'
---| '"space"'
---| '"Shift_L"'
---| '"Shift_R"'
---| '"Left"'
---| '"Right"'
---| '"Up"'
---| '"Down"'
---| '"Home"'
---| '"End"'
---| '"Delete"'
---| '"Escape"'
---| '"Tab"'
---| '"Page_Up"'
---| '"Page_Down"'
---| '"Insert"'
---| '"Num_Lock"'
---| '"F1"'
---| '"F2"'
---| '"F3"'
---| '"F4"'
---| '"F5"'
---| '"F6"'
---| '"F7"'
---| '"F8"'
---| '"F9"'
---| '"F10"'
---| '"F11"'
---| '"F12"'
---| '"Cut"'
---| '"Copy"'
---| '"Paste"'
---| '"SelectAll"'
---| '"Undo"'
---| '"Redo"'
---| '"Control+x"'
---| '"Control+c"'
---| '"Control+v"'
---| '"Control+a"'
---| '"Control+z"'
---| '"Control+y"'
---| '"Control+d"'
---| '"Keyboard_symbols"'
---| '"Keyboard_number"'
---| '"Keyboard_default"'
---| '"liquid_keyboard_switch"'
---| '"liquid_keyboard_exit"'
---| '"Eisu_toggle"'
---| '"Mode_switch"'
---| '"BACK"'
---| '"Menu"'
---| '"ContextMenu"'
---| '"MediaPlayPause"'
---| '"MediaStop"'
---| '"MediaNext"'
---| '"MediaPrevious"'
---| '"VOICE_ASSIST"'
---| '"VolumeUp"'
---| '"VolumeDown"'
---| '"VolumeMute"'
---| '"function"'
---| string

-- ============================================================================
-- 枚举定义
-- ============================================================================

--- 编码注释位置
---@alias CommentPosition
---| '"RIGHT"'              # 右侧（默认）
---| '"TOP"'                # 顶部
---| '"OVERLAY"'            # 覆盖
---| string                 # 任意文本

--- 按键标签转换模式
---@alias LabelTransform
---| '"NONE"'               # 不转换（默认）
---| '"UPPERCASE"'          # 大写
---| string                 # 任意文本

--- 按键行为枚举（序列化时为小写 snake_case）
---@alias KeyBehaviorKey
---| '"composing"'          # 输入中点击
---| '"has_menu"'           # 有候选菜单时点击
---| '"paging"'             # 翻页时点击
---| '"combo"'              # 组合键
---| '"ascii"'              # 英文模式键
---| '"click"'              # 普通点击
---| '"double_click"'       # 双击
---| '"lazy_double_click"'  # 延迟双击
---| '"swipe_up"'           # 上滑
---| '"long_click"'         # 长按
---| '"swipe_down"'         # 下滑
---| '"swipe_left"'         # 左滑
---| '"swipe_right"'        # 右滑
---| '"extra"'              # 扩展弹窗按键

--- Shift 锁定模式
---@alias ShiftLock
---| '"long"'               # 长按锁定
---| '"click"'              # 单击锁定
---| '"ascii_long"'         # 英文模式长按锁定

--- 预设按键命令类型。对应 option 字段传递参数。
---@alias Command
---| '"liquid_keyboard"'    # 打开液态键盘 (option: 面板标签或类型)
---| '"menu_keyboard"'      # 打开设置菜单
---| '"clipboard_window"'   # 打开剪贴板窗口 (option: "0"=全部 "1"=收藏)
---| '"set_color_scheme"'   # 切换配色方案 (option: 方案ID)
---| '"set_theme"'          # 切换主题 (option: 主题名) 或刷新 (option: "$reload")
---| '"broadcast"'          # 发送广播 (option: Intent action)
---| '"clipboard"'          # 剪贴板操作
---| '"commit"'             # 直接提交文本 (option: 提交内容)
---| '"date"'               # 提交格式化日期 (option: 日期格式)
---| '"run"'                # 运行命令 (option: 命令路径)
---| '"apply"'              # 应用配置 (option: 配置内容)
---| '"share_text"'         # 分享文本
---| '"select_candidate"'   # 选择候选 (option: 候选序号)
---| '"t9_clear"'           # 清除 T9 输入
---| '"dynamic_clear"'      # 清除动态键盘输入

--- 键盘切换目标。以 "." 开头的值由 evalKeyboard 特殊解析。
--- 其他值为 preset_keyboards 中定义的键盘 ID（如 "symbols"、"number"）。
---@alias Select
---| '".default"'           # 智能匹配默认键盘
---| '".prior"'             # 前一个键盘
---| '".next"'              # 后一个键盘
---| '".last"'              # 上次使用的键盘
---| '".previous"'          # 来源键盘（回退）
---| '".last_lock"'         # 上次锁定的键盘
---| '".ascii"'             # 英文模式键盘
---| string                 # preset_keyboards 中的自定义 ID

--- 液态键盘固定按键栏位置
---@alias KeyBarPosition
---| '"TOP"'                # 顶部
---| '"LEFT"'               # 左侧
---| '"BOTTOM"'             # 底部（默认）
---| '"RIGHT"'              # 右侧
---| '"NAVBAR"'             # 导航栏区域

--- 液态键盘面板类型
---@alias LiquidKeyboardType
---| '"SINGLE"'             # 单面板
---| '"SYMBOL"'             # 符号面板
---| '"TABS"'               # 标签面板
---| '"HISTORY"'            # 历史面板
---| '"VAR_LENGTH"'         # 变长面板

-- ============================================================================
-- 颜色方案 (ColorScheme)
-- ============================================================================

--- 配色方案颜色表
--- 所有字段均为可选的 Color 类型字符串，未指定的颜色将按回退链解析。
---@class SchemeColors
---@field text_color                              Color        # 编码文字颜色（必须定义）
---@field back_color                              Color        # 候选区背景（必须定义）
---@field border_color?                           Color        # 边框颜色（回退至 back_color）
---@field candidate_separator_color?              Color        # 候选分割线（回退至 border_color）
---@field candidate_text_color?                   Color        # 候选文字（回退至 text_color）
---@field comment_text_color?                     Color        # 编码注释（回退至 candidate_text_color）
---@field label_color?                            Color        # 候选序号/标签（回退至 candidate_text_color）
---@field hilited_text_color?                     Color        # 标明的编码文字（回退至 text_color）
---@field hilited_back_color?                     Color        # 标明的编码背景（回退至 back_color）
---@field hilited_candidate_text_color?           Color        # 标明的候选文字（回退至 hilited_text_color）
---@field hilited_candidate_back_color?           Color        # 标明的候选背景（回退至 hilited_back_color）
---@field hilited_candidate_button_color?         Color        # 标明的候选按钮（回退至 hilited_candidate_back_color）
---@field hilited_label_color?                    Color        # 标明的标签（回退至 hilited_candidate_text_color）
---@field hilited_comment_text_color?             Color        # 标明的注释（回退至 comment_text_color）
---@field key_back_color?                         Color        # 按键背景（回退至 back_color）
---@field key_text_color?                         Color        # 按键文字（回退至 candidate_text_color）
---@field key_symbol_color?                       Color        # 按键符号（回退至 comment_text_color）
---@field key_border_color?                       Color        # 按键边框（回退至 border_color）
---@field hilited_key_back_color?                 Color        # 标明的按键背景（回退至 hilited_candidate_back_color）
---@field hilited_key_text_color?                 Color        # 标明的按键文字（回退至 hilited_candidate_text_color）
---@field hilited_key_symbol_color?               Color        # 标明的按键符号（回退至 hilited_comment_text_color）
---@field off_key_back_color?                     Color        # 关闭状态按键背景（回退至 key_back_color）
---@field off_key_text_color?                     Color        # 关闭状态按键文字（回退至 key_text_color）
---@field off_key_symbol_color?                   Color        # 关闭状态按键符号（回退至 key_symbol_color）
---@field on_key_back_color?                      Color        # 打开状态按键背景（回退至 hilited_key_back_color）
---@field on_key_text_color?                      Color        # 打开状态按键文字（回退至 hilited_key_text_color）
---@field on_key_symbol_color?                    Color        # 打开状态按键符号（回退至 hilited_key_symbol_color）
---@field hilited_off_key_back_color?             Color        # 标明-关闭按键背景（回退至 hilited_key_back_color）
---@field hilited_off_key_text_color?             Color        # 标明-关闭按键文字（回退至 hilited_key_text_color）
---@field hilited_off_key_symbol_color?           Color        # 标明-关闭按键符号（回退至 hilited_key_symbol_color）
---@field hilited_on_key_back_color?              Color        # 标明-打开按键背景（回退至 hilited_key_back_color）
---@field hilited_on_key_text_color?              Color        # 标明-打开按键文字（回退至 hilited_key_text_color）
---@field hilited_on_key_symbol_color?            Color        # 标明-打开按键符号（回退至 hilited_key_symbol_color）
---@field preview_back_color?                     Color        # 按键预览/弹出背景（回退至 key_back_color）
---@field preview_text_color?                     Color        # 按键预览/弹出文字（回退至 key_text_color）
---@field hilited_preview_back_color?             Color        # 标明预览背景（回退至 hilited_key_back_color）
---@field hilited_preview_text_color?             Color        # 标明预览文字（回退至 hilited_key_text_color）
---@field popup_back_color?                       Color        # 弹窗背景（回退至 key_back_color）
---@field popup_text_color?                       Color        # 弹窗文字（回退至 key_text_color）
---@field hilited_popup_back_color?               Color        # 标明弹窗背景（回退至 hilited_key_back_color）
---@field hilited_popup_text_color?               Color        # 标明弹窗文字（回退至 hilited_key_text_color）
---@field shadow_color?                           Color        # 阴影颜色（回退至 border_color）
---@field root_background?                        Color        # 全局/根背景（回退至 back_color）
---@field candidate_background?                   Color        # 候选区背景（回退至 back_color）
---@field candidate_border_color?                 Color        # 候选边框（回退至 border_color）
---@field keyboard_back_color?                    Color        # 键盘背景色（回退至 border_color）
---@field keyboard_background?                    Color        # 键盘背景（回退至 keyboard_back_color）
---@field liquid_keyboard_background?             Color        # 液态键盘背景（回退至 keyboard_back_color）
---@field text_back_color?                        Color        # 编码区背景（回退至 back_color）
---@field long_text_color?                        Color        # 长文本/剪贴板文字（回退至 key_text_color）
---@field long_text_back_color?                   Color        # 长文本/剪贴板背景（回退至 key_back_color）
---@field clipboard_category_back_color?          Color        # 剪贴板分类标签背景（回退至 key_back_color）
---@field clipboard_category_selected_back_color? Color        # 剪贴板选中标签背景（回退至 hilited_candidate_back_color）
---@field clipboard_category_selected_text_color? Color        # 剪贴板选中标签文字（回退至 hilited_candidate_text_color）
---@field clipboard_entry_back_color?             Color        # 剪贴板条目背景（回退至 key_back_color）
---@field hilited_clipboard_entry_back_color?     Color        # 剪贴板选中条目背景（回退至 hilited_candidate_back_color）
---@field clipboard_checkbox_color?               Color        # 剪贴板复选框颜色（回退至 key_text_color）
---@field t9_side_back_color?                     Color        # T9 侧栏背景（回退至 key_back_color）
---@field t9_side_hilited_back_color?             Color        # T9 侧栏高亮背景（回退至 hilited_key_back_color）
---@field t9_side_text_color?                     Color        # T9 侧栏文字（回退至 key_text_color）
---@field t9_side_border_color?                   Color        # T9 侧栏边框（回退至 key_border_color）
---@field t9_side_spacing_color?                  Color        # T9 侧栏间距颜色（回退至 key_border_color）
---@field light_scheme?                           string       # 亮色模式切换目标配色 ID
---@field dark_scheme?                            string       # 暗色模式切换目标配色 ID

--- 配色方案
---@class ColorScheme
---@field id                                      string       # 配色方案唯一标识符
---@field colors                                  SchemeColors # 颜色键值对表

-- ============================================================================
-- 预设按键 (PresetKey)
-- ============================================================================

--- 预设按键行为定义
--- 在 preset_keys 表中以键名为标识符（如 preset_keys = { Space = {...} }）
---@class PresetKey
---@field command?        Command   # 命令类型（如 "function" 用于调用内置功能）
---@field option?         string    # 命令选项值
---@field select?         Select    # 键盘切换目标（如 "symbols"、"number"、".default"）
---@field toggle?         string    # Rime 运行时开关名称（如 "ascii_mode"、"full_shape"）
---@field label?          string    # 按键显示标签
---@field preview?        string?   # 预览文字（nil 时使用 label）
---@field shift_lock?     ShiftLock # Shift 锁定模式： "long" | "click" | "ascii_long"
---@field commit?         string    # 直接提交的文本
---@field text?           string    # 多步动作序列（支持 {PresetKey} / {KeyCode} 序列）
---@field actions?        string[]  # 多步动作序列（PresetKey 名 或 KeyCode）
---@field sticky?         boolean   # 是否粘滞键（按下后保持激活）
---@field repeatable?     boolean   # 是否可长按重复
---@field slide_cursor?   boolean   # 是否启用滑动光标
---@field slide_delete?   boolean   # 是否启用滑动删除
---@field functional?     boolean   # 是否为功能键（不计入文本输入）
---@field states?         string[]  # 开关按键的双态标签 [off_label, on_label]
---@field send?           KeyName   # 发送的按键事件或键码（如 "space"、"BackSpace"、"Return"）

-- ============================================================================
-- 全局样式 (GeneralStyle) 及子类型
-- ============================================================================

--- 字体样式
---@class FontStyle
---@field key?                     string[]              # 按键标签字体文件列表
---@field label?                   string[]              # 按键副标签字体文件列表
---@field latin?                   string[]              # 拉丁字符字体文件列表
---@field hanb?                    string[]              # 汉字字体文件列表
---@field symbol?                  string[]              # 符号字体文件列表
---@field text?                    string[]              # 文字字体文件列表
---@field hint?                    string[]              # 提示字体文件列表
---@field candidate?               string[]              # 候选字体文件列表
---@field comment?                 string[]              # 注释字体文件列表
---@field popup?                   string[]              # 弹窗字体文件列表
---@field clipboard?               string[]              # 剪贴板字体文件列表
---@field t9_side?                 string[]              # T9 侧栏字体文件列表
---@field clipboard_category?      string[]              # 剪贴板分类标签字体文件列表
---@field key_size?                number                # 按键标签字号（默认 15）
---@field key_long_size?           number                # 长按键字号（默认 15）
---@field label_size?              number                # 副标签字号（默认 0）
---@field symbol_size?             number                # 符号字号（默认 0）
---@field hint_size?               number                # 提示字号（默认 0）
---@field candidate_size?          number                # 候选字号（默认 15）
---@field comment_size?            number                # 注释字号（默认 10）
---@field popup_size?              number                # 弹窗字号（默认 0）
---@field clipboard_category_size? number                # 剪贴板标签字号（默认 13）
---@field clipboard_size?          number                # 剪贴板字号（默认 14）
---@field t9_side_size?            number                # T9 侧栏字号（默认 -1）
---@field variations?              { [string]: boolean } # 字体变体特性（如 { cpct = true }）
---@field display?                 { [string]: string }  # Unicode 异体字显示映射

--- 回车键标签文本
---@class EnterLabel
---@field go?       string # 前往（默认 "go"）
---@field done?     string # 完成（默认 "done"）
---@field next?     string # 下个（默认 "next"）
---@field pre?      string # 上个（默认 "pre"）
---@field search?   string # 搜索（默认 "search"）
---@field send?     string # 发送（默认 "send"）
---@field default?  string # 默认回车文本（默认 "default"）

--- 全局样式
---@class GeneralStyle
---@field fonts?                              FontStyle       # 字体样式子表
---@field keyboard_height?                    integer         # 竖屏键盘高度 (px)
---@field keyboard_height_land?               integer         # 横屏键盘高度 (px)
---@field horizontal_gap?                     integer         # 键水平间距 (px)
---@field vertical_gap?                       integer         # 键盘行距 (px)
---@field round_corner?                       number          # 按键圆角半径
---@field shadow_radius?                      number          # 按键阴影半径
---@field key_border?                         integer         # 按键边框宽度
---@field keyboard_padding?                   integer         # 键盘左右边距 (px)
---@field keyboard_padding_bottom?            integer         # 键盘底部边距（避免触发全面屏手势）
---@field keyboard_padding_top?               integer         # 键盘顶部边距
---@field keyboard_padding_land?              integer         # 横屏键盘左右边距
---@field keyboard_padding_land_bottom?       integer         # 横屏键盘底部边距
---@field key_text_offset_x?                  number          # 按键文字 X 偏移
---@field key_text_offset_y?                  number          # 按键文字 Y 偏移
---@field key_symbol_offset_x?                number          # 符号 X 偏移
---@field key_symbol_offset_y?                number          # 符号 Y 偏移
---@field key_hint_offset_x?                  number          # 提示 X 偏移
---@field key_hint_offset_y?                  number          # 提示 Y 偏移
---@field key_press_offset_x?                 number          # 按下时 X 偏移
---@field key_press_offset_y?                 number          # 按下时 Y 偏移
---@field candidate_view_height?              integer         # 候选区高度 (px)
---@field candidate_padding?                  integer         # 候选项内边距 (px)
---@field candidate_spacing?                  number          # 候选间距 (px)
---@field candidate_text_vertical_bias?       number          # 候选文字垂直偏移 (0.0~1.0，默认 1.0)
---@field candidate_border?                   integer         # 候选边框宽度
---@field candidate_border_round?             number          # 候选边框圆角
---@field candidate_corner_radius?            number          # 候选项圆角半径
---@field comment_height?                     integer         # 编码注释区高度
---@field comment_vertical_bias?              number          # 注释垂直偏移(overlay 模式)
---@field comment_position?                   CommentPosition # 注释位置（RIGHT / TOP / OVERLAY）
---@field popup_bottom_margin?                integer         # 悬浮提示底部边距
---@field popup_width?                        integer         # 悬浮提示宽度
---@field popup_height?                       integer         # 悬浮提示高度
---@field popup_key_height?                   integer         # 悬浮提示按键高度
---@field enter_label_mode?                   integer         # 回车键文本模式：0=不使用 1=仅action 2=优先 3=回退
---@field enter_labels?                       EnterLabel      # 回车键标签文本
---@field t9_side_round_corner?               number          # T9 侧栏圆角（-1=跟随 round_corner）
---@field auto_caps?                          boolean         # 自动句首大写
---@field background_folder?                  string          # 背景图存放子目录（默认 "backgrounds"）
---@field reset_ascii_mode_on_focus_change?   boolean         # 焦点变更时重置英文模式

-- ============================================================================
-- 预编辑区 (Preedit)
-- ============================================================================

---@class PreeditForeground
---@field font_size? number # 预编辑区字号（默认 16）

--- 预编辑区样式（编码栏）
---@class Preedit
---@field horizontal_padding? integer           # 横向内边距（默认 8）
---@field top_end_radius?     number            # 上端圆角半径
---@field alpha?              number            # 透明度 0.0~1.0（默认 0.8）
---@field foreground?         PreeditForeground # 前景样式

-- ============================================================================
-- 候选窗口/悬浮窗 (Window)
-- ============================================================================

---@class WindowPadding
---@field vertical?          integer           # 垂直内边距
---@field horizontal?        integer           # 水平内边距

---@class WindowForeground
---@field label_font_size?   number            # 序号字号（默认 20）
---@field text_font_size?    number            # 候选文字字号（默认 20）
---@field comment_font_size? number            # 注释字号（默认 20）

--- 候选窗口 / 悬浮窗样式
---@class Window
---@field insets?            WindowPadding     # 窗口内边距（默认 vertical=4, horizontal=4）
---@field item_padding?      WindowPadding     # 候选项内边距（默认 vertical=2, horizontal=4）
---@field min_width?         integer           # 最小宽度
---@field corner_radius?     number            # 窗口圆角
---@field border?            integer           # 边框宽度
---@field shadow?            number            # 阴影半径
---@field alpha?             number            # 透明度 0.0~1.0（默认 1.0）
---@field foreground?        WindowForeground  # 前景样式

-- ============================================================================
-- 工具栏 (ToolBar)
-- ============================================================================

---@class ToolBarButtonBackground
---@field type?              string   # 背景类型（默认 "rectangle"）
---@field corner_radius?     number   # 圆角半径（默认 10）
---@field normal?            Color    # 普通状态颜色/图片
---@field highlight?         Color    # 高亮状态颜色/图片
---@field vertical_inset?    integer  # 垂直内缩（默认 4）
---@field horizontal_inset?  integer  # 水平内缩（默认 4）

---@class ToolBarButtonForeground
---@field style?             string   # 前景样式
---@field option_styles?     string[] # 选项样式列表
---@field normal?            string   # 普通状态颜色/图标
---@field highlight?         string   # 高亮状态颜色/图标
---@field font_size?         number   # 字号（默认 18）
---@field padding?           integer  # 内边距（默认 4）

--- 工具栏按钮
---@class ToolBarButton
---@field background?        ToolBarButtonBackground # 按钮背景样式
---@field foreground?        ToolBarButtonForeground # 按钮前景样式
---@field action?            KeyName                 # 点击动作
---@field long_press_action? KeyName                 # 长按动作
---@field size?              integer[]               # 按钮尺寸 [width, height]

--- 工具栏样式
---@class ToolBar
---@field primary_button? ToolBarButton?   # 主按钮（可选，不配置则使用默认）
---@field buttons?        ToolBarButton[]  # 附加按钮列表
---@field button_font?    string[]         # 按钮字体文件列表
---@field back_style?     string           # 返回按钮样式（默认 "ic@arrow-left"）

-- ============================================================================
-- 候选工具栏 (CandidatesTool)
-- ============================================================================

---@class PopupAction
---@field action KeyName # 弹窗动作
---@field label  string  # 弹窗标签

--- 候选工具栏。
--- 此表为可选——不配置时工具栏隐藏。
---@class CandidatesTool
---@field nav_width?               integer           # 导航区域宽度（默认 44）
---@field popup_width?             integer           # 弹窗宽度
---@field popup_text_size?         number            # 弹窗文字字号
---@field popup_text_color?        Color             # 弹窗文字颜色
---@field popup_background_color?  Color             # 弹窗背景颜色
---@field popup_font?              string[]          # 弹窗字体文件列表
---@field background?              Color             # 工具栏背景
---@field separator_color?         Color             # 分隔线颜色
---@field button_font?             string[]          # 按钮字体文件列表
---@field buttons?                 ToolBarButton[]   # 工具栏按钮列表
---@field popup?                   PopupAction[]     # 弹窗动作列表

-- ============================================================================
-- 液态键盘 (LiquidKeyboard)
-- ============================================================================

---@class EdgeInsets
---@field left?             number                # 左间距
---@field top?              number                # 上间距
---@field right?            number                # 右间距
---@field bottom?           number                # 下间距

--- 液态键盘固定按键项
---@class FixedKeyItem
---@field click?            string                # 点击动作
---@field label?            string                # 显示标签
---@field width?            number?               # 宽度（nil 表示自适应）
---@field height?           number?               # 高度（nil 表示自适应）
---@field margin?           EdgeInsets?           # 外边距
---@field padding?          EdgeInsets?           # 内边距
---@field is_string_format? boolean               # 是否以字符串格式处理

--- 液态键盘固定按键栏
---@class KeyBar
---@field keys?             FixedKeyItem[]        # 固定按键列表
---@field position?         KeyBarPosition        # 栏位位置（默认 BOTTOM）

--- 液态键盘按键项
---@class KeyItem
---@field text?             string                # 主文本
---@field alt_text?         string                # 替代文本（默认同 text）

--- 液态键盘面板
---@class LiquidKeyboardPanel
---@field id?               string                # 面板唯一标识符
---@field type?             LiquidKeyboardType    # 面板类型
---@field name?             string                # 面板名称（标签显示用）
---@field keys?             KeyItem[]             # 按键列表

--- 液态键盘。
--- 可选——不配置时使用默认值。
---@class LiquidKeyboard
---@field single_width?     integer               # 单键宽度
---@field key_height?       integer               # 按键高度
---@field margin_x?         number                # 水平外边距
---@field fixed_key_bar?    KeyBar                # 固定按键栏
---@field keyboards?        LiquidKeyboardPanel[] # 面板列表

-- ============================================================================
-- 键盘布局 (TextKeyboard) 及相关类型
-- ============================================================================

--- 水平对齐方向
---@alias Align
---| '"left"'   # 左对齐
---| '"center"' # 居中（默认）
---| '"right"'  # 右对齐

--- 按键标签分段。每个分段可独立设置粗体、颜色、缩放和对齐。
--- 文本前缀 "ic@" 表示图标，如 "ic@arrow-left"。
--- 文本 "\n" 表示换行。
---
--- 示例：
---   label = {
---     { text = "A", bold = true },
---     { text = "1", scale = 0.6, color = "red", align = "right" },
---   },
---   label_symbol = {
---     { text = "ic@arrow-left", scale = 0.7 },
---     { text = "@", align = "right" },
---   },
---@class LabelSegment
---@field text?   string  # 文本内容（"ic@" 前缀表示图标；"\n" 表示换行）
---@field bold?   boolean # 粗体
---@field color?  Color   # 颜色键名或 hex
---@field scale?  number  # 字号缩放因子（如 0.6 表示 60%）
---@field align?  Align   # 水平对齐

--- 按键定义。
---@class TextKey
---@field width?                      number          # 按键宽度（相对于行的比例，默认 0.0）
---@field spacer?                     boolean         # 是否为空白占位键
---@field round_corner?               number          # 圆角半径（-1 = 使用全局值）
---@field round_corner_top_left?      number?         # 左上角圆角半径（nil = 跟随 round_corner）
---@field round_corner_top_right?     number?         # 右上角圆角半径
---@field round_corner_bottom_left?   number?         # 左下角圆角半径
---@field round_corner_bottom_right?  number?         # 右下角圆角半径
---@field key_border?                 integer         # 按键边框（-1 = 使用全局值）
---@field key_border_color?           Color           # 按键边框颜色
---@field label?                      LabelSegment[]  # 按键标签（结构化分段）
---@field label_symbol?               LabelSegment[]  # 按键副标签（右上角，结构化分段）
---@field hint?                       LabelSegment[]  # 提示文字（底部，结构化分段）
---@field click?                      KeyName         # 点击动作（预设按键名或键码）
---@field send_bindings?              boolean         # 是否发送按键绑定（默认 true）
---@field key_text_size?              number          # 按键文字字号（0 = 使用全局值）
---@field symbol_text_size?           number          # 符号文字字号（0 = 使用全局值）
---@field hint_text_size?             number          # 提示文字字号（0 = 使用全局值）
---@field key_text_offset_x?          number          # 按键文字 X 偏移
---@field key_text_offset_y?          number          # 按键文字 Y 偏移
---@field key_symbol_offset_x?        number          # 符号 X 偏移
---@field key_symbol_offset_y?        number          # 符号 Y 偏移
---@field key_hint_offset_x?          number          # 提示 X 偏移
---@field key_hint_offset_y?          number          # 提示 Y 偏移
---@field key_press_offset_x?         number          # 按下时 X 偏移
---@field key_press_offset_y?         number          # 按下时 Y 偏移
---@field key_text_color?             Color           # 按键文字颜色
---@field key_back_color?             Color           # 按键背景颜色
---@field key_symbol_color?           Color           # 按键符号颜色
---@field hilited_key_text_color?     Color           # 标明按键文字颜色
---@field hilited_key_back_color?     Color           # 标明按键背景颜色
---@field hilited_key_symbol_color?   Color           # 标明按键符号颜色
---@field popup?                      KeyName[]       # 弹窗按键列表
---@field dynamic?                    string          # 动态键盘关联的按键名
---@field composing?                  KeyName         # 输入中点击
---@field has_menu?                   KeyName         # 有候选菜单时点击
---@field paging?                     KeyName         # 翻页时点击
---@field combo?                      KeyName         # 组合键
---@field ascii?                      KeyName         # 英文模式键
---@field double_click?               KeyName         # 双击
---@field lazy_double_click?          KeyName         # 延迟双击
---@field swipe_up?                   KeyName         # 上滑
---@field long_click?                 KeyName         # 长按
---@field swipe_down?                 KeyName         # 下滑
---@field swipe_left?                 KeyName         # 左滑
---@field swipe_right?                KeyName         # 右滑
---@field extra?                      KeyName         # 扩展弹窗按键

--- 键盘行定义
---@class KeyboardRow
---@field height? number    # 行高（0 = 自动）
---@field split?  boolean   # 是否为分割行（landscape_split_percent 处分割）
---@field keys?   TextKey[] # 按键列表

--- 键盘布局定义
---@class TextKeyboard
---@field name?                     string         # 键盘名称
---@field author?                   string         # 作者
---@field keyboard_height?          integer        # 键盘高度（0 = 使用全局值）
---@field keyboard_height_land?     integer        # 横屏键盘高度（0 = 使用全局值）
---@field auto_height_index?        integer        # 自动高度基准行索引（-1 = 禁用）
---@field horizontal_gap?           integer        # 键水平间距（0 = 使用全局值）
---@field vertical_gap?             integer        # 键垂直间距（0 = 使用全局值）
---@field round_corner?             number         # 圆角半径（-1 = 使用全局值）
---@field key_border?               integer        # 按键边框（-1 = 使用全局值）
---@field ascii_mode?               boolean        # 是否为英文输入模式（默认 true）
---@field reset_ascii_mode?         boolean        # 是否在激活时重置英文模式
---@field label_transform?          LabelTransform # 标签转换（NONE / UPPERCASE）
---@field lock?                     boolean        # 是否锁定该键盘（不随模式切换）
---@field ascii_keyboard?           string         # 英文模式下关联的键盘 ID
---@field landscape_keyboard?       string         # 横屏时切换的键盘 ID
---@field landscape_split_percent?  integer        # 横屏分割百分比
---@field key_text_offset_x?        number         # 按键文字 X 偏移
---@field key_text_offset_y?        number         # 按键文字 Y 偏移
---@field key_symbol_offset_x?      number         # 符号 X 偏移
---@field key_symbol_offset_y?      number         # 符号 Y 偏移
---@field key_hint_offset_x?        number         # 提示 X 偏移
---@field key_hint_offset_y?        number         # 提示 Y 偏移
---@field key_press_offset_x?       number         # 按下时 X 偏移
---@field key_press_offset_y?       number         # 按下时 Y 偏移
---@field import_preset?            string         # 导入预设键盘 ID
---@field navbar?                   boolean        # 是否显示导航栏
---@field t9_mode?                  boolean        # 是否为 T9 模式
---@field keyboard_padding_top?     integer        # 键盘顶部边距
---@field t9_sidebar_width?         number         # T9 侧栏宽度比例（默认 0.15）
---@field t9_sidebar_position?      string         # T9 侧栏位置（默认 "left"）
---@field t9_sidebar_span_rows?     integer        # T9 侧栏跨越行数（默认 3）
---@field t9_sidebar_show_items?    integer        # T9 侧栏显示条目数（默认 4）
---@field t9_sidebar_symbols?       string[]       # T9 侧栏符号列表
---@field dynamic_mode?             boolean        # 是否启用动态键盘
---@field dynamic_original?         string         # 动态键盘原始布局引用
---@field rows?                     KeyboardRow[]  # 键盘行列表

-- ============================================================================
-- 根主题 (Theme)
-- ============================================================================

--- LyraIME 键盘主题根表。
--- 这是主题文件的顶层返回值。所有属性均为可选（除 name 外），
--- 未指定的属性会使用默认值。
---
--- 用法：
---   return theme {
---     name = "我的主题",
---     version = "1.0",
---     style = style { ... },
---     preset_color_schemes = { scheme("light", { ... }), scheme("dark", { ... }) },
---     preset_keys = { Space = { ... }, BackSpace = { ... } },
---     preset_keyboards = { default = keyboard { ... } },
---   }
---@class Theme
---@field name                  string                     # 主题名称（***必填***）
---@field version               string                     # 版本号（默认 ""）
---@field author?               string                     # 作者（默认 ""）
---@field style                 GeneralStyle               # 全局样式
---@field preedit?              Preedit                    # 预编辑区样式（默认值见 Preedit）
---@field window?               Window                     # 候选窗口样式（默认值见 Window）
---@field tool_bar?             ToolBar                    # 工具栏样式（默认值见 ToolBar）
---@field candidates_tool?      CandidatesTool             # 候选工具栏（nil = 隐藏）
---@field liquid_keyboard?      LiquidKeyboard             # 液态键盘（默认值见 LiquidKeyboard）
---@field fallback_colors?      { [string]: Color }        # 回退颜色映射（如 { candidate_text_color = "text_color" }）
---@field preset_color_schemes  ColorScheme[]              # 配色方案列表
---@field preset_keys?          { [string]: PresetKey }    # 预设按键（键名 → 定义）
---@field preset_keyboards      { [string]: TextKeyboard } # 预设键盘布局（ID → 定义）

-- ============================================================================
-- 全局 DSL 函数
-- ============================================================================

--- 沙箱模块加载函数。等价于 Lua 原生 require（沙箱中 require 已被 nil）。
--- 模块搜索路径：themes/lib/ 及其所有子目录下的 ?.lua、?/init.lua。
---
--- 用法：
---   local mocha = safe_require("colors.mocha")
---   local layout = safe_require("layouts.qwerty")
---@param modname string # 模块名
---@return any
function safe_require(modname)
  return (require(modname))
end

--- 声明根主题表（主题文件顶层必须调用）。
--- 对传入的表进行类型检查后原样返回。与 `return { ... }` 等价的类型安全封装。
---@param t Theme
---@return Theme
function theme(t)
  return t
end

--- 声明全局样式子表。
---@param t GeneralStyle
---@return GeneralStyle
function style(t)
  return t
end

--- 声明键盘布局。
--- 可用作 preset_keyboards 映射中的值，也可用 merge 和 insert 进行组合。
---@param t TextKeyboard
---@return TextKeyboard
function keyboard(t)
  return t
end

--- 声明键盘行。一般用作 keyboard.rows 中的元素。
---@param t KeyboardRow
---@return KeyboardRow
function row(t)
  return t
end

--- 声明键盘按键。一般用作 row.keys 中的元素。
---@param t TextKey
---@return TextKey
function key(t)
  return t
end

--- 声明预编辑区样式。
---@param t Preedit
---@return Preedit
function preedit(t)
  return t
end

--- 声明候选窗口样式。
---@param t Window
---@return Window
function window(t)
  return t
end

--- 声明工具栏样式。
---@param t ToolBar
---@return ToolBar
function toolbar(t)
  return t
end

--- 声明工具栏按钮。可用于 ToolBar.primary_button 或 ToolBar.buttons。
---@param t ToolBarButton
---@return ToolBarButton
function btn(t)
  return t
end

--- 声明工具栏按钮背景。
---@param t ToolBarButtonBackground
---@return ToolBarButtonBackground
function bg(t)
  return t
end

--- 声明工具栏按钮前景。
---@param t ToolBarButtonForeground
---@return ToolBarButtonForeground
function fg(t)
  return t
end

--- 声明液态键盘。
---@param t LiquidKeyboard
---@return LiquidKeyboard
function liquid(t)
  return t
end

--- 声明回退颜色映射。未配置的颜色按此表键名重定向。
---@param t { [string]: string } # 回退映射表
---@return { [string]: string }
function fallback(t)
  return t
end

--- 声明配色方案。返回包含 id 和 colors 字段的表。
---
--- 用法：
---   scheme("light", {
---     text_color = "0x000000",
---     back_color = "0xFFFFFF",
---     light_scheme = "light",  -- 自身标记
---     dark_scheme = "dark",    -- 暗色模式跳转目标
---   })
---@param id string                          # 配色方案唯一标识符
---@param colors SchemeColors  # 颜色键值对表
---@return ColorScheme
function scheme(id, colors)
  return { id = id, colors = colors }
end

--- 声明预设按键。返回第二个参数（颜色表）。
--- 注意：当前实现中，第一个参数（id）不嵌入返回值，
---       预设按键通过 preset_keys 表中的键名查找。
--- 用法：
---   preset_keys = {
---     Space = pk("space", { label = " ", send = "space" }),
---   }
---@param id string     # 按键标识符（当前未嵌入返回值，保留供将来使用）
---@param t PresetKey   # 预设按键定义
---@return PresetKey
function pk(id, t)
  return t
end

--- lua_sandbox.cc:135-169 l_merge 实现：
---   先将 a 的全部键值复制到新表 result，再遍历 b：
---   - 若 result[k] 与 b[k] 同为 table → 递归 merge
---   - 否则以 b[k] 直接覆盖 result[k]
---   返回新表，a / b 均不被修改。
---   替代旧 YAML 的 __include 和 __patch（字段覆盖）。
---
---   注：C++ 递归时以 b[k]（value）为键去 result 中查找（lua_gettable），
---   此处对 Lua trace 来说可能返回 nil。本节中的代码做了修正，以 result[k]（key）作为
---   第一个参数进行递归调用。

--- 深度合并两个表。第二个表的字段会覆盖第一个表的同名字段。
--- 当两个表在同一键下均为表类型时，递归合并（而非直接替换）。
---
--- 用法：
---   -- 基于已有布局创建新变体
---   local letter_kb = keyboard(merge(base_kb, { ascii_mode = true }))
---   -- 嵌套覆盖（修改特定按键的行为）
---   local modified = keyboard(merge(base_kb, {
---     rows = { [3] = { keys = { [2] = { long_click = "x" } } } }
---   }))
---@param a table # 基础表
---@param b table # 覆盖表（优先级更高）
---@return table  # 合并后的新表
function merge(a, b)
  local result = {}
  for k, v in pairs(a) do
    result[k] = v
  end
  for k, v in pairs(b) do
    if type(result[k]) == "table" and type(v) == "table" then
      result[k] = merge(result[k], v)
    else
      result[k] = v
    end
  end
  return result
end

--- lua_sandbox.cc:172-196 l_insert 实现：
---   先用 pairs 计数得到数组长度 n，再将位置 1..pos-1 复制到新表，
---   在 pos 处插入 val，最后将原位置 pos..n 的元素集体后移一位。
---   返回新表，原表 t 不被修改。
---   替代旧 YAML 的 __patch（数组插入）。

--- 在数组表指定位置插入值（1-索引），返回新数组表。
--- 原表不会被修改。
---
--- 用法：
---   -- 在键盘第 5 行后插入新行
---   local new_rows = insert(base_kb.rows, 5, row { ... })
---   -- 在第 2 行第 3 个键后插入新键
---   local new_keys = insert(base_kb.rows[2].keys, 3, key { ... })
---@param t   table   # 原数组表（1-索引的连续整数键）
---@param pos integer # 插入位置（1-索引）
---@param val any     # 要插入的值
---@return table      # 插入后的新数组表
function insert(t, pos, val)
  local n = 0
  for _ in pairs(t) do
    n = n + 1
  end
  local result = {}
  for i = 1, pos - 1 do
    result[i] = t[i]
  end
  result[pos] = val
  for i = pos, n do
    result[i + 1] = t[i]
  end
  return result
end

-- ============================================================================
-- 键盘布局组合模式示例
-- ============================================================================
--[[

  -- 1. 定义基础布局
  local base_keyboard = {
    name = "26键基础布局",
    ascii_mode = false,
    rows = {
      row { keys = { key { click = "q" }, key { click = "w" }, ... } },
      row { keys = { key { click = "a" }, key { click = "s" }, ... } },
      ...
    }
  }

  -- 2. 直接引用
  preset_keyboards = {
    default = keyboard(base_keyboard),
  }

  -- 3. merge 创建变体
  preset_keyboards = {
    default = keyboard(base_keyboard),
    letter = keyboard(merge(base_keyboard, {
      name = "英文键盘",
      ascii_mode = true,
      reset_ascii_mode = true,
    })),
  }

  -- 4. insert 插入行列
  local with_extra_row = merge(base_keyboard, {
    rows = insert(base_keyboard.rows, 2, row {
      keys = { key { click = "1" }, key { click = "2" }, ... }
    })
  })

--]]
