import os

def fix_imports():
    replacements = {
        'import hara.lang.base.P.Bits;': 'import hara.lang.base.primitive.Bits;',
        'import hara.lang.base.P.Box;': 'import hara.lang.base.primitive.Box;',
        'import hara.lang.base.P.Cast;': 'import hara.lang.base.primitive.Cast;',
        'import hara.lang.base.P.Complex;': 'import hara.lang.base.primitive.Complex;',
        'import hara.lang.base.P.Ratio;': 'import hara.lang.base.primitive.Ratio;',
        'import static hara.lang.base.P.Bits.': 'import static hara.lang.base.primitive.Bits.',
        'import static hara.lang.base.P.Box.': 'import static hara.lang.base.primitive.Box.',
        'import static hara.lang.base.P.Cast.': 'import static hara.lang.base.primitive.Cast.',
        # Handle cases where P.Bits was replaced by Bits in import line (unlikely but possible if regex was weird)
        'import hara.lang.base.Bits;': 'import hara.lang.base.primitive.Bits;',
        'import hara.lang.base.Ratio;': 'import hara.lang.base.primitive.Ratio;',
    }

    for root, dirs, files in os.walk('src'):
        for file in files:
            if file.endswith('.java'):
                filepath = os.path.join(root, file)
                with open(filepath, 'r') as f:
                    content = f.read()

                new_content = content
                for old, new in replacements.items():
                    new_content = new_content.replace(old, new)

                if new_content != content:
                    with open(filepath, 'w') as f:
                        f.write(new_content)
                    print(f"Updated imports in {filepath}")

if __name__ == '__main__':
    fix_imports()
