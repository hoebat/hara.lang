import re

filepath = 'src/main/java/hara/lang/base/G.java'
with open(filepath, 'r') as f:
    content = f.read()

# Add import hara.lang.protocol.Constant; if missing
if 'import hara.lang.protocol.Constant;' not in content and 'import hara.lang.protocol.*;' not in content:
    # Find package declaration
    package_end = content.find(';')
    if package_end != -1:
        content = content[:package_end+1] + '\n\nimport hara.lang.protocol.Constant;' + content[package_end+1:]

with open(filepath, 'w') as f:
    f.write(content)
print("Fixed G.java")
