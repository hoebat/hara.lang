import os
import re

# Fix usage of OFn -> IOFn
# Scan all java files
for root, dirs, files in os.walk('src/main/java'):
    for file in files:
        if file.endswith('.java'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                content = f.read()

            new_content = re.sub(r'(?<![\w])OFn\b', 'IOFn', content)

            if new_content != content:
                print(f"Replacing OFn -> IOFn in {filepath}")
                with open(filepath, 'w') as f:
                    f.write(new_content)
