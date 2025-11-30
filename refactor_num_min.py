import os
import re

SRC_DIR = 'src/main/java'
PKG_PATH = 'hara/lang/base/primitive'
NUM_FILE = os.path.join(SRC_DIR, PKG_PATH, 'Num.java')
MIN_FILE = os.path.join(SRC_DIR, PKG_PATH, 'Min.java')

def refactor_min():
    with open(NUM_FILE, 'r') as f:
        content = f.read()

    # Regex to find Min interface block
    start_pattern = r'public\s+interface\s+Min\s+\{'
    match = re.search(start_pattern, content)
    if not match:
        print("Min interface not found in Num.java")
        return

    start_index = match.start()
    open_brace_index = match.end() - 1 # '{' position

    brace_count = 1
    i = open_brace_index + 1
    while i < len(content) and brace_count > 0:
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
        i += 1

    end_index = i

    min_content = content[start_index:end_index]

    # Process min_content
    new_min_content = min_content

    # Replace calls to Num methods
    # Min uses isNaN, lt (not gt presumably)
    # Be careful with Double.isNaN

    # Lookbehind to ensure not preceded by .
    new_min_content = re.sub(r'(?<!\.)\bisNaN\(', 'Num.isNaN(', new_min_content)
    new_min_content = re.sub(r'(?<!\.)\bgt\(', 'Num.gt(', new_min_content)
    new_min_content = re.sub(r'(?<!\.)\blt\(', 'Num.lt(', new_min_content)

    # Create Min.java
    imports = []
    with open(NUM_FILE, 'r') as f:
        for line in f:
            if line.strip().startswith('import '):
                imports.append(line.strip())

    with open(MIN_FILE, 'w') as f:
        f.write(f"package hara.lang.base.primitive;\n\n")
        for imp in imports:
            f.write(f"{imp}\n")
        f.write("\n")
        f.write(new_min_content)
        f.write("\n")

    print(f"Created {MIN_FILE}")

    # Remove Min from Num.java
    new_num_content = content[:start_index] + content[end_index:]

    with open(NUM_FILE, 'w') as f:
        f.write(new_num_content)

    print(f"Updated {NUM_FILE}")

if __name__ == '__main__':
    refactor_min()
