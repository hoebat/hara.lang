import os
import shutil
import re

def refactor():
    target_dir = 'src/main/java/hara/lang/base/primitive'
    if not os.path.exists(target_dir):
        os.makedirs(target_dir)

    src_file = 'src/main/java/hara/lang/base/Arr.java'
    dest_file = 'src/main/java/hara/lang/base/primitive/Array.java'

    if os.path.exists(src_file):
        shutil.move(src_file, dest_file)
        print(f"Moved {src_file} to {dest_file}")

    # Update content of Array.java
    if os.path.exists(dest_file):
        with open(dest_file, 'r') as f:
            content = f.read()

        new_content = content.replace('package hara.lang.base;', 'package hara.lang.base.primitive;')
        new_content = new_content.replace('interface Arr', 'interface Array')
        new_content = new_content.replace('class Arr', 'class Array')
        # Replace Arr. calls inside the file to Array.
        new_content = re.sub(r'\bArr\.', 'Array.', new_content)

        if new_content != content:
            with open(dest_file, 'w') as f:
                f.write(new_content)
            print("Updated Array.java content")

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

                # Replace import
                new_content = new_content.replace('import hara.lang.base.Arr;', 'import hara.lang.base.primitive.Array;')

                # Replace FQCN usage if any (unlikely to have FQCN for Arr)
                new_content = new_content.replace('hara.lang.base.Arr', 'hara.lang.base.primitive.Array')

                # Replace static usage Arr.
                new_content = re.sub(r'\bArr\.', 'Array.', new_content)

                # Replace class usage (e.g. implements Arr - unlikely)
                new_content = re.sub(r'\bArr\b', 'Array', new_content)

                # Add import if Array is used and not imported
                # Arr was in hara.lang.base. Files in hara.lang.base didn't import it.
                # Now Array is in hara.lang.base.primitive.
                # So files in hara.lang.base MUST import hara.lang.base.primitive.Array.
                if 'Array.' in new_content or 'Array ' in new_content or 'Array<' in new_content:
                     if 'import hara.lang.base.primitive.Array;' not in new_content and 'package hara.lang.base.primitive;' not in new_content:
                         pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
                         if pkg_match:
                             end = pkg_match.end()
                             new_content = new_content[:end] + '\n\nimport hara.lang.base.primitive.Array;' + new_content[end:]

                if new_content != content:
                    with open(filepath, 'w') as f:
                        f.write(new_content)
                    print(f"Updated references in {filepath}")

if __name__ == '__main__':
    refactor()
