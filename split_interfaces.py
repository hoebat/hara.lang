import re
import os

source_file = 'src/main/java/hara/lang/base/I.java'
output_dir = 'src/main/java/hara/lang/protocol'
package_decl = 'package hara.lang.protocol;\n\n'

with open(source_file, 'r') as f:
    content = f.read()

# Extract imports
imports = re.findall(r'import .*?;', content)
imports_block = '\n'.join(imports) + '\n\n'
imports_block += 'import hara.lang.base.*;\n'
imports_block += 'import hara.lang.base.Ex;\n'
imports_block += 'import hara.lang.base.Std;\n'
imports_block += 'import hara.lang.base.Data;\n'
imports_block += 'import hara.lang.base.Arr;\n'
imports_block += 'import hara.lang.base.It;\n'
imports_block += 'import hara.lang.base.Str;\n'
imports_block += 'import hara.lang.base.G;\n'

# Find all interface names to build the map for internal reference updates
interface_names = re.findall(r'public interface (\w+)', content)
# Filter out I itself if it matched (it matches "public interface I")
if 'I' in interface_names:
    interface_names.remove('I')

name_map = {name: 'I' + name for name in interface_names}

replacements = [
    (r'G\.EMPTY_ARRAY', 'Constant.EMPTY_ARRAY'),
    (r'G\.F', 'Constant.F'),
    (r'G\.T', 'Constant.T'),
    (r'G\.MetaType', 'Constant.MetaType'),
    (r'G\.HashType', 'Constant.HashType'),
    (r'G\.ObjType', 'Constant.ObjType'),
    # Also handle static imports or direct usage if any (I.java uses G.Enum fully qualified usually)
]

def process_body(body, current_interface_name):
    # Apply constant replacements
    for pattern, repl in replacements:
        body = re.sub(pattern, repl, body)

    # Apply interface renames
    for name, new_name in name_map.items():
        if name == current_interface_name:
            continue # Don't rename the interface inside itself if it refers to itself?
            # Actually, we SHOULD rename recursive references e.g. "Cons<E> cons(E e)" -> "ICons<E> cons(E e)"
            # Wait, "public interface Cons ... { Cons cons(...) }"
            # If I rename "Cons" to "ICons", the return type should be "ICons".
            # So yes, rename everything.
            pass

        # Regex to match whole word
        # We need to be careful not to match suffixes
        body = re.sub(r'\b' + name + r'\b', new_name, body)
        body = re.sub(r'I\.' + name + r'\b', new_name, body)

    return body

def extract_interfaces(text):
    pos = 0
    # Find "public interface I {" start to skip it?
    # No, the logic searches for "public interface X".
    # I.java starts with "public interface I {".
    # Inside it are other interfaces.
    # We want to skip the outer "I" definition and look for inner ones.

    # Let's just find all matches
    matches = list(re.finditer(r'public interface (\w+)', text))

    for match in matches:
        name = match.group(1)
        if name == 'I': continue # Skip the container

        start = match.start()

        # Find opening brace
        brace_start = text.find('{', start)
        if brace_start == -1: continue

        # Find matching closing brace
        brace_count = 1
        curr = brace_start + 1
        while brace_count > 0 and curr < len(text):
            if text[curr] == '{':
                brace_count += 1
            elif text[curr] == '}':
                brace_count -= 1
            curr += 1

        end = curr
        full_block = text[start:end]

        # Now process this block

        # 1. Rename the definition line: "public interface Name" -> "public interface IName"
        # We search from start of block
        def_brace_idx = full_block.find('{')
        def_part = full_block[:def_brace_idx]
        body_part = full_block[def_brace_idx:]

        # Rename extended interfaces in def_part
        for n, nn in name_map.items():
            if n == name: continue
            def_part = re.sub(r'\b' + n + r'\b', nn, def_part)
            def_part = re.sub(r'I\.' + n + r'\b', nn, def_part)

        # Rename the interface itself in definition
        def_part = re.sub(r'public interface ' + name + r'\b', 'public interface I' + name, def_part)

        # 2. Process body
        # We need to rename references to 'name' (itself) and other interfaces.
        # e.g. inside Cons: "Cons<E> cons(E e)" -> "ICons<E> cons(E e)"

        # Also need to handle "WatchEntry" inside Watch.
        # process_body does general replacement.

        # Special case: The interface name itself inside the body.
        # If I replace 'Name' with 'IName' everywhere, it works.

        processed_body = process_body(body_part, name)

        # One catch: if we have "public class WatchEntry ... extends ... Watch<...>"
        # "Watch" becomes "IWatch". Correct.

        # Apply the self-rename separately or just rely on the loop in process_body?
        # The loop in process_body iterates all names including 'name'.
        # So it should handle it.

        new_content = package_decl + imports_block + def_part + processed_body

        filepath = os.path.join(output_dir, f'I{name}.java')
        with open(filepath, 'w') as out:
            out.write(new_content)
        print(f"Created I{name}.java")

extract_interfaces(content)
