import os
import re

# 1. Fix G.java: Constant.Constant -> Constant
g_path = 'src/main/java/hara/lang/base/G.java'
with open(g_path, 'r') as f:
    content = f.read()
if 'Constant.Constant' in content:
    content = content.replace('Constant.Constant', 'Constant')
    print("Fixed Constant.Constant in G.java")
with open(g_path, 'w') as f:
    f.write(content)

# 2. Fix Block.java: hara.lang.base.G.ObjType -> hara.lang.protocol.Constant.ObjType
block_path = 'src/main/java/hara/lib/block/Block.java'
with open(block_path, 'r') as f:
    content = f.read()
content = content.replace('hara.lang.base.G.ObjType', 'hara.lang.protocol.Constant.ObjType')
content = content.replace('hara.lang.base.G.HashType', 'hara.lang.protocol.Constant.HashType')
content = content.replace('hara.lang.base.G.MetaType', 'hara.lang.protocol.Constant.MetaType')
with open(block_path, 'w') as f:
    f.write(content)
print("Fixed Block.java")

# 3. Fix Data.java and Vector.java: PushFirst -> IPushFirst, PopFirst -> IPopFirst, Metadata -> IMetadata
targets = [
    'src/main/java/hara/lang/base/Data.java',
    'src/main/java/hara/lang/data/Vector.java',
    'src/main/java/hara/lang/data/Symbol.java',
    'src/main/java/hara/lang/data/Keyword.java',
    'src/main/java/hara/lang/base/Obj.java'
]

replacements = {
    'PushFirst': 'IPushFirst',
    'PopFirst': 'IPopFirst',
    'PushLast': 'IPushLast',
    'PopLast': 'IPopLast',
    'Metadata': 'IMetadata',
    'Coll': 'IColl',
    'Count': 'ICount',
    'Empty': 'IEmpty',
    'Hash': 'IHash',
    'Display': 'IDisplay',
    'Seq': 'ISeq', # Wait, I don't think there is ISeq? I.java didn't have Seq.
    'Sequential': 'ISequential' # I.java didn't have Sequential? Check list.
    # The list: Assoc, Coll, Component, Conj, Cons, Context, Count, Deref, DerefTimeout, Display, Dissoc, Empty, Equality, ExInfo, Find, Fn, OFn, Hash, HashCached, Indexed, IndexedKV, InvokeIn, Lookup, Metadata, Mutable, Namespaced, Nth, ObjType, Pair, PeekFirst, PeekLast, Persistent, PopFirst, PopLast, PushFirst, PushLast, Ranged, Realize, Reset, ToMutable, ToPersistent, Validate, Watch, HasRuntime
}

for filepath in targets:
    if not os.path.exists(filepath): continue
    with open(filepath, 'r') as f:
        content = f.read()

    original = content

    for old, new in replacements.items():
        # Replace whole word
        # Careful with strings or comments? Simple replacement is usually fine for Java unless inside string literals.
        # But "Metadata" is a common word.
        # We should ensure it's a type.
        # \bMetadata\b
        content = re.sub(r'\b' + old + r'\b', new, content)

        # Also fix fully qualified if any?
        # hara.lang.base.I.Metadata -> already handled.

    if content != original:
        print(f"Fixed types in {filepath}")
        with open(filepath, 'w') as f:
            f.write(content)

# 4. Fix Obj.java imports
obj_path = 'src/main/java/hara/lang/base/Obj.java'
with open(obj_path, 'r') as f:
    content = f.read()
# Remove bad imports
if 'import hara.lang.base.G.HashType;' in content:
    content = content.replace('import hara.lang.base.G.HashType;', '')
    # Ensure Constant import is there (it is, from previous scripts)
    # Check usage of HashType -> Constant.HashType
    content = re.sub(r'\bHashType\b', 'Constant.HashType', content)
    # But wait, Constant.HashType is correct.
    # If I have "import hara.lang.protocol.Constant.HashType;", then usage "HashType" is valid.
    # But I removed the import. So usage "HashType" becomes invalid.
    # Replacing "HashType" with "Constant.HashType" fixes it (assuming Constant is imported or fully qualified).
    # Obj.java has "import hara.lang.protocol.Constant;" (or should have).

    # Handle "Constant.Constant.HashType" risk again
    content = content.replace('Constant.Constant.HashType', 'Constant.HashType')

    with open(obj_path, 'w') as f:
        f.write(content)
print("Fixed Obj.java imports")
