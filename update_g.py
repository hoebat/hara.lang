import re

source_file = 'src/main/java/hara/lang/base/G.java'

with open(source_file, 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    stripped = line.strip()
    # Skip moved constants
    if 'public static final Object[] EMPTY_ARRAY' in line: continue
    if 'public static final Boolean F' in line: continue
    if 'public static final Boolean T' in line: continue
    if 'public enum MetaType' in line: continue
    if 'public enum HashType' in line: continue
    if 'public enum ObjType' in line: continue

    # Update I.Display -> IDisplay (and other I. references if they appear here)
    # G.java uses I.Display, I.Hash, I.Hash.hashGet, etc.
    # It imports hara.lang.base.I? No, it's in the same package.
    # So it uses I.Display.
    # We need to change I.Display -> hara.lang.protocol.IDisplay
    # Or add import hara.lang.protocol.*; and change I.Display -> IDisplay.

    # Let's handle imports first.
    if line.startswith('package hara.lang.base;'):
        new_lines.append(line)
        new_lines.append('\nimport hara.lang.protocol.*;\n')
        new_lines.append('import hara.lang.protocol.Constant.*;\n') # For static constants if we want to use them directly?
        # But G.java used them as its own members.
        # Now G.java (and others) will use Constant.T etc.
        # Wait, if I remove T from G, code INSIDE G using T will fail unless I import it or use Constant.T.
        continue

    # Replacements inside G.java
    line = re.sub(r'I\.Display', 'IDisplay', line)
    line = re.sub(r'I\.Hash', 'IHash', line)
    line = re.sub(r'G\.HashType', 'Constant.HashType', line) # It was referring to itself via G.
    line = re.sub(r'HashType', 'Constant.HashType', line) # If just HashType used
    # But wait, public static final HashType DEFAULT_HASH = HashType.MURMUR3;
    # HashType moved to Constant.
    # So Constant.HashType DEFAULT_HASH = Constant.HashType.MURMUR3;

    # Also fix usages of T, F, EMPTY_ARRAY inside G if any?
    # G.java doesn't seem to use T/F internally in the snippet I saw.

    new_lines.append(line)

with open(source_file, 'w') as f:
    f.writelines(new_lines)
