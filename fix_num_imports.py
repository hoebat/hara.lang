import os

def fix_num_imports():
    filepath = 'src/main/java/hara/lang/base/primitive/Num.java'
    if not os.path.exists(filepath):
        print(f"{filepath} not found")
        return

    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content

    imports_to_add = [
        'import hara.lang.base.NumUtils;',
        'import hara.lang.base.NumOps;'
    ]

    # Insert after package decl
    if 'package hara.lang.base.primitive;' in new_content:
        for imp in imports_to_add:
            if imp not in new_content:
                new_content = new_content.replace('package hara.lang.base.primitive;', 'package hara.lang.base.primitive;\n\n' + imp)

    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Updated imports in {filepath}")

if __name__ == '__main__':
    fix_num_imports()
