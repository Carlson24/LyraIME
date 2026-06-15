# 主题迁移指南：弹性布局（Flex）键盘系统

## 概述

LyraIME 的键盘布局系统已从固定像素/百分比模型迁移至基于**权重**的弹性（Flex）布局模型。此指南说明所有变更及主题迁移方法。

## 变更摘要

### 已删除的字段

| 位置 | 已删除字段 | 说明 |
|------|-----------|------|
| `style:` | `key_height` | 全局默认键高（绝对像素）已移除 |
| `style:` | `key_width` | 全局默认键宽（屏幕百分比）已移除 |
| 键盘级 | `width` | 键盘级默认键宽（旧 0-100 权重）已移除 |
| 键盘级 | `height` | 键盘级默认键高（绝对像素）已移除 |
| 键盘级 | `columns` | 最大列数限制（换行由行结构显式定义）已移除 |
| 每键 | `height` | 每键高度：若定义则忽略并输出警告 |

### 新增的结构

| 位置 | 新增字段 | 说明 |
|------|---------|------|
| 键盘级 | `rows` | 替代旧的 `keys` 平铺列表，显式定义每一行 |
| 行级 | `height` | 行高权重（`0~1` 浮点），`0` = 未定义（自动均分） |
| 行级 | `split` | 布尔值，标记该行在横屏时需分割（`true` 时插入左右手间隙） |
| 每键 | `spacer` | 布尔值，显式标记空白占位符（替代旧的仅有 `width` 无 `click` 的键） |

### 修改的字段

| 字段 | 旧含义 | 新含义 |
|------|-------|-------|
| `width`（每键） | 旧权重（基于 `MAX_TOTAL_WEIGHT=100`） | 新权重（`0~1` 浮点，`0` = 未定义/自动均分） |

## 权重规则

### 行高权重

每个键盘的 `rows` 结构包含若干行。每行可选择性定义 `height`（`0~1` 浮点权重）：

1. **全部未定义**（所有行 `height` 均为 `0` 或未定义）：高度均分
2. **部分定义**：已定义的行取各自的权重，剩余权重均分给未定义的行
3. **总和 < 1.0**：在第一行之上和最后一行之下添加等量空白，使各行上下居中
4. **总和 > 1.0**：**错误**，停止解析，日志输出报错信息

**示例**：5 行键盘，第 1 行 `height: 0.12`，第 5 行 `height: 0.28`：

```
已定义总和 = 0.12 + 0.28 = 0.40
剩余 = 1.0 - 0.40 = 0.60
未定义行（第 2、3、4 行）各得 0.60 / 3 = 0.20
最终：0.12, 0.20, 0.20, 0.20, 0.28（总和 1.0）
```

### 键宽权重

每行内的每个键可选择性定义 `width`（`0~1` 浮点权重）：

1. **全部未定义**（行内所有键 `width` 均为 `0` 或未定义）：宽度均分
2. **部分定义**：已定义的键取各自的权重，剩余权重均分给未定义的键
3. **全部定义 且 总和 < 1.0**：在行首和行尾添加等量空白，使该行居中
4. **总和 > 1.0**：**错误**，停止解析，日志输出报错信息

## 新格式示例

### 旧格式（迁移前）

```yaml
style:
  key_height: 44
  key_width: 10.0

preset_keyboards:
  default:
    width: 10
    height: 44
    columns: 30
    keys:
      - {click: q, long_click: '1'}
      - {click: w, long_click: '2'}
      # ... 8 more ...
      - {click: a, long_click: select_all}
      # ... 9 more ...
      - {click: space, width: 30}
      - {width: 5}                     # 旧空白标记
      - {click: BackSpace, width: 15}
```

### 新格式（迁移后）

```yaml
style:
  # key_height 和 key_width 已删除

preset_keyboards:
  default:
    # width、height、columns 已删除
    rows:
      - keys:
          - {click: q, long_click: '1'}
          - {click: w, long_click: '2'}
          # ... 8 more ...
      - keys:
          - {click: a, long_click: select_all}
          # ... 9 more ...
      - keys:
          - {click: space, width: 0.3}
          - {spacer: true, width: 0.05}  # 新空白标记
          - {click: BackSpace, width: 0.15}
```

### 带行高权重的示例

```yaml
preset_keyboards:
  custom:
    rows:
      - height: 0.12                   # 数字行较矮
        keys:
          - {click: '1'}
          - {click: '2'}
          # ...
      - keys:                           # height 未定义 → 均分
          - {click: q}
          - {click: w}
          # ...
      - height: 0.28                   # 底行较宽（功能键行）
        keys:
          - {click: space, width: 0.4}
          - {click: BackSpace, width: 0.15}
```

### 横屏分割标记

```yaml
preset_keyboards:
  landscape_layout:
    rows:
      - keys:
          - {click: q}
          - {click: w}
          # ...
      - split: true                     # 横屏时在此行插入左右手分割间隙
        keys:
          - {click: a}
          - {click: s}
          # ...
```

## 迁移步骤（手动迁移旧主题）

1. **从 `style:` 中删除 `key_height` 和 `key_width`**

2. **将每个键盘的 `keys:` 平铺列表转换为 `rows:` 结构**：
   - 分析旧的自动换行行为（基于 `columns` 和宽度溢出）以确定行分割点
   - 将每行键组合为 `rows:` 列表中的一个条目

3. **转换键宽**：
   - 旧 `width: X`（基于 100 权重制）→ 新 `width: X/100`（基于 1.0 权重制）
   - 例如：`width: 15` → `width: 0.15`
   - 使用键盘级默认宽度的键：移除 `width` 字段（将自动均分）

4. **转换空白标记**：
   - 旧：`{width: 5}`（无 `click`）
   - 新：`{spacer: true, width: 0.05}`

5. **移除每键 `height`**：
   - 删除所有键定义中的 `height` 字段
   - 若某行需要不同的高度，使用行级 `height` 权重
   - 若 `__include` 键组预设中有 `height`，删除该字段

6. **删除键盘级字段**：`width`、`height`、`columns`

## 自动化迁移

本项目提供了一个 Python 迁移脚本：

```bash
python3 tools/migrate_theme.py <theme.yaml>
```

该脚本会自动：
- 从 `style:` 中删除 `key_height` 和 `key_width`
- 将每个键盘的 `keys:` 转换为 `rows:`（使用旧的自动换行逻辑）
- 将所有键宽从旧权重（除以 100）转换为新权重
- 将旧空白标记转换为 `spacer: true`
- 删除每键 `height` 和 `__include` 预设中的 `height`
- 删除键盘级 `width`、`height`、`columns`
- 保留所有 YAML 锚点（`&`/`*`）、注释和格式

## 错误处理

| 条件 | 行为 |
|------|------|
| 行高权重总和 > 1.0 | `Timber.e()` 报错，键盘为空（不创建任何按键） |
| 行内键宽权重总和 > 1.0 | `Timber.e()` 报错，键盘为空 |
| 每键 `height` 已定义 | `Timber.w()` 警告，忽略该值 |
| 每键 `width` > 1.0 | `error()` 抛出异常 |
