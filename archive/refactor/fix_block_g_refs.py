import re

filepath = 'src/main/java/hara/lib/block/Block.java'
with open(filepath, 'r') as f:
    content = f.read()

# Replace G.ObjType with Constant.ObjType
content = re.sub(r'(?<![\w\.])G\.ObjType\b', 'Constant.ObjType', content)
content = re.sub(r'(?<![\w\.])G\.HashType\b', 'Constant.HashType', content)
content = re.sub(r'(?<![\w\.])G\.MetaType\b', 'Constant.MetaType', content)

# Also ensure Constant is imported
if 'import hara.lang.protocol.Constant;' not in content and 'import hara.lang.protocol.*;' not in content:
    content = content.replace('import hara.lang.base.G;', 'import hara.lang.base.G;\nimport hara.lang.protocol.Constant;')

with open(filepath, 'w') as f:
    f.write(content)
print("Fixed Block.java")
