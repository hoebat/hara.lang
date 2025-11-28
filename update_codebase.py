import os
import re

root_dirs = ['src/main/java', 'src/test/java']

# List of interfaces moved from I.<Name> to I<Name>
interfaces = [
    'Assoc', 'Coll', 'Component', 'Conj', 'Cons', 'Context', 'Count', 'Deref',
    'DerefTimeout', 'Display', 'Dissoc', 'Empty', 'Equality', 'ExInfo', 'Find',
    'Fn', 'OFn', 'Hash', 'HashCached', 'Indexed', 'IndexedKV', 'InvokeIn',
    'Lookup', 'Metadata', 'Mutable', 'Namespaced', 'Nth', 'ObjType', 'Pair',
    'PeekFirst', 'PeekLast', 'Persistent', 'PopFirst', 'PopLast', 'PushFirst',
    'PushLast', 'Ranged', 'Realize', 'Reset', 'ToMutable', 'ToPersistent',
    'Validate', 'Watch', 'HasRuntime'
]

# Map I.Name -> IName
interface_replacements = {name: 'I' + name for name in interfaces}

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    original_content = content

    # 1. Imports
    # If "import hara.lang.base.I;" -> replace with "import hara.lang.protocol.*;"
    if 'import hara.lang.base.I;' in content:
        content = content.replace('import hara.lang.base.I;', 'import hara.lang.protocol.*;')

    # If "import hara.lang.base.I.Name;" -> "import hara.lang.protocol.IName;"
    content = re.sub(r'import hara\.lang\.base\.I\.(\w+);', r'import hara.lang.protocol.I\1;', content)

    # Add Constant import if G is used for constants or if we introduce Constant usage
    # We will check if we introduce Constant usage later or just add it if G was imported?
    # Safer to just add it if we do replacements.

    # 2. Interface References
    # "I.Name" -> "IName"
    # We need to be careful. "I.Name" matches literal string.
    for name, new_name in interface_replacements.items():
        # Replace "hara.lang.base.I.Name" -> "hara.lang.protocol.IName"
        content = content.replace(f'hara.lang.base.I.{name}', f'hara.lang.protocol.{new_name}')

        # Replace "I.Name" -> "IName"
        # Use regex to ensure word boundary on right, and check if "I." is preceded by nothing or non-dot char?
        # Actually "I.Name" is specific enough usually.
        content = re.sub(r'(?<![\w\.])I\.' + name + r'\b', new_name, content)

        # Replace "Name" -> "IName" IF it was imported statically or previously imported as I.Name?
        # This is hard. If  became ,
        # then usage of  in code must become .
        # So we look for the name as a whole word.
        # But only if it's not a variable name etc.
        # This assumes code followed the convention of using the interface name type.
        # Given "I.Coll", users likely used "I.Coll" or "Coll" (if imported).
        # We'll replace "Name" with "IName" ONLY if we see evidence it was an interface type.
        # E.g. "implements Coll", "Coll c", "extends Coll".
        # Regex:  -> ?
        # Risky for common words like "Count", "Empty", "Find", "Hash".
        # "Hash" is very common. "Map<...>"
        # Let's check imports.
        # If the file had , then  means the interface.
        # We already replaced the import line.
        # So if we replaced , we should safe-list "Coll" for replacement in this file.

    # 3. Constants
    # hara.lang.base.G.T -> hara.lang.protocol.Constant.T
    content = content.replace('hara.lang.base.G.T', 'hara.lang.protocol.Constant.T')
    content = content.replace('hara.lang.base.G.F', 'hara.lang.protocol.Constant.F')
    content = content.replace('hara.lang.base.G.EMPTY_ARRAY', 'hara.lang.protocol.Constant.EMPTY_ARRAY')

    # G.T -> Constant.T
    content = re.sub(r'(?<![\w\.])G\.T\b', 'Constant.T', content)
    content = re.sub(r'(?<![\w\.])G\.F\b', 'Constant.F', content)
    content = re.sub(r'(?<![\w\.])G\.EMPTY_ARRAY\b', 'Constant.EMPTY_ARRAY', content)

    # Enums
    # G.MetaType -> Constant.MetaType
    content = re.sub(r'(?<![\w\.])G\.MetaType\b', 'Constant.MetaType', content)
    content = re.sub(r'(?<![\w\.])G\.HashType\b', 'Constant.HashType', content)
    content = re.sub(r'(?<![\w\.])G\.ObjType\b', 'Constant.ObjType', content) # Also handles G.ObjType.VALUE

    # Also handle static imports of G?
    # "import static hara.lang.base.G.*;" -> "import static hara.lang.protocol.Constant.*;"
    if 'import static hara.lang.base.G.*;' in content:
        content = content.replace('import static hara.lang.base.G.*;', 'import static hara.lang.protocol.Constant.*;')

    # If we introduced "Constant.", we need to import it.
    if 'Constant.' in content and 'import hara.lang.protocol.Constant;' not in content and 'package hara.lang.protocol;' not in content:
         # Check if we already have hara.lang.protocol.*
         if 'import hara.lang.protocol.*;' not in content:
             # Find last import and append
             if 'import ' in content:
                 last_import = content.rfind('import ')
                 end_of_line = content.find('\n', last_import)
                 content = content[:end_of_line+1] + 'import hara.lang.protocol.Constant;\n' + content[end_of_line+1:]
             else:
                 # No imports? Add after package
                 package_end = content.find(';')
                 if package_end != -1:
                     content = content[:package_end+1] + '\n\nimport hara.lang.protocol.Constant;' + content[package_end+1:]

    # Handle "implements I" -> "implements ?"
    # "I" itself is empty/gone.
    # If something implemented "I", it probably meant the container or it was a typo?
    # Or "implements I.Coll". "I.Coll" -> "IColl".

    if content != original_content:
        print(f"Updating {filepath}")
        with open(filepath, 'w') as f:
            f.write(content)

for root_dir in root_dirs:
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.java'):
                process_file(os.path.join(root, file))
