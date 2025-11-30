import os

MAX_FILE = 'src/main/java/hara/lang/base/primitive/Max.java'

def fix_max():
    with open(MAX_FILE, 'r') as f:
        content = f.read()

    # Fix incorrect replacement
    content = content.replace('Double.Num.isNaN', 'Double.isNaN')

    with open(MAX_FILE, 'w') as f:
        f.write(content)

    print(f"Fixed {MAX_FILE}")

if __name__ == '__main__':
    fix_max()
