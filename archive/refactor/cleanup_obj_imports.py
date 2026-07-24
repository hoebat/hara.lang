import os

SRC_DIR = 'src/main/java'

def remove_unused_obj_imports():
    for root, dirs, files in os.walk(SRC_DIR):
        for file in files:
            if not file.endswith('.java'):
                continue

            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                lines = f.readlines()

            new_lines = []
            changed = False
            for line in lines:
                if 'import hara.lang.base.Obj;' in line:
                    # check if Obj is actually used?
                    # Since Obj was empty/commented out, it shouldn't be used.
                    # Unless it was used as a marker?
                    # But the compilation error says "cannot find symbol class Obj".
                    # So removing the import is the right fix, assuming no code uses it.
                    changed = True
                    continue # Skip this line
                new_lines.append(line)

            if changed:
                with open(filepath, 'w') as f:
                    f.writelines(new_lines)
                print(f"Updated {filepath}")

if __name__ == '__main__':
    remove_unused_obj_imports()
