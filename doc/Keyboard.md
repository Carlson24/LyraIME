<!--
SPDX-FileCopyrightText: 2015 - 2026 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# 键盘主题迁移指南

本文说明将 LyraIME 主题从旧格式（扁平 `keys` + 自动换行）迁移至新格式（显式 `rows` + `weight` 弹性布局）。

## 变更总览

| 变更项 | 旧 | 新 |
|--------|----|----|
| 密钥结构 | `keys:` 扁平列表 | `rows:` 包装的行列表 |
| 宽度模型 | `width: 10`（百分比） | `weight: 1.0`（flex-grow 权重，范围 0-1） |
| 间隔键 | `{width: 5}`（无 `click` 隐式判断） | `{spacer: 0.5}`（显式声明） |
| 换行方式 | `columns: 30` 自动 | 显式行定义，删除 `columns` |
| 键盘级宽高 | `width:` / `height:` 于键盘节点 | **移除**，行高由 `height` 权重控制 |
| 单键高度 | `{click: x, height: 50}` | **移除**，仅行级 `height` 定义行高 |

## 新概念

### Flex 弹性布局

每行是一个水平弹性容器。键的实际像素宽度 = `weight / 行总权重 × 可用宽度`。

- 未指定 `weight` 的键，默认 `weight: 1.0`，等分剩余空间
- 权重范围为浮点数，建议 0-1，但可使用任意正数
- 视图层使用 `FlexboxLayout`（Google flexbox 3.0），`flexGrow` 属性对应 `weight`

### 行（Row）

```yaml
rows:
  - keys:
      - {click: 'q', weight: 1.0}
      - {click: 'w'}
    height: 1.0          # 行高权重（占键盘总高比例），默认 1.0
    split_after: 5       # 横屏分屏点（可选，-1 不参与分屏）
  - keys:
      - {click: 'a'}
```

- `height`：行高权重，所有行的 `height` 总和决定各行在键盘总高中所占比例
- `split_after`：在该 entry 之后插入横屏分屏间隙（仅在 `landscape_split_percent > 0` 时生效）
- 行内不能为单键指定高度

### 间隔（Spacer）

旧格式中 `{width: 5}` 无 `click` 的隐式间隔，现在需显式声明：

```yaml
keys:
  - {click: 'a'}
  - {spacer: 0.5}        # 占 0.5 权重的空白
  - {click: 's'}
```

Spacer 渲染为不可见的 `Space` View，仅用于布局留白。

## 迁移步骤

### 步骤一：将 `keys` 拆为 `rows`

每个有 `click` 的键计一次，根据旧格式的 `columns` 值（默认 30）或 `width` 推断每行键数。

**推断规则**：若无 `columns`，由 `width` 推断 —— `每行键数 = round(100 / width)`。

```yaml
# 旧格式 — qwerty 键盘 (columns: 10, width: 10)
qwerty:
  columns: 10
  width: 10
  height: 55
  keys:
    - {click: 'q'}
    - {click: 'w'}
    # … 8 more …
    - {click: 'p'}
    - {width: 5}        # spacer
    - {click: 'a'}
    - {click: 's'}
    # … 8 more …
    - {click: 'l'}
    - {width: 5}        # spacer
    - {click: Shift_L, width: 15}

# 新格式 — 每 10 个 clickable key 一行
qwerty:
  rows:
    - keys:
      - {click: 'q', weight: 10.0}
      - {click: 'w', weight: 10.0}
      # … 8 more …
      - {click: 'p', weight: 10.0}
    - keys:
      - {spacer: 5.0}
      - {click: 'a', weight: 10.0}
      - {click: 's', weight: 10.0}
      # … 8 more …
      - {click: 'l', weight: 10.0}
      - {spacer: 5.0}
      - {click: Shift_L, weight: 15.0}
```

### 步骤二：`width` → `weight`

键盘级的 `width` 决定默认键权重：

