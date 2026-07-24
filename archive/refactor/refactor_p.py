import os
import re

def refactor():
    base_dir = 'src/main/java/hara/lang/base'
    prim_dir = 'src/main/java/hara/lang/base/primitive'
    if not os.path.exists(prim_dir):
        os.makedirs(prim_dir)

    p_path = os.path.join(base_dir, 'P.java')

    with open(p_path, 'r') as f:
        content = f.read()

    # Extract inner classes/interfaces
    # Helper to extract block by name
    def extract_block(name, type_decl="interface"):
        # Regex to find "public [static] interface/class Name {"
        # pattern = r'public\s+(?:static\s+)?' + type_decl + r'\s+' + name + r'\s*(?:extends\s+[\w\.]+\s*(?:implements\s+[\w\.,\s]+)?|implements\s+[\w\.,\s]+)?\{'
        # Simplified regex finding start
        start_idx = content.find(f' {name} ')
        if start_idx == -1: return None

        # Find start of line
        start_line_idx = content.rfind('\n', 0, start_idx) + 1

        # Find matching brace
        brace_count = 0
        idx = content.find('{', start_idx)
        if idx == -1: return None

        start_brace = idx
        brace_count = 1
        idx += 1

        while brace_count > 0 and idx < len(content):
            if content[idx] == '{':
                brace_count += 1
            elif content[idx] == '}':
                brace_count -= 1
            idx += 1

        if brace_count == 0:
            end_line_idx = idx # End of block
            return content[start_line_idx:end_line_idx]
        return None

    classes = {
        'Bits': 'interface',
        'Box': 'interface',
        'Cast': 'interface',
        'Complex': 'class',
        'Ratio': 'class'
    }

    for name, type_decl in classes.items():
        block = extract_block(name, type_decl)
        if block:
            # Adjust content
            # Remove "static" from definition if present (top level classes aren't static)
            block = block.replace('public static ', 'public ')

            # Imports
            imports = """package hara.lang.base.primitive;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;
import hara.lang.base.Num;
"""
            # Note: Num usage in Ratio. Num is in hara.lang.base currently.

            new_content = imports + "\n" + block

            with open(os.path.join(prim_dir, f'{name}.java'), 'w') as f:
                f.write(new_content)
            print(f"Created {name}.java")

    # Delete P.java?
    # os.remove(p_path) # Wait, confirm extraction first

    # Update references
    update_references()

def update_references():
    replacements = {
        'P.Bits': 'Bits',
        'P.Box': 'Box',
        'P.Cast': 'Cast',
        'P.Complex': 'Complex',
        'P.Ratio': 'Ratio'
    }

    # Imports to add
    import_map = {
        'Bits': 'hara.lang.base.primitive.Bits',
        'Box': 'hara.lang.base.primitive.Box',
        'Cast': 'hara.lang.base.primitive.Cast',
        'Complex': 'hara.lang.base.primitive.Complex',
        'Ratio': 'hara.lang.base.primitive.Ratio'
    }

    for root, dirs, files in os.walk('src'):
        for file in files:
            if file.endswith('.java'):
                filepath = os.path.join(root, file)
                if filepath.endswith('P.java'): continue

                with open(filepath, 'r') as f:
                    content = f.read()

                new_content = content

                # Replace usages
                for old, new in replacements.items():
                    if old in new_content:
                        new_content = new_content.replace(old, new)
                        # Add import
                        imp = f'import {import_map[new]};'
                        if imp not in new_content and f'package {import_map[new].rsplit(".",1)[0]};' not in new_content:
                             # Add import
                             pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
                             if pkg_match:
                                 end = pkg_match.end()
                                 new_content = new_content[:end] + '\n\n' + imp + new_content[end:]

                if new_content != content:
                    with open(filepath, 'w') as f:
                        f.write(new_content)
                    print(f"Updated {filepath}")

if __name__ == '__main__':
    refactor()
