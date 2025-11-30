import os
import re

SRC_DIR = 'src/main/java'
BASE_PKG = 'hara.lang.base'
PRIMITIVE_PKG = 'hara.lang.base.primitive'
UT_FILE = os.path.join(SRC_DIR, 'hara/lang/base/Ut.java')
PRIMITIVE_DIR = os.path.join(SRC_DIR, 'hara/lang/base/primitive')

INNER_CLASSES = ['Clock', 'Counter', 'Delay', 'Flag', 'Murmur3', 'RefCache', 'SipHash', 'Volatile']

def extract_classes(content):
    classes = {}
    lines = content.splitlines()
    imports = []

    # Extract imports
    for line in lines:
        if line.strip().startswith('import '):
            imports.append(line.strip())

    # Simple parser to extract classes
    current_class = None
    buffer = []
    brace_count = 0
    in_class = False

    # Regex to detect start of inner class
    class_start_re = re.compile(r'^\s*(public\s+)?(static\s+)?(final\s+)?(class|interface)\s+(\w+)')

    for line in lines:
        if not in_class:
            m = class_start_re.match(line)
            if m and m.group(5) in INNER_CLASSES:
                in_class = True
                current_class = m.group(5)
                # Remove 'static' and ensure 'public'
                decl = line.replace('static ', '').strip()
                if 'public' not in decl:
                    decl = 'public ' + decl
                buffer = [decl]
                brace_count = line.count('{') - line.count('}')
            else:
                continue
        else:
            buffer.append(line)
            brace_count += line.count('{') - line.count('}')
            if brace_count == 0:
                # End of class
                classes[current_class] = '\n'.join(buffer)
                current_class = None
                in_class = False
                buffer = []

    return imports, classes

def write_classes(imports, classes):
    if not os.path.exists(PRIMITIVE_DIR):
        os.makedirs(PRIMITIVE_DIR)

    for name, content in classes.items():
        filepath = os.path.join(PRIMITIVE_DIR, f"{name}.java")
        with open(filepath, 'w') as f:
            f.write(f"package {PRIMITIVE_PKG};\n\n")
            f.write(f"import {BASE_PKG}.*;\n") # Add wildcard import for base package classes (Ex, G, etc)
            for imp in imports:
                f.write(f"{imp}\n")
            f.write("\n")
            f.write(content)
            f.write("\n")
        print(f"Created {filepath}")

def update_references():
    for root, dirs, files in os.walk('src'):
        for file in files:
            if not file.endswith('.java'):
                continue

            filepath = os.path.join(root, file)
            # Skip the file we are about to delete if it still exists (though we read it into memory first)
            if filepath == UT_FILE:
                continue

            with open(filepath, 'r') as f:
                content = f.read()

            original_content = content

            # Replace imports
            # import hara.lang.base.Ut; -> remove or replace?
            # import hara.lang.base.Ut.*; -> import hara.lang.base.primitive.*;

            if 'import hara.lang.base.Ut.*;' in content:
                content = content.replace('import hara.lang.base.Ut.*;', f'import {PRIMITIVE_PKG}.*;')

            if 'import hara.lang.base.Ut;' in content:
                # If specifically importing Ut, we might need to import the primitive classes if they are used by simple name.
                # Ideally, if we replace usages with FQN or add imports.
                # Replacing with nothing might break things if Ut.Clock was used.
                # But we will replace Ut.Clock with primitive.Clock (FQN) below?
                # If I replace `Ut.Clock` with `hara.lang.base.primitive.Clock`, I don't need `import Ut`.
                content = content.replace('import hara.lang.base.Ut;', '')

            # Replace usages
            for cls in INNER_CLASSES:
                # Replace fully qualified usage
                content = content.replace(f'hara.lang.base.Ut.{cls}', f'{PRIMITIVE_PKG}.{cls}')
                # Replace usage via Ut
                # We need to be careful not to replace things like `Ut.something` if `something` is not in INNER_CLASSES, but Ut only has these.
                # Use regex to match `Ut.Clock` but not `AnyUt.Clock` (word boundary)

                # Regex for `Ut.<Class>`
                # We need to handle `Ut.Clock` where Ut is either the simple name or part of FQN (already handled above)
                # Since we already handled FQN `hara.lang.base.Ut.Clock`, `Ut.Clock` remaining must be the simple name reference.

                pattern = r'\bUt\.' + cls + r'\b'
                content = re.sub(pattern, f'{PRIMITIVE_PKG}.{cls}', content)

                # Also handle static imports? `import static hara.lang.base.Ut.*;` -> `import static hara.lang.base.primitive.*;` ?
                # Ut is an interface, so it might be used that way?
                # The file check showed `import hara.lang.base.Ut.*;` in test.

            if content != original_content:
                with open(filepath, 'w') as f:
                    f.write(content)
                print(f"Updated {filepath}")

def main():
    with open(UT_FILE, 'r') as f:
        content = f.read()

    imports, classes = extract_classes(content)

    if len(classes) != len(INNER_CLASSES):
        print(f"Warning: Expected {len(INNER_CLASSES)} classes, found {len(classes)}")
        print(f"Found: {list(classes.keys())}")
        # Identify missing
        missing = set(INNER_CLASSES) - set(classes.keys())
        print(f"Missing: {missing}")
        # Proceed only if we found something?
        if not classes:
            return

    write_classes(imports, classes)

    # Delete Ut.java
    os.remove(UT_FILE)
    print(f"Deleted {UT_FILE}")

    update_references()

if __name__ == '__main__':
    main()
