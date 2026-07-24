import os
import re

protocol_dir = 'src/main/java/hara/lang/protocol'
files = [os.path.join(protocol_dir, f) for f in os.listdir(protocol_dir) if f.endswith('.java')]

for filepath in files:
    with open(filepath, 'r') as f:
        content = f.read()

    # Fix "Constant.IObjType" -> "Constant.ObjType"
    # The split script might have blindly replaced "ObjType" with "IObjType" (interface rename)
    # AND "G.ObjType" with "Constant.ObjType".
    # But if "G.ObjType" became "Constant.ObjType", and then "ObjType" -> "IObjType",
    # it might have become "Constant.IObjType".

    new_content = content.replace('Constant.IObjType', 'Constant.ObjType')

    # Fix "I.IMetadata" -> "IMetadata"
    # "I.Metadata" -> "IMetadata".
    # My script did "I.Metadata" -> "IMetadata".
    # And "Metadata" -> "IMetadata".
    # So "I.Metadata" -> "I.IMetadata" if I replaced "Metadata" first?
    # Or "I.IMetadata" if I replaced "Metadata" -> "IMetadata" inside "I.Metadata"?

    new_content = new_content.replace('I.IMetadata', 'IMetadata')
    new_content = new_content.replace('I.Metadata', 'IMetadata') # Just in case

    # Check for other double prefixes
    new_content = new_content.replace('hara.lang.protocol.I.IMetadata', 'hara.lang.protocol.IMetadata')

    if new_content != content:
        print(f"Fixing bad replacements in {filepath}")
        with open(filepath, 'w') as f:
            f.write(new_content)
