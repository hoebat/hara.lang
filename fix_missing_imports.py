import os
import re

def fix_imports():
    targets = {
        'Ratio': 'hara.lang.base.primitive.Ratio',
        'Bits': 'hara.lang.base.primitive.Bits',
        'Box': 'hara.lang.base.primitive.Box',
        'Cast': 'hara.lang.base.primitive.Cast',
        'Complex': 'hara.lang.base.primitive.Complex',
        'Array': 'hara.lang.base.primitive.Array',
        'Num': 'hara.lang.base.primitive.Num'
    }

    for root, dirs, files in os.walk('src'):
        for file in files:
            if file.endswith('.java'):
                filepath = os.path.join(root, file)
                with open(filepath, 'r') as f:
                    content = f.read()

                new_content = content

                for name, fqn in targets.items():
                    # Simple heuristic: if name is used as a class (e.g. " Name " or "Name." or "Name<")
                    # and not imported, and not defined in file (heuristic), and not in same package.

                    # Regex for usage: \bName\b
                    if re.search(r'\b' + name + r'\b', new_content):
                        # Check if already imported
                        if f'import {fqn};' in new_content: continue
                        if f'import static {fqn}' in new_content: continue

                        # Check package
                        pkg_name = fqn.rsplit('.', 1)[0]
                        if f'package {pkg_name};' in new_content: continue

                        # Add import
                        pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
                        if pkg_match:
                            end = pkg_match.end()
                            new_content = new_content[:end] + '\n\nimport ' + fqn + ';' + new_content[end:]
                            print(f"Added import {fqn} to {filepath}")

                if new_content != content:
                    with open(filepath, 'w') as f:
                        f.write(new_content)

if __name__ == '__main__':
    fix_imports()
