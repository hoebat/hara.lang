import os
import re

def refactor():
    target_dir = 'src/main/java/hara/lang/data/types'

    # Create ObjFn.java
    obj_fn_content = """package hara.lang.data.types;

import hara.lang.base.Ex;
import hara.lang.base.G;
import hara.lang.data.Keyword;
import hara.lang.protocol.*;

public abstract class ObjFn extends ObjPersistent {

  public ObjFn() {
    super(null);
  }

  public ObjFn(IMetadata meta) {
    super(meta);
  }

  @Override
  public Constant.ObjType getObjType() {
    return Constant.ObjType.FUNCTION;
  }

  @Override
  public ObjFn withMeta(IMetadata meta) {
    throw new Ex.Unsupported();
  }

  @Override
  public long hashCalc(Constant.HashType t) {
    return G.hashFn(t).apply(hashSeed()) * 31 + ((IHash) _meta).hashCalc(t);
  }

  @Override
  public String display() {
    var name = Keyword.create("name").invoke(_meta);
    return "#<" + name + ">";
  }
}
"""
    with open(os.path.join(target_dir, 'ObjFn.java'), 'w') as f:
        f.write(obj_fn_content)
    print("Created ObjFn.java")

    # Create ObjEmpty.java
    obj_empty_content = """package hara.lang.data.types;

import hara.lang.base.Iter;
import hara.lang.protocol.*;
import java.util.Iterator;

public abstract class ObjEmpty<E> extends ObjPersistent implements IColl<E>, INth<E> {

  public ObjEmpty() {
    super(null);
  }

  public ObjEmpty(IMetadata meta) {
    super(meta);
  }

  @Override
  public long count() {
    return 0;
  }

  @Override
  public ObjEmpty<E> empty() {
    return this;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public Iterator iterator() {
    return Iter.emptyIterator();
  }

  @Override
  public E nth(long i) {
    return null;
  }
}
"""
    with open(os.path.join(target_dir, 'ObjEmpty.java'), 'w') as f:
        f.write(obj_empty_content)
    print("Created ObjEmpty.java")

    # Update Obj.java (remove FN and EMPTY)
    obj_path = 'src/main/java/hara/lang/base/Obj.java'
    with open(obj_path, 'r') as f:
        lines = f.readlines()

    new_lines = []
    skip = False
    brace_count = 0

    for line in lines:
        if 'abstract class FN extends ObjPersistent' in line or 'abstract class EMPTY<E> extends ObjPersistent' in line:
            skip = True
            brace_count = 0

        if skip:
            brace_count += line.count('{')
            brace_count -= line.count('}')
            if brace_count == 0:
                skip = False
            continue

        new_lines.append(line)

    with open(obj_path, 'w') as f:
        f.writelines(new_lines)
    print("Updated Obj.java")

    # Update references
    update_references()

def update_references():
    for root, dirs, files in os.walk('src'):
        for file in files:
            if file.endswith('.java'):
                filepath = os.path.join(root, file)
                with open(filepath, 'r') as f:
                    content = f.read()

                new_content = content

                # Replace Obj.FN -> ObjFn
                new_content = new_content.replace('Obj.FN', 'ObjFn')

                # Replace Obj.EMPTY -> ObjEmpty
                new_content = new_content.replace('Obj.EMPTY', 'ObjEmpty')

                # Add imports if needed
                if 'ObjFn' in new_content and 'import hara.lang.data.types.ObjFn;' not in new_content:
                     pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
                     if pkg_match:
                         end = pkg_match.end()
                         new_content = new_content[:end] + '\n\nimport hara.lang.data.types.ObjFn;' + new_content[end:]

                if 'ObjEmpty' in new_content and 'import hara.lang.data.types.ObjEmpty;' not in new_content:
                     if 'import hara.lang.data.types.ObjEmpty;' not in new_content:
                         pkg_match = re.search(r'package\s+[\w\.]+;', new_content)
                         if pkg_match:
                             end = pkg_match.end()
                             new_content = new_content[:end] + '\n\nimport hara.lang.data.types.ObjEmpty;' + new_content[end:]

                if new_content != content:
                    with open(filepath, 'w') as f:
                        f.write(new_content)
                    print(f"Updated references in {filepath}")

if __name__ == '__main__':
    refactor()
