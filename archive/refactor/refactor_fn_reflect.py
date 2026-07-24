import os
import re

def refactor():
    target_dir = 'src/main/java/hara/lang/base/primitive'
    if not os.path.exists(target_dir):
        os.makedirs(target_dir)

    fn_path = 'src/main/java/hara/lang/base/Fn.java'
    reflect_path = 'src/main/java/hara/lang/base/primitive/Reflect.java'

    with open(fn_path, 'r') as f:
        lines = f.readlines()

    reflect_content = []
    new_fn_lines = []

    in_reflect = False
    brace_count = 0

    for line in lines:
        if 'public interface Reflect {' in line:
            in_reflect = True
            brace_count = 1
            # Start collecting Reflect content
            # Adjust package and imports for new file
            continue # Don't add 'public interface Reflect {' line yet, will construct manually

        if in_reflect:
            brace_count += line.count('{')
            brace_count -= line.count('}')

            if brace_count == 0:
                in_reflect = False
                continue

            reflect_content.append(line)
        else:
            new_fn_lines.append(line)

    # Write Reflect.java
    reflect_body = "".join(reflect_content)
    # Unindent?
    # Assuming 2 spaces indent
    reflect_body_lines = reflect_content
    # Find common indent?

    reflect_header = """package hara.lang.base.primitive;

import hara.lang.data.types.ObjFn;
import hara.lang.base.Ex;
import hara.lang.base.Iter;
import hara.lang.protocol.IFn;
import hara.lang.protocol.IMetadata;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.*;

public interface Reflect {
"""
    reflect_footer = "}\n"

    with open(reflect_path, 'w') as f:
        f.write(reflect_header + reflect_body + reflect_footer)
    print(f"Created {reflect_path}")

    # Write Fn.java
    # Add import
    import_stmt = "import hara.lang.base.primitive.Reflect;\n"
    # Insert after last import
    last_import_idx = -1
    for i, line in enumerate(new_fn_lines):
        if line.startswith('import '):
            last_import_idx = i

    if last_import_idx != -1:
        new_fn_lines.insert(last_import_idx + 1, import_stmt)
    else:
        new_fn_lines.insert(2, import_stmt) # After package

    with open(fn_path, 'w') as f:
        f.writelines(new_fn_lines)
    print(f"Updated {fn_path}")

    # Update references
    update_references()

def update_references():
    for root, dirs, files in os.walk('src'):
        for file in files:
            if file.endswith('.java'):
                filepath = os.path.join(root, file)
                with open(filepath, 'r') as f:
                    content = f.read()

                new_content = content

                # Replace Fn.Reflect -> Reflect
                new_content = new_content.replace('Fn.Reflect', 'Reflect')

                # Add import if needed
                if 'Reflect.' in new_content or 'Reflect ' in new_content:
                     if 'import hara.lang.base.primitive.Reflect;' not in new_content and 'package hara.lang.base.primitive;' not in new_content:
                         pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
                         if pkg_match:
                             end = pkg_match.end()
                             new_content = new_content[:end] + '\n\nimport hara.lang.base.primitive.Reflect;' + new_content[end:]

                if new_content != content:
                    with open(filepath, 'w') as f:
                        f.write(new_content)
                    print(f"Updated references in {filepath}")

if __name__ == '__main__':
    refactor()
