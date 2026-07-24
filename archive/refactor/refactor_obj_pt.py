import os
import re

def refactor():
    obj_path = 'src/main/java/hara/lang/base/Obj.java'
    target_dir = 'src/main/java/hara/lang/data/types'
    target_path = os.path.join(target_dir, 'ObjPersistent.java')

    if not os.path.exists(target_dir):
        os.makedirs(target_dir)

    # Perform extraction only if target doesn't exist to avoid overwriting on retry if partial
    # But for safety, I assume clean state or I read Obj.java to see if PT is there.

    with open(obj_path, 'r') as f:
        lines = f.readlines()

    # Extract PT
    start = -1
    end = -1
    brace_count = 0
    found = False

    content_lines = []

    for i, line in enumerate(lines):
        if 'abstract class PT' in line and not found:
            start = i
            found = True

        if found:
            content_lines.append(line)
            brace_count += line.count('{')
            brace_count -= line.count('}')
            if brace_count == 0:
                end = i
                break

    if start != -1 and end != -1:
        # Create ObjPersistent.java
        body_lines = content_lines
        # Detect indent
        first_line = body_lines[0]
        indent = len(first_line) - len(first_line.lstrip())
        if indent > 0:
            body_lines = [line[indent:] if len(line) >= indent else line.lstrip() for line in body_lines]

        body = "".join(body_lines)
        body = body.replace('abstract class PT', 'public abstract class ObjPersistent')
        body = body.replace('public PT()', 'public ObjPersistent()')
        body = body.replace('public PT(IMetadata meta)', 'public ObjPersistent(IMetadata meta)')

        imports = [
            "package hara.lang.data.types;",
            "",
            "import hara.lang.protocol.*;",
            "import hara.lang.base.Iter;",
            "import java.util.Iterator;",
            ""
        ]

        with open(target_path, 'w') as f:
            f.write("\n".join(imports) + body)
        print(f"Created {target_path}")

        # Remove from Obj.java
        new_obj_lines = lines[:start] + lines[end+1:]
        with open(obj_path, 'w') as f:
            f.writelines(new_obj_lines)
        print(f"Updated {obj_path}")

    # Update references even if extraction didn't happen (maybe already extracted but refs fail)
    update_references(target_path)

def update_references(target_path):
    for root, dirs, files in os.walk('src'):
        for file in files:
            if file.endswith('.java'):
                filepath = os.path.join(root, file)
                # Normalize paths for comparison
                if os.path.abspath(filepath) == os.path.abspath(target_path):
                    continue

                with open(filepath, 'r') as f:
                    content = f.read()

                new_content = content

                # Replace FQCN
                new_content = new_content.replace('hara.lang.base.Obj.PT', 'hara.lang.data.types.ObjPersistent')

                # Replace Obj.PT
                if 'Obj.PT' in new_content:
                     new_content = new_content.replace('Obj.PT', 'ObjPersistent')
                     if 'import hara.lang.data.types.ObjPersistent;' not in new_content:
                         pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
                         if pkg_match:
                             end_pos = pkg_match.end()
                             new_content = new_content[:end_pos] + "\n\nimport hara.lang.data.types.ObjPersistent;" + new_content[end_pos:]
                         else:
                             new_content = "import hara.lang.data.types.ObjPersistent;\n" + new_content

                # Replace plain PT usage
                # Check for `extends PT`, `new PT`, `(PT)`, `PT `
                # Regex \bPT\b
                if re.search(r'\bPT\b', new_content):
                    new_content = re.sub(r'\bPT\b', 'ObjPersistent', new_content)

                    if 'import hara.lang.data.types.ObjPersistent;' not in new_content:
                         if 'package hara.lang.data.types;' not in new_content:
                             pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
                             if pkg_match:
                                 end_pos = pkg_match.end()
                                 new_content = new_content[:end_pos] + "\n\nimport hara.lang.data.types.ObjPersistent;" + new_content[end_pos:]
                             else:
                                 new_content = "import hara.lang.data.types.ObjPersistent;\n" + new_content

                if new_content != content:
                    with open(filepath, 'w') as f:
                        f.write(new_content)
                    print(f"Updated references in {filepath}")

if __name__ == '__main__':
    refactor()
