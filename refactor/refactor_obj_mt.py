import os
import re

def refactor():
    obj_path = 'src/main/java/hara/lang/base/Obj.java'
    target_dir = 'src/main/java/hara/lang/data/types'
    target_path = os.path.join(target_dir, 'ObjMutable.java')

    if not os.path.exists(target_dir):
        os.makedirs(target_dir)

    with open(obj_path, 'r') as f:
        lines = f.readlines()

    # Extract MT
    start = -1
    end = -1
    brace_count = 0
    found = False

    mt_content_lines = []

    for i, line in enumerate(lines):
        if 'abstract class MT' in line and not found:
            start = i
            found = True

        if found:
            mt_content_lines.append(line)
            brace_count += line.count('{')
            brace_count -= line.count('}')
            if brace_count == 0:
                end = i
                break

    if start != -1 and end != -1:
        # Create ObjMutable.java
        mt_body_str = "".join(mt_content_lines)

        # Unindent
        # Assuming 2 spaces indent based on file content observation
        mt_body_lines = mt_content_lines
        # Detect indent of first line
        first_line = mt_body_lines[0]
        indent = len(first_line) - len(first_line.lstrip())
        if indent > 0:
            mt_body_lines = [line[indent:] if len(line) >= indent else line.lstrip() for line in mt_body_lines]

        mt_body = "".join(mt_body_lines)
        mt_body = mt_body.replace('abstract class MT', 'public abstract class ObjMutable')

        imports = [
            "package hara.lang.data.types;",
            "",
            "import hara.lang.protocol.*;",
            "import hara.lang.base.Iter;",
            "import java.util.Iterator;",
            ""
        ]

        with open(target_path, 'w') as f:
            f.write("\n".join(imports) + mt_body)
        print(f"Created {target_path}")

        # Remove from Obj.java
        new_obj_lines = lines[:start] + lines[end+1:]
        with open(obj_path, 'w') as f:
            f.writelines(new_obj_lines)
        print(f"Updated {obj_path}")

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

                # Replace FQCN
                new_content = new_content.replace('hara.lang.base.Obj.MT', 'hara.lang.data.types.ObjMutable')

                # Replace Obj.MT
                if 'Obj.MT' in new_content:
                     new_content = new_content.replace('Obj.MT', 'ObjMutable')
                     # Add import if missing
                     if 'import hara.lang.data.types.ObjMutable;' not in new_content:
                         # Insert after package declaration
                         pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
                         if pkg_match:
                             end_pos = pkg_match.end()
                             new_content = new_content[:end_pos] + "\n\nimport hara.lang.data.types.ObjMutable;" + new_content[end_pos:]
                         else:
                             new_content = "import hara.lang.data.types.ObjMutable;\n" + new_content

                if new_content != content:
                    with open(filepath, 'w') as f:
                        f.write(new_content)
                    print(f"Updated references in {filepath}")

if __name__ == '__main__':
    refactor()
