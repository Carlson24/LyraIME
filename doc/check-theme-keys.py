#!/usr/bin/env python3
"""
Check Rime/LyraIME theme .lua files for undefined preset key references.

Detects cases like:
    key { click = "MissingKey" }   -- "MissingKey" not in preset_keys

Resolves safe_require() calls to follow preset_keys and layout definitions
across library files.

Usage:
  python3 check-theme-keys.py theme.lua
  python3 check-theme-keys.py themes/*.lua
  python3 check-theme-keys.py --lib-dir lib/ theme.lua
  python3 check-theme-keys.py --no-follow theme.lua
  python3 check-theme-keys.py --dump-builtin   # dump built-in key list

Output (GCC-compatible):
  file:line:col: severity: message

Exit code: non-zero if any warnings or errors found.
"""

from __future__ import annotations

import sys
import os
import re
from dataclasses import dataclass, field
from typing import List, Optional, Set, Dict

# ── Built-in key names ────────────────────────────────────────────────────────
# 从 Keycode.kt 枚举提取 — 这是 Keycode.fromString() 能解析的完整集合。
# 373 entries：枚举名 + reverseMap 符号键名。
# 生成方式：python3 -c "(see Keycode.kt extraction script)"

BUILTIN_KEYS: Set[str] = {
    '!','"','#','$','%','&',"'",'(',')','*','+',',','-','.','/',
    '0','1','2','3','3D_MODE','4','5','6','7','8','9',':',';','<','=','>','?','@','[','\\',']','^','_',
    '_0','_1','_11','_12','_2','_3','_3D_MODE','_4','_5','_6','_7','_8','_9','`',
    'A','a','ALL_APPS','Alt_L','Alt_R','ampersand','apostrophe','APP_SWITCH','asciicircum','asciitilde',
    'ASSIST','asterisk','at','AVR_INPUT','AVR_POWER',
    'B','b','BACK','backslash','BackSpace','bar','BOOKMARK','braceleft','braceright',
    'bracketleft','bracketright','BRIGHTNESS_DOWN','BRIGHTNESS_UP',
    'BUTTON_1','BUTTON_10','BUTTON_11','BUTTON_12','BUTTON_13','BUTTON_14','BUTTON_15','BUTTON_16',
    'BUTTON_2','BUTTON_3','BUTTON_4','BUTTON_5','BUTTON_6','BUTTON_7','BUTTON_8','BUTTON_9',
    'BUTTON_A','BUTTON_B','BUTTON_C','BUTTON_L1','BUTTON_L2','BUTTON_MODE',
    'BUTTON_R1','BUTTON_R2','BUTTON_SELECT','BUTTON_START','BUTTON_THUMBL','BUTTON_THUMBR',
    'BUTTON_X','BUTTON_Y','BUTTON_Z',
    'C','c','CALCULATOR','CALENDAR','CALL','CAMERA','Caps_Lock','CAPTIONS',
    'CHANNEL_DOWN','CHANNEL_UP','Clear','colon','comma','CONTACTS','Control_L','Control_R','COPY','CUT',
    'D','d','Delete','dollar','Down','DVR',
    'E','e','Eisu_toggle','End','ENDCALL','ENVELOPE','equal','Escape','exclam','EXPLORER',
    'F','f','F1','F10','F11','F12','F2','F3','F4','F5','F6','F7','F8','F9','Find','FOCUS','function',
    'G','g','grave','greater','GUIDE',
    'H','h','HEADSETHOOK','Help','Henkan','Hiragana_Katakana','HOME','Home',
    'I','i','INFO','Insert',
    'J','j',
    'K','k','Kana_Lock',
    'KP_0','KP_1','KP_2','KP_3','KP_4','KP_5','KP_6','KP_7','KP_8','KP_9',
    'KP_Add','KP_Begin','KP_Decimal','KP_Divide','KP_Enter','KP_Equal','KP_Multiply','KP_Separator','KP_Subtract',
    'L','l','LANGUAGE_SWITCH','LAST_CHANNEL','Left','less',
    'M','m','MANNER_MODE','MEDIA_AUDIO_TRACK','MEDIA_CLOSE','MEDIA_EJECT',
    'MEDIA_FAST_FORWARD','MEDIA_NEXT','MEDIA_PAUSE','MEDIA_PLAY','MEDIA_PLAY_PAUSE',
    'MEDIA_PREVIOUS','MEDIA_RECORD','MEDIA_REWIND','MEDIA_SKIP_BACKWARD','MEDIA_SKIP_FORWARD',
    'MEDIA_STEP_BACKWARD','MEDIA_STEP_FORWARD','MEDIA_STOP','MEDIA_TOP_MENU',
    'Menu','Meta_L','Meta_R','minus','Mode_switch','Muhenkan','MUSIC','MUTE',
    'N','n','NAVIGATE_IN','NAVIGATE_NEXT','NAVIGATE_OUT','NAVIGATE_PREVIOUS','Next','NOTIFICATION','NUM','Num_Lock','numbersign',
    'O','o',
    'P','p','Page_Down','Page_Up','PAIRING','parenleft','parenright','PASTE','Pause','percent','period',
    'PICTSYMBOLS','plus','Pointer_DownLeft','Pointer_DownRight','Pointer_UpLeft','Pointer_UpRight',
    'POWER','PROFILE_SWITCH','PROG_BLUE','PROG_GREEN','PROG_RED','PROG_YELLOW',
    'Q','q','question','quotedbl',
    'R','r','REFRESH','Return','Right','RO',
    'S','s','Scroll_Lock','semicolon','SETTINGS','Shift_L','Shift_R','slash',
    'SLEEP','SOFT_LEFT','SOFT_RIGHT','SOFT_SLEEP','space',
    'STB_INPUT','STB_POWER','STEM_1','STEM_2','STEM_3','STEM_PRIMARY',
    'SYM','Sys_Req','SYSTEM_NAVIGATION_DOWN','SYSTEM_NAVIGATION_LEFT','SYSTEM_NAVIGATION_RIGHT','SYSTEM_NAVIGATION_UP',
    'T','t','Tab','THUMBS_DOWN','THUMBS_UP',
    'TV','TV_ANTENNA_CABLE','TV_AUDIO_DESCRIPTION','TV_AUDIO_DESCRIPTION_MIX_DOWN','TV_AUDIO_DESCRIPTION_MIX_UP',
    'TV_CONTENTS_MENU','TV_DATA_SERVICE','TV_INPUT','TV_INPUT_COMPONENT_1','TV_INPUT_COMPONENT_2',
    'TV_INPUT_COMPOSITE_1','TV_INPUT_COMPOSITE_2','TV_INPUT_HDMI_1','TV_INPUT_HDMI_2','TV_INPUT_HDMI_3','TV_INPUT_HDMI_4',
    'TV_INPUT_VGA_1','TV_MEDIA_CONTEXT_MENU','TV_NETWORK','TV_NUMBER_ENTRY','TV_POWER','TV_RADIO_SERVICE',
    'TV_SATELLITE','TV_SATELLITE_BS','TV_SATELLITE_CS','TV_SATELLITE_SERVICE',
    'TV_TELETEXT','TV_TERRESTRIAL_ANALOG','TV_TERRESTRIAL_DIGITAL','TV_TIMER_PROGRAMMING','TV_ZOOM_MODE',
    'U','u','underscore','Up',
    'V','v','VOICE_ASSIST','VoidSymbol','VOLUME_DOWN','VOLUME_MUTE','VOLUME_UP',
    'W','w','WAKEUP','WINDOW',
    'X','x',
    'Y','y','yen',
    'Z','z','Zenkaku_Hankaku','ZOOM_IN','ZOOM_OUT',
    '{','|','}','~',
}