```yaml
# 旧格式
width: 10
keys:
  - {click: 'q'}           # 默认 width=10（来自键盘级）
  - {click: space, width: 30}  # 显式 width=30

# 新格式
rows:
  - keys:
    - {click: 'q', weight: 10.0}      # 搬迁默认值
    - {click: space, weight: 30.0}    # 搬迁显式值
```

### 步骤三：移除 `columns`、`height`、`width`

删除键盘级以下字段：

- `columns:` — 不再需要
- `width:` — 已被每个键的 `weight` 替代
- `height:` — 已被行级 `height` 权重替代

```yaml
# 旧格式
qwerty:
  columns: 10      # 删除
  width: 10        # 删除
  height: 55       # 删除
  ascii_mode: 0
  lock: true

# 新格式
qwerty:
  ascii_mode: 0
  lock: true
  rows:
    # … 行定义 …
```

### 步骤四：更新 `__include` 继承

使用 `__include` 简化重复定义，被引用的键盘也应已迁移为新格式：

```yaml
# 继承 default 键盘所有 rows
letter:
  __include: /preset_keyboards/default
  ascii_mode: 1
  reset_ascii_mode: true
  lock: false
```

注意：`__include` 由 Rime YAML 加载器在部署时合并，迁移时无需对继承子句做任何改动。

## 横屏分屏

旧格式使用键盘级 `landscape_split_percent`，自动在每行过半处插入间隙。新格式改为**行内显式定义**：

```yaml
# 新格式 — 第 5 个 entry 后分屏
rows:
  - split_after: 5          # 在第 5 个 entry 之后分屏
    keys:
    - {click: 'q', weight: 1.0}
    - {click: 'w', weight: 1.0}
    - {click: 'e', weight: 1.0}
    - {click: 'r', weight: 1.0}
    - {click: 't', weight: 1.0}    # ← 在此之后插入分屏间隙
    - {click: 'y', weight: 1.0}
    - {click: 'u', weight: 1.0}
    - {click: 'i', weight: 1.0}
    - {click: 'o', weight: 1.0}
    - {click: 'p', weight: 1.0}
```

- `split_after` 从 0 开始计数（0 = 第一个 entry 之后）
- `-1` 或未指定 = 此行不参与分屏
- 键盘级 `landscape_split_percent` 决定间隙宽度比例

## 完整示例：从 `trime.yaml` 重构

以 `default` 键盘（40 键）为例。

### 旧格式（`trime.yaml` 原始内容）

```yaml
preset_keyboards:
  default:
    name: 预设40键
    ascii_mode: 0
    width: 10
    height: 44
    lock: true
    keys:
      - {click: '1', long_click: '!'}
      - {click: '2', long_click: '@'}
      - {click: '3', long_click: '#'}
      - {click: '4', long_click: '$'}
      - {click: '5', long_click: '%'}
      - {click: '6', long_click: '^'}
      - {click: '7', long_click: '&'}
      - {click: '8', long_click: '*'}
      - {click: '9', long_click: '('}
      - {click: '0', long_click: ')'}
      - {click: 'q', long_click: '_'}
      - {click: 'w', long_click: '-'}
      - {click: 'e', long_click: '+'}
      # … 30 more keys …
      - {click: space, width: 30}
      - {click: BackSpace, width: 15}
      - {click: Return, composing: Return1, width: 15}
```

### 新格式（迁移后）

