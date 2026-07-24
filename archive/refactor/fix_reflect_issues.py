import os
import re

def fix_reflect_constructors():
    filepath = 'src/main/java/hara/lang/base/primitive/Reflect.java'
    with open(filepath, 'r') as f:
        content = f.read()

    # Make constructors public
    # Pattern: ConstructorName(Args) {
    # We look for lines starting with ConstructorName or indented
    # Construct regex
    class_names = ['InstanceField', 'InstanceMethod', 'StaticConstructor', 'StaticField', 'StaticMethod']

    new_content = content
    for name in class_names:
        # Regex to find package-private constructor and add public
        # Matches "  Name(" or "Name("
        # Be careful not to match "public Name("
        # We replace "  Name(" with "  public Name("

        # Regex: (^\s*)(Name\s*\() -> \1public \2
        pattern = r'(^\s*)(' + name + r'\s*\()'
        new_content = re.sub(pattern, r'\1public \2', new_content, flags=re.MULTILINE)

    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Updated {filepath} (constructors public)")

def fix_builtin_macro():
    files = ['src/main/java/hara/kernel/base/Builtin.java', 'src/main/java/hara/kernel/base/Macro.java']

    for filepath in files:
        if not os.path.exists(filepath):
            continue

        with open(filepath, 'r') as f:
            lines = f.readlines()

        new_lines = []
        for line in lines:
            if 'import hara.lang.base.primitive.Reflect;' in line:
                continue # Remove import

            # Replace new Reflect.X with new hara.lang.base.primitive.Reflect.X
            if 'new Reflect.' in line:
                line = line.replace('new Reflect.', 'new hara.lang.base.primitive.Reflect.')

            new_lines.append(line)

        with open(filepath, 'w') as f:
            f.writelines(new_lines)
        print(f"Updated {filepath} (imports and usage)")

if __name__ == '__main__':
    fix_reflect_constructors()
    fix_builtin_macro()