# ── Reference field sets ──────────────────────────────────────────────────────

# Fields whose string value is always a key name reference (only appear in
# key() / btn() contexts).
_UNCONDITIONAL_REF_FIELDS: Set[str] = {
    "click", "long_click",
    "swipe_up", "swipe_down", "swipe_left", "swipe_right",
    "composing", "has_menu", "paging", "combo", "ascii",
    "double_click", "lazy_double_click", "extra", "dynamic",
    "action", "long_press_action",
}

# Fields whose string value is a key name — only inside preset_keys context.
_PRESET_KEYS_REF_FIELDS: Set[str] = {
    "send",
}

# Array-typed fields whose elements are key names — inside preset_keys.
_PRESET_KEYS_ARRAY_FIELDS: Set[str] = {
    "actions",
}

# Array-typed fields whose elements are key names — inside key{} calls.
_KEY_ARRAY_FIELDS: Set[str] = {
    "popup",
}


# ── Tokenizer ─────────────────────────────────────────────────────────────────

@dataclass(slots=True)
class Token:
    kind: str         # ident, string, number, lbrace, rbrace, lparen, rparen,
                      # lbracket, rbracket, eq, comma, dot, colon, semi, op
    value: str
    line: int
    col: int


class LuaScanner:
    """Minimal Lua 5.x lexer: ignores comments, tokenizes the rest."""

    def __init__(self, source: str):
        self._src = source
        self._n = len(source)
        self.pos = 0
        self.line = 1
        self.col = 1

    def _peek(self, offset: int = 0) -> str:
        i = self.pos + offset
        return self._src[i] if i < self._n else ""

    def _advance(self, n: int = 1) -> None:
        for _ in range(n):
            if self.pos >= self._n:
                break
            ch = self._src[self.pos]
            if ch == "\n":
                self.line += 1
                self.col = 1
            else:
                self.col += 1
            self.pos += 1

    def _skip_ws(self) -> None:
        while self.pos < self._n and self._src[self.pos] in " \t\r\n":
            self._advance()

    def _skip_comment(self) -> bool:
        if self._peek() != "-" or self._peek(1) != "-":
            return False
        self._advance(2)
        if self._peek() == "[" and self._peek(1) in "[=":
            self._advance()
            return self._skip_long_bracket()
        while self.pos < self._n and self._src[self.pos] != "\n":
            self._advance()
        return True

    def _skip_long_bracket(self) -> bool:
        start = self.pos
        while self.pos < self._n and self._src[self.pos] == "=":
            self._advance()
        level = self.pos - start
        if self.pos >= self._n or self._src[self.pos] != "[":
            return False
        self._advance()
        closer = "]" + "=" * level + "]"
        idx = self._src.find(closer, self.pos)
        if idx == -1:
            for _ in range(self.pos, self._n):
                self._advance()
        else:
            chunk = self._src[self.pos:idx]
            for ch in chunk:
                if ch == "\n":
                    self.line += 1
                    self.col = 1
                else:
                    self.col += 1
            self.pos = idx + len(closer)
            self.col += len(closer)
        return True

    def _read_string(self) -> Optional[str]:
        quote = self._src[self.pos]
        if quote not in "\"'":
            return None
        self._advance()
        chars: List[str] = []
        while self.pos < self._n:
            ch = self._src[self.pos]
            if ch == "\\":
                self._advance()
                if self.pos < self._n:
                    esc = self._src[self.pos]
                    tbl = {"\\": "\\", "\"": "\"", "'": "'",
                           "n": "\n", "r": "\r", "t": "\t"}
                    chars.append(tbl.get(esc, esc))
                    self._advance()
            elif ch == quote:
                self._advance()
                return "".join(chars)
            elif ch == "\n":
                return "".join(chars)
            else:
                chars.append(ch)
                self._advance()
        return "".join(chars)

    def _read_ident(self) -> Optional[str]:
        ch = self._src[self.pos]
        if not (ch.isalpha() or ch == "_"):
            return None
        start = self.pos
        while self.pos < self._n:
            c = self._src[self.pos]
            if c.isalnum() or c == "_":
                self._advance()
            else:
                break
        return self._src[start:self.pos]

    def _read_number(self) -> Optional[str]:
        ch = self._src[self.pos]
        if not (ch.isdigit() or (ch == "." and self._peek(1).isdigit())):
            return None
        start = self.pos
        if ch == "0" and self._peek(1) in "xX":
            self._advance(2)
            while self.pos < self._n and self._src[self.pos] in "0123456789abcdefABCDEF":
                self._advance()
            return self._src[start:self.pos]
        while self.pos < self._n and self._src[self.pos].isdigit():
            self._advance()
        if self._peek() == "." and self._peek(1).isdigit():
            self._advance()
            while self.pos < self._n and self._src[self.pos].isdigit():
                self._advance()
        if self._peek() in "eE":
            self._advance()
            if self._peek() in "+-":
                self._advance()
            while self.pos < self._n and self._src[self.pos].isdigit():
                self._advance()
        return self._src[start:self.pos]

    def next_token(self) -> Optional[Token]:
        while self.pos < self._n:
            self._skip_ws()
            if self.pos >= self._n:
                break
            if self._skip_comment():
                continue

            line, col = self.line, self.col
            ch = self._src[self.pos]

            # Long bracket (ignored)
            if ch == "[" and self._peek(1) in "[=":
                self._advance()
                if not self._skip_long_bracket():
                    continue
                continue

            if ch in "\"'":
                val = self._read_string()
                if val is not None:
                    return Token("string", val, line, col)
                continue

            if ch.isdigit() or (ch == "." and self._peek(1).isdigit()):
                val = self._read_number()
                if val is not None:
                    return Token("number", val, line, col)
                continue

            if ch.isalpha() or ch == "_":
                val = self._read_ident()
                if val is not None:
                    return Token("ident", val, line, col)
                continue

            self._advance()
            if ch == "{": return Token("lbrace",   "{",  line, col)
            if ch == "}": return Token("rbrace",   "}",  line, col)
            if ch == "(": return Token("lparen",   "(",  line, col)
            if ch == ")": return Token("rparen",   ")",  line, col)
            if ch == "[": return Token("lbracket", "[",  line, col)
            if ch == "]": return Token("rbracket", "]",  line, col)
            if ch == "=": return Token("eq",       "=",  line, col)
            if ch == ",": return Token("comma",    ",",  line, col)
            if ch == ".":
                if self._peek() == ".":
                    self._advance()
                    if self._peek() == ".":
                        self._advance()
                        return Token("op", "...", line, col)
                    return Token("op", "..", line, col)
                return Token("dot", ".", line, col)
            if ch == ":": return Token("colon",    ":",  line, col)
            if ch == ";": return Token("semi",     ";",  line, col)
            return Token("op", ch, line, col)

        return None


