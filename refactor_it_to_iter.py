import os
import re

def refactor():
    paths = ['src/main/java', 'src/test/java']

    # Rename file
    old_path = 'src/main/java/hara/lang/base/It.java'
    new_path = 'src/main/java/hara/lang/base/Iter.java'
    if os.path.exists(old_path):
        os.rename(old_path, new_path)
        print(f"Renamed {old_path} to {new_path}")

    # Rename test file if exists
    old_test = 'src/test/java/hara/lang/base/ItTest.java'
    new_test = 'src/test/java/hara/lang/base/IterTest.java'
    if os.path.exists(old_test):
        os.rename(old_test, new_test)
        print(f"Renamed {old_test} to {new_test}")

    for root_path in paths:
        for dirpath, dirnames, filenames in os.walk(root_path):
            for filename in filenames:
                if filename.endswith('.java'):
                    filepath = os.path.join(dirpath, filename)
                    with open(filepath, 'r') as f:
                        content = f.read()

                    new_content = content

                    # 1. Replace FQCN (safer)
                    new_content = new_content.replace('hara.lang.base.It', 'hara.lang.base.Iter')

                    # 2. Replace It. (static access)
                    new_content = re.sub(r'\bIt\.', 'Iter.', new_content)

                    # 3. Replace class/interface declaration
                    new_content = re.sub(r'interface It\b', 'interface Iter', new_content)
                    new_content = re.sub(r'class It\b', 'class Iter', new_content)

                    # 4. Replace usages as type or in casts, generics or simple usage
                    # Note: \bIt\b matches "It" word.
                    # We accept that some comments might be changed.

                    new_content = re.sub(r'\bIt\b', 'Iter', new_content)

                    if new_content != content:
                        with open(filepath, 'w') as f:
                            f.write(new_content)
                        print(f"Updated {filepath}")

if __name__ == '__main__':
    refactor()
