import os
import re

def refactor():
    src_dir = 'src/main/java/hara/lang/base'
    dest_dir = 'src/main/java/hara/lang/data'
    ut_path = os.path.join(src_dir, 'Ut.java')

    with open(ut_path, 'r') as f:
        content = f.read()

    # Helper to extract block
    def extract_block(name):
        start_idx = content.find(f'class {name}')
        if start_idx == -1: return None

        # Find start of line (public ...)
        start_line_idx = content.rfind('\n', 0, start_idx) + 1

        # Find brace
        idx = content.find('{', start_idx)
        if idx == -1: return None

        brace_count = 1
        idx += 1
        while brace_count > 0 and idx < len(content):
            if content[idx] == '{': brace_count += 1
            elif content[idx] == '}': brace_count -= 1
            idx += 1

        return content[start_line_idx:idx]

    classes = ['AsList', 'AsMap', 'AsSet']

    for name in classes:
        block = extract_block(name)
        if block:
            # Prepare new content
            # Imports
            imports = """package hara.lang.data;

import hara.lang.data.types.ObjMutable;
import hara.lang.data.types.*;
import hara.lang.protocol.*;
import hara.lang.base.Iter;
import hara.lang.base.primitive.*;
import java.util.Iterator;
import java.util.Collection;
"""
            new_content = imports + "\n" + block

            with open(os.path.join(dest_dir, f'{name}.java'), 'w') as f:
                f.write(new_content)
            print(f"Created {name}.java")

            # Remove from Ut.java
            content = content.replace(block, "")

    with open(ut_path, 'w') as f:
        f.write(content)
    print("Updated Ut.java")

    # Update references
    update_references()

def update_references():
    replacements = {
        'Ut.AsList': 'AsList',
        'Ut.AsMap': 'AsMap',
        'Ut.AsSet': 'AsSet'
    }

    imports = {
        'AsList': 'hara.lang.data.AsList',
        'AsMap': 'hara.lang.data.AsMap',
        'AsSet': 'hara.lang.data.AsSet'
    }

    for root, dirs, files in os.walk('src'):
        for file in files:
            if file.endswith('.java'):
                filepath = os.path.join(root, file)
                if filepath.endswith('Ut.java'): continue

                with open(filepath, 'r') as f:
                    file_content = f.read()

                new_content = file_content
                for old, new in replacements.items():
                    if old in new_content:
                        new_content = new_content.replace(old, new)
                        imp = f'import {imports[new]};'
                        if imp not in new_content:
                             pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
                             if pkg_match:
                                 end = pkg_match.end()
                                 new_content = new_content[:end] + '\n\n' + imp + new_content[end:]

                if new_content != file_content:
                    with open(filepath, 'w') as f:
                        f.write(new_content)
                    print(f"Updated {filepath}")

if __name__ == '__main__':
    refactor()