# ── Shared check context ──────────────────────────────────────────────────────

@dataclass
class Issue:
    filepath: str
    line: int
    col: int
    severity: str
    message: str


@dataclass
class _Ref:
    name: str
    field: str
    line: int
    col: int


class _CheckContext:
    """Mutable state shared across recursive file analysis."""
    preset_keys: Set[str]
    issues: List[Issue]
    visited: Set[str]    # realpaths already processed
    lib_dirs: List[str]
    no_follow: bool

    def __init__(self, lib_dirs: List[str], no_follow: bool = False):
        self.preset_keys = set()
        self.issues = []
        self.visited = set()
        self.lib_dirs = lib_dirs
        self.no_follow = no_follow


# ── Analyzer ──────────────────────────────────────────────────────────────────

class ThemeAnalyzer:
    """Walks tokens to extract preset_keys and key references, validates them.
    Supports recursive analysis of safe_require() dependencies."""

    def __init__(self, tokens: List[Token], filepath: str, ctx: _CheckContext):
        self._tokens = tokens
        self._n = len(tokens)
        self._filepath = filepath
        self._ctx = ctx
        self._preset_keys_range = (-1, -1)
        self._key_ranges: List[tuple] = []
        self._refs: List[_Ref] = []

    # ── helpers ──

    @staticmethod
    def _find_matching(tokens: List[Token], start: int) -> int:
        kinds = {"lbrace": "rbrace", "lparen": "rparen", "lbracket": "rbracket"}
        close = kinds.get(tokens[start].kind)
        if close is None:
            return start
        depth = 1
        i = start + 1
        while i < len(tokens) and depth > 0:
            k = tokens[i].kind
            if k == tokens[start].kind:
                depth += 1
            elif k == close:
                depth -= 1
            i += 1
        return i - 1

    @staticmethod
    def _skip_value(tokens: List[Token], start: int) -> int:
        if start >= len(tokens):
            return start
        t = tokens[start]
        if t.kind in ("string", "number", "ident", "dot", "colon", "semi", "op"):
            return start + 1
        if t.kind in ("lbrace", "lparen", "lbracket"):
            return ThemeAnalyzer._find_matching(tokens, start) + 1
        return start + 1

    # ── safe_require resolution ──

    def _resolve_require(self, modname: str) -> Optional[str]:
        """Resolve a require module name to an absolute file path."""
        parts = modname.replace(".", os.sep)
        for lib_dir in self._ctx.lib_dirs:
            for variant in (f"{parts}.lua", os.path.join(parts, "init.lua")):
                path = os.path.join(lib_dir, variant)
                if os.path.isfile(path):
                    return path
        return None

    def _match_safe_require(self, i: int) -> Optional[str]:
        """Check tokens[i:] for 'safe_require ( STRING )'.
        Returns the require name string, or None."""
        if (i + 3 < self._n
                and self._tokens[i].kind == "ident"
                and self._tokens[i].value == "safe_require"
                and self._tokens[i + 1].kind == "lparen"
                and self._tokens[i + 2].kind == "string"
                and self._tokens[i + 3].kind == "rparen"):
            return self._tokens[i + 2].value
        return None

    def _follow_require(self, modname: str) -> None:
        """Resolve and recursively analyze a safe_require'd file."""
        fpath = self._resolve_require(modname)
        if fpath is None:
            return
        realpath = os.path.realpath(fpath)
        if realpath in self._ctx.visited:
            return
        self._ctx.visited.add(realpath)

        try:
            with open(realpath, "r", encoding="utf-8") as f:
                source = f.read()
        except OSError:
            return

        scanner = LuaScanner(source)
        tokens: List[Token] = []
        while (tok := scanner.next_token()) is not None:
            tokens.append(tok)

        sub = ThemeAnalyzer(tokens, fpath, self._ctx)
        sub.analyze()

    def _extract_keys_from_file(self, filepath: str) -> Set[str]:
        """Read a file and extract top-level table keys from 'return { ... }'.
        Used for preset_keys library files like lib/preset_keys.lua."""
        try:
            with open(filepath, "r", encoding="utf-8") as f:
                source = f.read()
        except OSError:
            return set()

        scanner = LuaScanner(source)
        tokens: List[Token] = []
        while (tok := scanner.next_token()) is not None:
            tokens.append(tok)

        return self._extract_keys_from_tokens(tokens)

    def _extract_keys_from_tokens(self, tokens: List[Token]) -> Set[str]:
        """Extract top-level keys from a table token sequence.
        Handles: '{ Key1 = ..., Key2 = ..., ... }' or 'return { Key1 = ..., ... }'."""
        i = 0
        n = len(tokens)

        # Look for 'return'? '{' or just '{' at top level
        if i < n and tokens[i].kind == "ident" and tokens[i].value == "return":
            i += 1  # skip 'return'

        if i >= n or tokens[i].kind != "lbrace":
            return set()

        lbrace = i
        rbrace = ThemeAnalyzer._find_matching(tokens, lbrace)
        return self._extract_table_keys(tokens, lbrace + 1, rbrace)

    def _extract_table_keys(self, tokens: List[Token],
                             start: int, end: int) -> Set[str]:
        """Walk tokens[start:end] at depth 1, collecting key names."""
        keys: Set[str] = set()
        i = start
        while i < end:
            t = tokens[i]

            if t.kind in ("lbrace", "lparen", "lbracket"):
                # Skip nested block
                i = ThemeAnalyzer._find_matching(tokens, i) + 1
                continue
            if t.kind in ("rbrace", "rparen", "rbracket"):
                i += 1
                continue

            if t.kind == "ident":
                if i + 1 < end and tokens[i + 1].kind == "eq":
                    keys.add(t.value)
                    i = ThemeAnalyzer._skip_value(tokens, i + 2)
                    continue
            elif t.kind == "string":
                if i + 1 < end and tokens[i + 1].kind == "eq":
                    keys.add(t.value)
                    i = ThemeAnalyzer._skip_value(tokens, i + 2)
                    continue
            elif t.kind == "lbracket":
                i = ThemeAnalyzer._find_matching(tokens, i) + 1
                continue

            i += 1
        return keys

    # ── inline preset_keys extraction ──

    def _extract_preset_keys_inline(self, pos: int) -> int:
        """pos is right after '{' of 'preset_keys = {'.
        Walk at depth 1, collecting key names.
        Returns position after matching '}'."""
        end = self._find_matching(self._tokens, pos - 1)
        keys = self._extract_table_keys(self._tokens, pos, end)
        self._ctx.preset_keys.update(keys)
        return end + 1

    # ── block range detection (for context-aware field checks) ──

    def _find_block_ranges(self) -> None:
        self._preset_keys_range = (-1, -1)
        self._key_ranges = []
        i = 0
        while i < self._n:
            t = self._tokens[i]
            if (t.kind == "ident" and t.value == "preset_keys"
                    and i + 2 < self._n
                    and self._tokens[i + 1].kind == "eq"
                    and self._tokens[i + 2].kind == "lbrace"):
                end = self._find_matching(self._tokens, i + 2)
                self._preset_keys_range = (i + 3, end)
                i = end + 1
                continue
            if (t.kind == "ident" and t.value == "key"
                    and i + 1 < self._n
                    and self._tokens[i + 1].kind == "lbrace"):
                end = self._find_matching(self._tokens, i + 1)
                self._key_ranges.append((i + 2, end))
                i = end + 1
                continue
            i += 1

    def _in_range(self, pos: int, r: tuple) -> bool:
        return r[0] <= pos < r[1]

    def _in_preset_keys(self, pos: int) -> bool:
        return self._in_range(pos, self._preset_keys_range)

    def _in_any_key_block(self, pos: int) -> bool:
        return any(self._in_range(pos, r) for r in self._key_ranges)

    # ── reference collection ──

    def _collect_refs(self) -> None:
        self._find_block_ranges()
        i = 0
        while i < self._n:
            t = self._tokens[i]

            # Unconditional scalar fields
            if t.kind == "ident" and t.value in _UNCONDITIONAL_REF_FIELDS:
                if (i + 2 < self._n
                        and self._tokens[i + 1].kind == "eq"
                        and self._tokens[i + 2].kind == "string"):
                    self._refs.append(_Ref(
                        self._tokens[i + 2].value, t.value, t.line, t.col))
                    i += 3
                    continue

            # send within preset_keys
            if (t.kind == "ident" and self._in_preset_keys(i)
                    and t.value in _PRESET_KEYS_REF_FIELDS):
                if (i + 2 < self._n
                        and self._tokens[i + 1].kind == "eq"
                        and self._tokens[i + 2].kind == "string"):
                    self._refs.append(_Ref(
                        self._tokens[i + 2].value, t.value, t.line, t.col))
                    i += 3
                    continue

            # actions array within preset_keys
            if (t.kind == "ident" and self._in_preset_keys(i)
                    and t.value in _PRESET_KEYS_ARRAY_FIELDS):
                if (i + 2 < self._n
                        and self._tokens[i + 1].kind == "eq"
                        and self._tokens[i + 2].kind == "lbrace"):
                    arr_end = self._find_matching(self._tokens, i + 2)
                    j = i + 3
                    while j < arr_end:
                        if self._tokens[j].kind == "string":
                            self._refs.append(_Ref(
                                self._tokens[j].value, t.value,
                                self._tokens[j].line, self._tokens[j].col))
                        j += 1
                    i = arr_end + 1
                    continue

            # popup array within key{} blocks
            if (t.kind == "ident" and self._in_any_key_block(i)
                    and t.value in _KEY_ARRAY_FIELDS):
                if (i + 2 < self._n
                        and self._tokens[i + 1].kind == "eq"
                        and self._tokens[i + 2].kind == "lbrace"):
                    arr_end = self._find_matching(self._tokens, i + 2)
                    j = i + 3
                    while j < arr_end:
                        if self._tokens[j].kind == "string":
                            self._refs.append(_Ref(
                                self._tokens[j].value, t.value,
                                self._tokens[j].line, self._tokens[j].col))
                        j += 1
                    i = arr_end + 1
                    continue

            i += 1

    # ── validation ──

    def _validate(self) -> None:
        for ref in self._refs:
            if _is_valid(ref.name, self._ctx.preset_keys, ref.field):
                continue
            self._ctx.issues.append(Issue(
                self._filepath, ref.line, ref.col, "warning",
                f"preset key \"{ref.name}\" is not defined (in '{ref.field}')"))

    # ── main analysis ──

    def analyze(self) -> None:
        """Walk tokens, collecting preset_keys, following requires,
        and validating key references. Results stored in self._ctx."""
        i = 0
        while i < self._n:
            t = self._tokens[i]

            # ── Pattern: preset_keys = { ... }   (inline) ──
            if (t.kind == "ident" and t.value == "preset_keys"
                    and i + 2 < self._n
                    and self._tokens[i + 1].kind == "eq"
                    and self._tokens[i + 2].kind == "lbrace"):
                i = self._extract_preset_keys_inline(i + 3)
                continue

            # ── Pattern: preset_keys = safe_require ( STRING ) ──
            if (t.kind == "ident" and t.value == "preset_keys"
                    and i + 2 < self._n
                    and self._tokens[i + 1].kind == "eq"):
                req = self._match_safe_require(i + 2)
                if req is not None:
                    fpath = self._resolve_require(req)
                    if fpath is not None:
                        keys = self._extract_keys_from_file(fpath)
                        self._ctx.preset_keys.update(keys)
                        # Also recursively analyze for key references
                        if not self._ctx.no_follow:
                            self._follow_require(req)
                    i += 6
                    continue

            # ── Pattern: preset_keys = merge ( safe_require ( STRING ) , { ... } ) ──
            if (t.kind == "ident" and t.value == "preset_keys"
                    and i + 9 < self._n
                    and self._tokens[i + 1].kind == "eq"
                    and self._tokens[i + 2].kind == "ident"
                    and self._tokens[i + 2].value == "merge"
                    and self._tokens[i + 3].kind == "lparen"
                    and self._tokens[i + 4].kind == "ident"
                    and self._tokens[i + 4].value == "safe_require"
                    and self._tokens[i + 5].kind == "lparen"
                    and self._tokens[i + 6].kind == "string"
                    and self._tokens[i + 7].kind == "rparen"
                    and self._tokens[i + 8].kind == "comma"
                    and self._tokens[i + 9].kind == "lbrace"):
                req = self._tokens[i + 6].value
                # Extract keys from required file
                fpath = self._resolve_require(req)
                if fpath is not None:
                    keys = self._extract_keys_from_file(fpath)
                    self._ctx.preset_keys.update(keys)
                # Extract keys from inline table
                inline_end = self._find_matching(self._tokens, i + 9)
                inline_keys = self._extract_table_keys(
                    self._tokens, i + 10, inline_end)
                self._ctx.preset_keys.update(inline_keys)
                # Follow requires
                if not self._ctx.no_follow:
                    if fpath is not None:
                        self._follow_require(req)
                    # Also scan inline table for safe_require calls
                    self._scan_inline_for_requires(i + 10, inline_end)
                # Skip past merge(...)
                # After rbrace: rparen closes merge call
                i = inline_end + 2 if (inline_end + 1 < self._n
                                        and self._tokens[inline_end + 1].kind == "rparen") else inline_end + 1
                continue

            # ── General safe_require calls (layouts, styles, colors, etc.) ──
            if not self._ctx.no_follow:
                # Pattern: any_name = safe_require ( STRING )
                if (t.kind == "ident" and i + 2 < self._n
                        and self._tokens[i + 1].kind == "eq"):
                    req = self._match_safe_require(i + 2)
                    if req is not None:
                        self._follow_require(req)
                        i += 6
                        continue

                # Pattern: any_name = merge ( safe_require ( STRING ) , { ... } )
                if (t.kind == "ident" and i + 9 < self._n
                        and self._tokens[i + 1].kind == "eq"
                        and self._tokens[i + 2].kind == "ident"
                        and self._tokens[i + 2].value == "merge"
                        and self._tokens[i + 3].kind == "lparen"
                        and self._tokens[i + 4].kind == "ident"
                        and self._tokens[i + 4].value == "safe_require"
                        and self._tokens[i + 5].kind == "lparen"
                        and self._tokens[i + 6].kind == "string"
                        and self._tokens[i + 7].kind == "rparen"
                        and self._tokens[i + 8].kind == "comma"
                        and self._tokens[i + 9].kind == "lbrace"):
                    req = self._tokens[i + 6].value
                    self._follow_require(req)
                    # Scan inline table for nested requires too
                    inline_end = self._find_matching(self._tokens, i + 9)
                    self._scan_inline_for_requires(i + 10, inline_end)
                    i = inline_end + 2 if (inline_end + 1 < self._n
                                            and self._tokens[inline_end + 1].kind == "rparen") else inline_end + 1
                    continue

            i += 1

        # Collect and validate references from this file
        self._collect_refs()
        self._validate()

    def _scan_inline_for_requires(self, start: int, end: int) -> None:
        """Scan tokens[start:end] at depth 1 for nested safe_require calls."""
        i = start
        while i < end:
            t = self._tokens[i]
            if t.kind in ("lbrace", "lparen", "lbracket"):
                i = self._find_matching(self._tokens, i) + 1
                continue
            if t.kind in ("rbrace", "rparen", "rbracket"):
                i += 1
                continue

            req = self._match_safe_require(i)
            if req is not None:
                self._follow_require(req)
                i += 4
                continue

            i += 1