```yaml
preset_keyboards:
  default:
    name: 预设40键
    ascii_mode: 0
    lock: true
    rows:
      - keys:
        - {click: '1', long_click: '!', weight: 10.0}
        - {click: '2', long_click: '@', weight: 10.0}
        - {click: '3', long_click: '#', weight: 10.0}
        - {click: '4', long_click: '$', weight: 10.0}
        - {click: '5', long_click: '%', weight: 10.0}
        - {click: '6', long_click: '^', weight: 10.0}
        - {click: '7', long_click: '&', weight: 10.0}
        - {click: '8', long_click: '*', weight: 10.0}
        - {click: '9', long_click: '(', weight: 10.0}
        - {click: '0', long_click: ')', weight: 10.0}
      - keys:
        - {click: 'q', long_click: '_', weight: 10.0}
        - {click: 'w', long_click: '-', weight: 10.0}
        - {click: 'e', long_click: '+', weight: 10.0}
        - {click: 'r', long_click: '=', weight: 10.0}
        - {click: 't', long_click: '|', weight: 10.0}
        - {click: 'y', long_click: '\', weight: 10.0}
        - {click: 'u', long_click: '[', weight: 10.0}
        - {click: 'i', long_click: ']', weight: 10.0}
        - {click: 'o', long_click: '{', weight: 10.0}
        - {click: 'p', long_click: '}', weight: 10.0}
      - keys:
        - {click: 'a', long_click: select_all, weight: 10.0}
        - {click: 's', long_click: Home, weight: 10.0}
        - {click: 'd', long_click: End, weight: 10.0}
        - {click: 'f', long_click: Page_Up, weight: 10.0}
        - {click: 'g', long_click: Page_Down, weight: 10.0}
        - {click: 'h', long_click: Left, weight: 10.0}
        - {click: 'j', long_click: Down, weight: 10.0}
        - {click: 'k', long_click: Up, weight: 10.0}
        - {click: 'l', long_click: Right, weight: 10.0}
        - {click: ';', long_click: ':', weight: 10.0}
      - keys:
        - {click: 'z', long_click: '`', weight: 10.0}
        - {click: 'x', long_click: cut, weight: 10.0}
        - {click: 'c', long_click: copy, weight: 10.0}
        - {click: 'v', long_click: paste, weight: 10.0}
        - {click: 'b', long_click: '~', weight: 10.0}
        - {click: 'n', long_click: Insert, weight: 10.0}
        - {click: 'm', long_click: Delete, weight: 10.0}
        - {click: ',', long_click: '<', weight: 10.0}
        - {click: '.', long_click: '>', weight: 10.0}
        - {click: '/', long_click: '?', weight: 10.0}
      - keys:
        - {click: Shift_L, weight: 10.0}
        - {click: Keyboard_symbols, long_click: Keyboard_number, weight: 10.0}
        - {click: Mode_switch, long_click: Menu, weight: 10.0}
        - {click: space, weight: 30.0}
        - {click: "'", long_click: '"', weight: 10.0}
        - {click: BackSpace, weight: 15.0}
        - {click: Return, composing: Return1, long_click: CommitComment, weight: 15.0}
```

## 权重调优建议

1. **行内均匀**：若行内所有键权重相同（如 `weight: 1.0`），各键等宽
2. **个性化键**：将功能键（Space、BackSpace、Return）权重设大值
3. **合理性检查**：行总权重直接影响各键像素占比，可先心算比例
4. **不要为高度焦虑**：行高由 `height` 权重决定，不是绝对像素值

## 横屏键盘

仍使用键盘级 `landscape_keyboard` 指定横屏替代键盘：

```yaml
my_keyboard:
  name: 直屏键盘
  landscape_keyboard: my_landscape_keyboard
  landscape_split_percent: 40
  rows:
    # … 直屏行定义 …

my_landscape_keyboard:
  name: 横屏键盘
  landscape_split_percent: 0
  rows:
    # … 横屏行定义 …
```

## 常见陷阱

- **不要在行内混用 `spacer` 和普通键总权重相差距过大**：一个超大 spacer 会把键压搾得很小
- **`__include` 的父主题必须也已迁移**：否则 YAML 加载后结构不一致
- **`height` 是比例，不是像素**：行高 `1.0` 和 `2.0` 意味后者行高是前者的两倍
- **旧主题文件先备份**：迁移后无法自动恢复旧格式
