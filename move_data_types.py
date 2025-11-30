import os
import shutil

def refactor():
    src_dir = 'src/main/java/hara/data/types'
    dest_dir = 'src/main/java/hara/lang/data/types'

    if not os.path.exists(dest_dir):
        os.makedirs(dest_dir)

    # Move files
    files_to_move = []
    if os.path.exists(src_dir):
        for filename in os.listdir(src_dir):
            if filename.endswith('.java'):
                files_to_move.append(filename)
                shutil.move(os.path.join(src_dir, filename), os.path.join(dest_dir, filename))
                print(f"Moved {filename}")

        # Remove old dir if empty
        if not os.listdir(src_dir):
            os.rmdir(src_dir)
            print(f"Removed {src_dir}")

    # Update package declarations in moved files
    for filename in files_to_move:
        filepath = os.path.join(dest_dir, filename)
        with open(filepath, 'r') as f:
            content = f.read()

        new_content = content.replace('package hara.data.types;', 'package hara.lang.data.types;')

        if new_content != content:
            with open(filepath, 'w') as f:
                f.write(new_content)
            print(f"Updated package in {filepath}")

    # Update imports in all files
    update_imports()

def update_imports():
    for root, dirs, files in os.walk('src'):
        for file in files:
            if file.endswith('.java'):
                filepath = os.path.join(root, file)
                with open(filepath, 'r') as f:
                    content = f.read()

                new_content = content.replace('import hara.data.types.', 'import hara.lang.data.types.')
                new_content = new_content.replace('hara.data.types.', 'hara.lang.data.types.') # Fully qualified usage

                if new_content != content:
                    with open(filepath, 'w') as f:
                        f.write(new_content)
                    print(f"Updated imports in {filepath}")

if __name__ == '__main__':
    refactor()