# ── Modifier names (for send field: parseSend supports Control+x syntax) ─────

_MODIFIER_NAMES: Set[str] = {"Control", "Shift", "Alt", "Meta", "Super"}

# ── Validation logic ──────────────────────────────────────────────────────────

def _is_valid(name: str, preset_keys: Set[str], field: str = "") -> bool:
    if name in BUILTIN_KEYS:
        return True
    if name in preset_keys:
        return True
    if name == "":
        return True
    if name.startswith("ic@"):
        return True
    # send field supports modifier syntax: Control+x, Shift+Return, etc.
    if field == "send" and "+" in name:
        parts = name.split("+")
        key_name = parts[-1]
        if key_name in BUILTIN_KEYS or key_name in preset_keys:
            if all(p in _MODIFIER_NAMES for p in parts[:-1]):
                return True
    if len(name) == 1 and 0x20 <= ord(name) <= 0x7E:
        return True
    return False


# ── Entry point ───────────────────────────────────────────────────────────────

def _detect_lib_dirs(filepath: str) -> List[str]:
    """Find lib/ directories relative to the theme file."""
    dirs: List[str] = []
    theme_dir = os.path.dirname(os.path.abspath(filepath))
    lib = os.path.join(theme_dir, "lib")
    if os.path.isdir(lib):
        dirs.append(lib)
    # Also try one level up for monorepo layouts
    parent_lib = os.path.join(os.path.dirname(theme_dir), "lib")
    if os.path.isdir(parent_lib):
        dirs.append(parent_lib)
    return dirs


