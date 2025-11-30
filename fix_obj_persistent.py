import os

def refactor():
    files = [
        'src/main/java/hara/lang/data/types/ObjPersistent.java',
        'src/main/java/hara/lang/data/types/ObjMutable.java'
    ]

    for filepath in files:
        with open(filepath, 'r') as f:
            content = f.read()

        new_content = content

        # 1. Make _meta public
        new_content = new_content.replace('protected final IMetadata _meta;', 'public final IMetadata _meta;')
        new_content = new_content.replace('protected IMetadata _meta;', 'public IMetadata _meta;')

        # 2. Implement display()
        # Remove abstract display() if present
        new_content = new_content.replace('public abstract String display();', '')

        # Add display implementation
        display_impl = """
  @Override
  public String display() {
    if (this instanceof IColl) {
      return Iter.display(((IColl) this).iterator());
    }
    return super.toString();
  }
"""
        # Insert before toString
        if 'public String toString()' in new_content:
            new_content = new_content.replace('@SuppressWarnings({"unchecked", "rawtypes"})\n  @Override\n  public String toString()', display_impl + '\n  @SuppressWarnings({"unchecked", "rawtypes"})\n  @Override\n  public String toString()')
        elif 'public String toString()' in new_content: # fallback match
             new_content = new_content.replace('public String toString()', display_impl + '\n  public String toString()')

        if new_content != content:
            with open(filepath, 'w') as f:
                f.write(new_content)
            print(f"Updated {filepath}")

if __name__ == '__main__':
    refactor()
