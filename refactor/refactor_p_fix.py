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
            # DO NOT Remove "static" from definition
            # But remove "static" from the class/interface declaration itself because top level types cannot be static
            # public static interface Bits -> public interface Bits
            block = block.replace(f'public static {type_decl} {name}', f'public {type_decl} {name}')

            # Imports
            imports = """package hara.lang.base.primitive;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;
import hara.lang.base.Num;
"""
            new_content = imports + "\n" + block

            with open(os.path.join(prim_dir, f'{name}.java'), 'w') as f:
                f.write(new_content)
            print(f"Created {name}.java")

    # Update references (skipped as likely done or handled)
    # update_references()

if __name__ == '__main__':
    refactor()
