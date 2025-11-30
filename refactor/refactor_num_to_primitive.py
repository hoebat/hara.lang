import os
import shutil
import re

def refactor():
    src_dir = 'src/main/java/hara/lang/base'
    dest_dir = 'src/main/java/hara/lang/base/primitive'

    filename = 'Num.java'
    src_file = os.path.join(src_dir, filename)
    dest_file = os.path.join(dest_dir, filename)

    # Move file
    if os.path.exists(src_file):
        shutil.move(src_file, dest_file)
        print(f"Moved {src_file} to {dest_file}")

    # Update content of Num.java
    if os.path.exists(dest_file):
        with open(dest_file, 'r') as f:
            content = f.read()

        new_content = content.replace('package hara.lang.base;', 'package hara.lang.base.primitive;')
        # Remove import hara.lang.base.primitive.Ratio; if present (redundant)
        new_content = new_content.replace('import hara.lang.base.primitive.Ratio;', '')

        if new_content != content:
            with open(dest_file, 'w') as f:
                f.write(new_content)
            print("Updated Num.java package")

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
                new_content = new_content.replace('import hara.lang.base.Num;', 'import hara.lang.base.primitive.Num;')

                # Replace FQCN usage
                new_content = new_content.replace('hara.lang.base.Num', 'hara.lang.base.primitive.Num')

                # Add import if Num is used (and not imported) - unlikely as it was in base
                # Files in hara.lang.base (like NumOps) will need import hara.lang.base.primitive.Num
                if 'Num.' in new_content or 'Num ' in new_content:
                     if 'import hara.lang.base.primitive.Num;' not in new_content and 'package hara.lang.base.primitive;' not in new_content:
                         pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
                         if pkg_match:
                             end = pkg_match.end()
                             new_content = new_content[:end] + '\n\nimport hara.lang.base.primitive.Num;' + new_content[end:]

                if new_content != content:
                    with open(filepath, 'w') as f:
                        f.write(new_content)
                    print(f"Updated references in {filepath}")

if __name__ == '__main__':
    refactor()
