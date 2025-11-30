import os
import re

SRC_DIR = 'src/main/java'
PKG_PATH = 'hara/lang/base/primitive'
NUM_FILE = os.path.join(SRC_DIR, PKG_PATH, 'Num.java')
MAX_FILE = os.path.join(SRC_DIR, PKG_PATH, 'Max.java')

def refactor_max():
    with open(NUM_FILE, 'r') as f:
        content = f.read()

    # Regex to find Max interface block
    # This is a bit complex with regex because of nested braces.
    # I'll find "public interface Max" and then count braces.

    start_pattern = r'public\s+interface\s+Max\s+\{'
    match = re.search(start_pattern, content)
    if not match:
        print("Max interface not found in Num.java")
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

    max_content = content[start_index:end_index]

    # Process max_content
    # 1. Remove outer wrapper? No, extract it.
    # 2. Add Num. prefixes to isNaN and gt calls.
    # 3. Add package and imports.

    # Extract body of interface (remove "public interface Max { " and " }")
    # Actually, we want to keep it as an interface definition in the new file.

    new_max_content = max_content

    # Replace calls to Num methods
    # Num.isNaN, Num.gt, Num.lt
    # We need to be careful not to replace definition (unlikely in interface)
    # or Math.max

    # Simple replaces for function calls
    new_max_content = re.sub(r'\bisNaN\(', 'Num.isNaN(', new_max_content)
    new_max_content = re.sub(r'\bgt\(', 'Num.gt(', new_max_content)
    # lt is not used in Max but good measure
    new_max_content = re.sub(r'\blt\(', 'Num.lt(', new_max_content)

    # If there are any other static methods from Num used?
    # From previous read, it was isNaN and gt.

    # Create Max.java
    imports = []
    # Read imports from Num.java
    with open(NUM_FILE, 'r') as f:
        for line in f:
            if line.strip().startswith('import '):
                imports.append(line.strip())

    # Add imports required by Num if they are not already there?
    # Max uses Object, double, long which are default.
    # It calls Num.isNaN -> Num is in same package.
    # It calls Math.max -> java.lang.Math.
    # It casts to Number -> java.lang.Number.
    # It might use BigDecimal? No visible usage in the snippet.

    with open(MAX_FILE, 'w') as f:
        f.write(f"package hara.lang.base.primitive;\n\n")
        for imp in imports:
            f.write(f"{imp}\n")
        f.write("\n")
        f.write(new_max_content)
        f.write("\n")

    print(f"Created {MAX_FILE}")

    # Remove Max from Num.java
    # We remove the block [start_index:end_index]
    # Also clean up empty lines around it if any

    new_num_content = content[:start_index] + content[end_index:]

    with open(NUM_FILE, 'w') as f:
        f.write(new_num_content)

    print(f"Updated {NUM_FILE}")

    # Update references
    # Since grep showed no Num.Max or .Max references (outside of definition presumably?),
    # maybe it's not used explicitly?
    # Wait, grep results were "No match found".
    # Maybe I should search for "Max.max".

    # Walk through files to replace Num.Max with Max if needed.
    # But since grep failed, maybe there are no external references?
    # Or maybe grep failed because I didn't escape dot properly?
    # I did `grep Num\.Max`.

    pass

if __name__ == '__main__':
    refactor_max()
