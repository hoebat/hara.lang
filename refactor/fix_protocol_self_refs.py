import os
import re

protocol_dir = 'src/main/java/hara/lang/protocol'

# List of all new interfaces
files = [f for f in os.listdir(protocol_dir) if f.startswith('I') and f.endswith('.java')]

for filename in files:
    name_without_i = filename[1:-5] # IColl.java -> Coll
    interface_name = filename[:-5]  # IColl
    filepath = os.path.join(protocol_dir, filename)

    with open(filepath, 'r') as f:
        content = f.read()

    # Replace the old name with the new name, but be careful.
    # We want to replace types, e.g. "Coll<E>" -> "IColl<E>"
    # "public interface IColl extends ..." (already done)
    # "Conj<E> conj(E e)" -> "IConj<E> conj(E e)"

    # We can use the fact that types start with Uppercase.
    # But wait, we have methods like "equals" etc.
    # The interface name is Capitalized.

    # Regex: match whole word 'name_without_i'
    # Check if it is not preceded by '.' (to avoid inner classes of other things, though less likely here)
    # And specifically, we want to update the self-reference.

    # "public class WatchEntry" -> "public class WatchEntry" (Keep as is? Or rename to IWatchEntry? No, user didn't ask to rename classes, just interfaces. Inner classes can stay named as is).
    # "Watch<R, V> ref" -> "IWatch<R, V> ref"

    # So we replace 'Name' with 'IName' IF it looks like a type usage.
    # Simplest heuristic: Word boundary.

    # Special exclusion: "public class Name" if Name was a class inside? No, interfaces don't contain classes named same as interface usually.
    # Exception: "public interface IColl ..." - "Coll" is not in "IColl".

    new_content = re.sub(r'(?<![\w])' + name_without_i + r'(?![\w])', interface_name, content)

    # Correction: If the inner class was named 'NameEntry', we don't want to change it.
    # But here the inner class is usually unique (WatchEntry).
    # If the file is IConj.java, we replace 'Conj' with 'IConj'.

    # However, we must ensure we don't break things like "Coll.java" string literals?
    # unlikely in these interfaces.

    if new_content != content:
        print(f"Fixing {filename}")
        with open(filepath, 'w') as f:
            f.write(new_content)
