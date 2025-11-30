import os
import re

def refactor():
    files = [
        'src/main/java/hara/lang/data/Queue.java',
        'src/main/java/hara/lang/data/SortedSet.java',
        'src/main/java/hara/lang/data/Vector.java',
        'src/main/java/hara/lang/data/SortedMap.java',
        'src/main/java/hara/lang/data/Trie.java',
        'src/main/java/hara/data/types/INamespacedType.java'
    ]

    for filepath in files:
        if not os.path.exists(filepath):
            continue

        with open(filepath, 'r') as f:
            content = f.read()

        new_content = content

        # Replace IRefType.ObjPersistent -> ObjPersistent
        new_content = new_content.replace('IRefType.ObjPersistent', 'ObjPersistent')
        new_content = new_content.replace('IRefType.ObjMutable', 'ObjMutable')
        new_content = new_content.replace('IRefType.MT', 'ObjMutable')

        # Ensure imports
        if 'ObjPersistent' in new_content and 'import hara.lang.data.types.ObjPersistent;' not in new_content:
             pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
             if pkg_match:
                 end = pkg_match.end()
                 new_content = new_content[:end] + '\n\nimport hara.lang.data.types.ObjPersistent;' + new_content[end:]

        if 'ObjMutable' in new_content and 'import hara.lang.data.types.ObjMutable;' not in new_content:
             if 'import hara.lang.data.types.ObjMutable;' not in new_content:
                 pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
                 if pkg_match:
                     end = pkg_match.end()
                     new_content = new_content[:end] + '\n\nimport hara.lang.data.types.ObjMutable;' + new_content[end:]

        if new_content != content:
            with open(filepath, 'w') as f:
                f.write(new_content)
            print(f"Updated {filepath}")

if __name__ == '__main__':
    refactor()