def check_file(filepath: str, extra_lib_dirs: List[str] | None = None,
               no_follow: bool = False) -> _CheckContext:
    """Analyze a theme file and all its safe_require'd dependencies."""
    lib_dirs = list(extra_lib_dirs or [])
    lib_dirs.extend(_detect_lib_dirs(filepath))

    ctx = _CheckContext(lib_dirs, no_follow=no_follow)
    ctx.visited.add(os.path.realpath(filepath))

    with open(filepath, "r", encoding="utf-8") as f:
        source = f.read()

    scanner = LuaScanner(source)
    tokens: List[Token] = []
    while (tok := scanner.next_token()) is not None:
        tokens.append(tok)

    analyzer = ThemeAnalyzer(tokens, filepath, ctx)
    analyzer.analyze()

    return ctx


# ── CLI ───────────────────────────────────────────────────────────────────────

def _dump_builtin() -> None:
    for key in sorted(BUILTIN_KEYS):
        print(key)


def main() -> None:
    args = sys.argv[1:]

    if not args:
        print(__doc__.strip())
        sys.exit(1)

    if "--dump-builtin" in args:
        _dump_builtin()
        return

    lib_dirs: List[str] = []
    no_follow = False
    files: List[str] = []

    i = 0
    while i < len(args):
        a = args[i]
        if a == "--lib-dir" and i + 1 < len(args):
            lib_dirs.append(args[i + 1])
            i += 2
        elif a.startswith("--lib-dir="):
            lib_dirs.append(a.split("=", 1)[1])
            i += 1
        elif a == "--no-follow":
            no_follow = True
            i += 1
        elif a.startswith("-"):
            i += 1
        elif os.path.isfile(a):
            files.append(a)
            i += 1
        else:
            print(f"check-theme-keys: not a file: {a}", file=sys.stderr)
            i += 1

    if not files:
        print("check-theme-keys: no .lua files specified", file=sys.stderr)
        sys.exit(1)

    total_issues = 0
    for fp in files:
        try:
            ctx = check_file(fp, lib_dirs, no_follow=no_follow)
        except Exception as exc:
            import traceback
            traceback.print_exc()
            print(f"{fp}:0:0: error: {exc}", file=sys.stderr)
            total_issues += 1
            continue

        for iss in ctx.issues:
            print(f"{os.path.relpath(iss.filepath)}:{iss.line}:{iss.col}: {iss.severity}: {iss.message}")
        total_issues += len(ctx.issues)

    if total_issues > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
