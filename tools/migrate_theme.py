#!/usr/bin/env python3
"""Migration script: convert old keyboard YAML format to new flex-based row format.

Usage: python3 migrate_theme.py <input.yaml>
"""

import sys
import ruamel.yaml
from pathlib import Path

MAX_TOTAL_WEIGHT = 100


def detect_rows(keys, keyboard):
    """Auto-wrap flat keys list into rows using the old algorithm."""
    default_width = float(keyboard.get('width', 0) or 0)
    columns = int(keyboard.get('columns', 30) or 30)
    if columns == -1:
        columns = 999999

    rows = []
    current_row = []
    x = 0.0
    column = 0

    for key in keys:
        if not isinstance(key, dict):
            current_row.append(key)
            continue

        click = key.get('click', '')
        key_width = float(key.get('width', 0) or 0)
        if key_width == 0 and click:
            key_width = default_width

        if column >= columns or (x + key_width) > MAX_TOTAL_WEIGHT:
            if current_row:
                rows.append(current_row)
            current_row = []
            x = 0.0
            column = 0

        current_row.append(key)

        if click:
            column += 1
        x += key_width

    if current_row:
        rows.append(current_row)

    return rows


def convert_key_in_place(key):
    """Convert a single key from old format to new format, in-place."""
    # Remove height (per-key height no longer supported)
    if 'height' in key:
        del key['height']

    # Convert width: divide by 100
    if 'width' in key:
        old_width = float(key.get('width', 0) or 0)
        key['width'] = round(old_width / 100, 4)

    # Check if this is a spacer (no click, has width)
    click = key.get('click', '')
    if not click:
        if key.get('width', 0) > 0:
            key['spacer'] = True
        else:
            # spacer is a key with no click and no width - remove empty entries
            pass


def migrate_keyboard(kb_name, kb_data):
    """Migrate a single keyboard from old format to new format."""
    if 'rows' in kb_data:
        print(f"  Keyboard '{kb_name}': already has rows, skipping")
        return

    if 'keys' not in kb_data:
        print(f"  Keyboard '{kb_name}': no keys found, skipping")
        return

    keys = kb_data['keys']
    if not keys:
        print(f"  Keyboard '{kb_name}': empty keys list, skipping")
        return

    print(f"  Keyboard '{kb_name}': converting {len(keys)} keys to rows...")

    # Detect rows
    raw_rows = detect_rows(keys, kb_data)

    # Convert each key in place
    for row_keys in raw_rows:
        for key in row_keys:
            if isinstance(key, dict):
                convert_key_in_place(key)

    # Build new rows structure
    new_rows = ruamel.yaml.comments.CommentedSeq()
    for row_keys in raw_rows:
        row_map = ruamel.yaml.comments.CommentedMap()
        row_map['keys'] = row_keys
        new_rows.append(row_map)

    # Replace keys with rows
    del kb_data['keys']
    kb_data['rows'] = new_rows

    # Remove deprecated keyboard-level fields
    for field in ('width', 'height', 'columns'):
        if field in kb_data:
            del kb_data[field]

    print(f"    -> {len(raw_rows)} rows created")


def migrate_style(style_data):
    """Remove deprecated fields from style section."""
    removed = []
    for field in ('key_height', 'key_width'):
        if field in style_data:
            del style_data[field]
            removed.append(field)
    if removed:
        print(f"  Removed from style: {', '.join(removed)}")

    # Remove height from __include presets
    for preset_key, preset_val in list(style_data.items()):
        if isinstance(preset_val, dict) and 'height' in preset_val:
            del preset_val['height']
            print(f"  Removed height from style preset: {preset_key}")


def migrate_file(filepath):
    """Migrate a single YAML file."""
    yaml = ruamel.yaml.YAML()
    yaml.preserve_quotes = True
    yaml.width = 4096
    yaml.map_indent = 2
    yaml.sequence_indent = 4
    yaml.sequence_dash_offset = 2

    print(f"\nProcessing: {filepath}")

    with open(filepath, 'r', encoding='utf-8') as f:
        data = yaml.load(f)

    if data is None:
        print("  Empty file, skipping")
        return

    # Migrate style section
    if 'style' in data:
        print("  Migrating style...")
        migrate_style(data['style'])

    # Migrate top-level __include presets (e.g., 0-9, a-z, 功能 in 喵呜呜呜)
    for key in list(data.keys()):
        if key in ('style', 'preset_keyboards', 'preset_keys',
                   'preset_color_schemes', 'fallback_colors',
                   'liquid_keyboard', 'preedit', 'window', 'tool_bar',
                   'config_version', 'name', 'author'):
            continue
        val = data[key]
        if isinstance(val, dict) and 'height' in val:
            del val['height']
            print(f"  Removed height from top-level preset: {key}")

    # Migrate preset_keyboards
    if 'preset_keyboards' in data:
        print(f"  Migrating preset_keyboards ({len(data['preset_keyboards'])} keyboards)...")
        for kb_name, kb_data in data['preset_keyboards'].items():
            migrate_keyboard(kb_name, kb_data)

    with open(filepath, 'w', encoding='utf-8') as f:
        yaml.dump(data, f)
    print(f"  Written: {filepath}")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    for filepath in sys.argv[1:]:
        if not Path(filepath).exists():
            print(f"File not found: {filepath}")
            continue
        migrate_file(filepath)


if __name__ == '__main__':
    main()
