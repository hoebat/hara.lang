import os
import re

root_dirs = ['src/main/java', 'src/test/java']

# List of interfaces moved
interfaces = [
    'IAssoc', 'IColl', 'IComponent', 'IConj', 'ICons', 'IContext', 'ICount', 'IDeref',
    'IDerefTimeout', 'IDisplay', 'IDissoc', 'IEmpty', 'IEquality', 'IExInfo', 'IFind',
    'IFn', 'IOFn', 'IHash', 'IHashCached', 'IIndexed', 'IIndexedKV', 'IInvokeIn',
    'ILookup', 'IMetadata', 'IMutable', 'INamespaced', 'INth', 'IObjType', 'IPair',
    'IPeekFirst', 'IPeekLast', 'IPersistent', 'IPopFirst', 'IPopLast', 'IPushFirst',
    'IPushLast', 'IRanged', 'IRealize', 'IReset', 'IToMutable', 'IToPersistent',
    'IValidate', 'IWatch', 'IHasRuntime', 'Constant'
]

def needs_import(content):
    # Check if any interface is used
    for i in interfaces:
        # Check for whole word match
        if re.search(r'\b' + i + r'\b', content):
            return True
    return False

def add_import(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    if 'package hara.lang.protocol;' in content:
        return # Don't add import to the package itself (though it wouldn't hurt)

    if 'import hara.lang.protocol.*;' in content:
        return # Already there

    if not needs_import(content):
        return

    # Add import
    print(f"Adding import to {filepath}")

    # Logic to insert import
    if 'import ' in content:
        # Find last import
        imports = list(re.finditer(r'^import .*?;', content, re.MULTILINE))
        if imports:
            last_import = imports[-1]
            end_pos = last_import.end()
            content = content[:end_pos] + '\nimport hara.lang.protocol.*;' + content[end_pos:]
        else:
             # Should not happen if 'import ' is in content, but maybe commented out?
             # Fallback to after package
             package_match = re.search(r'package .*?;', content)
             if package_match:
                 end_pos = package_match.end()
                 content = content[:end_pos] + '\n\nimport hara.lang.protocol.*;' + content[end_pos:]
    else:
        # No imports, add after package
        package_match = re.search(r'package .*?;', content)
        if package_match:
            end_pos = package_match.end()
            content = content[:end_pos] + '\n\nimport hara.lang.protocol.*;' + content[end_pos:]

    with open(filepath, 'w') as f:
        f.write(content)

for root_dir in root_dirs:
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.java'):
                add_import(os.path.join(root, file))
