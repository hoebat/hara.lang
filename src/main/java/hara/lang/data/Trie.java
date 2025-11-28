package hara.lang.data;

import hara.lang.base.Arr;
import hara.lang.base.Data;
import hara.lang.base.G;
import hara.lang.base.I;
import hara.lang.base.It;
import hara.lang.base.Std;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.function.Function;
import java.util.Objects;
import java.util.AbstractMap;

public interface Trie<V> extends I.Coll<String>, I.ObjType, I.Assoc<String, V>, I.Dissoc<String>, I.Lookup<String, V> {

    @Override
    Trie<V> assoc(String key, V val);

    @Override
    Trie<V> dissoc(String key);

    public interface Base<V> extends Trie<V> {

        Node<V> rootNode();

        @Override
        default I.Pair<String, V> find(String key) {
            Node<V> current = rootNode();
            for (char ch : key.toCharArray()) {
                Node<V> node = current.getChildren().get(ch);
                if (node == null) {
                    return null;
                }
                current = node;
            }
            return current.isEndOfWord() ? new Std.T.Tup2.L<>(null, key, current.getValue()) : null;
        }

        @Override
        default Iterator<String> keys() {
            return new TrieIterator<>(rootNode());
        }

        @Override
        default Iterator<V> vals() {
            return It.map(new TrieEntryIterator<>(rootNode()), Entry::getValue);
        }

        @Override
        default G.ObjType getObjType() {
            return G.ObjType.MAP;
        }

        @Override
        default String startString() {
            return "#{";
        }

        @Override
        default String endString() {
            return "}";
        }

        @Override
        default long hashCalc(G.HashType t) {
            Function<Object, Long> f = G.hashFn(t);
            long acc = Long.valueOf(hashSeed().hashCode());
            Iterator<Entry<String, V>> it = new TrieEntryIterator<>(rootNode());
            while (it.hasNext()) {
                Entry<String, V> entry = it.next();
                acc += f.apply(entry.getKey()) + f.apply(entry.getValue());
            }
            return acc;
        }

        @Override
        default boolean equality(Object other) {
            if (other instanceof Trie) {
                Trie<?> otherTrie = (Trie<?>) other;
                if (count() != otherTrie.count()) {
                    return false;
                }
                Iterator<String> it = iterator();
                while (it.hasNext()) {
                    String key = it.next();
                    if (!otherTrie.has(key) || !Objects.equals(find(key).getValue(), otherTrie.find(key).getValue())) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }

    public static class Node<V> {
        private final Map<Character, Node<V>> children;
        private V value;
        private boolean isEndOfWord;

        public Node() {
            this.children = new HashMap<>();
            this.isEndOfWord = false;
            this.value = null;
        }

        public Node(Node<V> other) {
            this.children = new HashMap<>(other.children);
            this.isEndOfWord = other.isEndOfWord;
            this.value = other.value;
        }

        public Map<Character, Node<V>> getChildren() {
            return children;
        }

        public V getValue() {
            return value;
        }

        public void setValue(V value) {
            this.value = value;
        }

        public boolean isEndOfWord() {
            return isEndOfWord;
        }

        public void setEndOfWord(boolean endOfWord) {
            isEndOfWord = endOfWord;
        }
    }

    public static class TrieEntryIterator<V> implements Iterator<Entry<String, V>> {
        private final Stack<AbstractMap.SimpleEntry<Node<V>, String>> stack = new Stack<>();
        private Entry<String, V> nextValue;

        public TrieEntryIterator(Node<V> root) {
            if (root != null) {
                stack.push(new AbstractMap.SimpleEntry<>(root, ""));
            }
            findNext();
        }

        @Override
        public boolean hasNext() {
            return nextValue != null;
        }

        @Override
        public Entry<String, V> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Entry<String, V> result = nextValue;
            findNext();
            return result;
        }

        private void findNext() {
            nextValue = null;
            while (!stack.isEmpty()) {
                AbstractMap.SimpleEntry<Node<V>, String> entry = stack.pop();
                Node<V> node = entry.getKey();
                String prefix = entry.getValue();

                List<Character> sortedKeys = new ArrayList<>(node.getChildren().keySet());
                Collections.sort(sortedKeys, Collections.reverseOrder());

                for (char ch : sortedKeys) {
                    stack.push(new AbstractMap.SimpleEntry<>(node.getChildren().get(ch), prefix + ch));
                }

                if (node.isEndOfWord()) {
                    nextValue = new Std.T.Tup2.L<>(null, prefix, node.getValue());
                    return;
                }
            }
        }
    }

    public static class TrieIterator<V> implements Iterator<String> {
        private final TrieEntryIterator<V> entryIterator;

        public TrieIterator(Node<V> root) {
            this.entryIterator = new TrieEntryIterator<>(root);
        }

        @Override
        public boolean hasNext() {
            return entryIterator.hasNext();
        }

        @Override
        public String next() {
            return entryIterator.next().getKey();
        }
    }


    public static class Standard<V> extends Data.RefType.PT implements Base<V>, I.ToMutable {
        private final Node<V> root;
        private final int _size;

        public static final Standard EMPTY = new Standard(null, new Node<>(), 0);

        public Standard(I.Metadata meta, Node<V> root, int size) {
            super(meta);
            this.root = root;
            this._size = size;
        }
        
        @SuppressWarnings("unchecked")
        public static <V> Standard<V> empty(I.Metadata meta) {
            Standard<V> ret = (Standard<V>) EMPTY;
            return (meta == null) ? ret : ret.withMeta(meta);
        }

        @SuppressWarnings("rawtypes")
        public static <V> Standard<V> from(I.Metadata meta, Object... elements) {
             return (Standard<V>) Mutable.from(meta, elements).toPersistent();
        }

        public static <V> Standard<V> into(Iterator<Entry<String, V>> it) {
            return Mutable.into(it).toPersistent();
        }

        public static <V> Standard<V> into(Standard<V> coll, Iterator<Entry<String, V>> it) {
            return Mutable.into(coll.toMutable(), it).toPersistent();
        }

        @Override
        public Node<V> rootNode() {
            return root;
        }

        @Override
        public Standard<V> assoc(String key, V val) {
            Node<V> newRoot = new Node<>(root);
            Node<V> current = newRoot;
            boolean isNewWord = !has(key);

            for (char ch : key.toCharArray()) {
                Node<V> child = current.getChildren().get(ch);
                Node<V> newNode = (child == null) ? new Node<>() : new Node<>(child);
                current.getChildren().put(ch, newNode);
                current = newNode;
            }
            current.setEndOfWord(true);
            current.setValue(val);
            return new Standard<>(_meta, newRoot, isNewWord ? _size + 1 : _size);
        }

        @Override
        public Standard<V> dissoc(String key) {
            int newSize = has(key) ? _size - 1 : _size;
            Node<V> newRoot = new Node<>(root);
            dissocHelper(newRoot, key, 0);
            return new Standard<>(_meta, newRoot, newSize);
        }

        private boolean dissocHelper(Node<V> current, String key, int index) {
            if (index == key.length()) {
                if (!current.isEndOfWord()) {
                    return false;
                }
                current.setEndOfWord(false);
                current.setValue(null);
                return current.getChildren().isEmpty();
            }
            char ch = key.charAt(index);
            Node<V> node = current.getChildren().get(ch);
            if (node == null) {
                return false;
            }
            Node<V> newNode = new Node<>(node);
            current.getChildren().put(ch, newNode);
            boolean shouldDeleteCurrentNode = dissocHelper(newNode, key, index + 1);
            if (shouldDeleteCurrentNode) {
                current.getChildren().remove(ch);
                return current.getChildren().isEmpty() && !current.isEndOfWord();
            }
            return false;
        }

        @Override
        public long count() {
            return _size;
        }

        @Override
        public Standard<V> empty() {
            return empty(_meta);
        }

        @Override
        public Iterator<String> iterator() {
            return new TrieIterator<>(root);
        }

        @Override
        public Standard<V> withMeta(I.Metadata meta) {
            return new Standard<>(meta, root, _size);
        }

        @Override
        public Standard<V> conj(String s) {
            return assoc(s, null);
        }

        @Override
        public Mutable<V> toMutable() {
            Mutable<V> m = new Mutable<>(_meta);
            Iterator<Entry<String, V>> it = new TrieEntryIterator<>(root);
            while(it.hasNext()) {
                Entry<String, V> e = it.next();
                m.assoc(e.getKey(), e.getValue());
            }
            return m;
        }
    }

    public static class Mutable<V> extends Data.RefType.MT implements Base<V>, I.ToPersistent {
        private Node<V> root;
        private int _size;

        public Mutable() {
            super(null);
            this.root = new Node<>();
            this._size = 0;
        }
        
        public Mutable(I.Metadata meta) {
            super(meta);
            this.root = new Node<>();
            this._size = 0;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public static <V> Mutable<V> from(I.Metadata meta, Object... elements) {
            return into(new Mutable<V>(meta), (Iterator)It.partitionPair(Arr.toIter(elements)));
        }

        public static <V> Mutable<V> into(Iterator<Entry<String, V>> it) {
            return into(new Mutable<V>(null), it);
        }

        public static <V> Mutable<V> into(Mutable<V> coll, Iterator<Entry<String, V>> it) {
            return It.reduce(it, coll, (m, e) -> m.assoc(e.getKey(), e.getValue()));
        }

        @Override
        public Node<V> rootNode() {
            return root;
        }

        @Override
        public Mutable<V> assoc(String key, V val) {
            Node<V> current = root;
            for (char ch : key.toCharArray()) {
                current = current.getChildren().computeIfAbsent(ch, c -> new Node<>());
            }
            if (!current.isEndOfWord()) {
                _size++;
            }
            current.setEndOfWord(true);
            current.setValue(val);
            return this;
        }

        @Override
        public Mutable<V> dissoc(String key) {
            if (has(key)) {
                dissocHelper(root, key, 0);
                _size--;
            }
            return this;
        }

        private boolean dissocHelper(Node<V> current, String key, int index) {
            if (index == key.length()) {
                if (!current.isEndOfWord()) {
                    return false;
                }
                current.setEndOfWord(false);
                current.setValue(null);
                return current.getChildren().isEmpty();
            }
            char ch = key.charAt(index);
            Node<V> node = current.getChildren().get(ch);
            if (node == null) {
                return false;
            }
            boolean shouldDeleteCurrentNode = dissocHelper(node, key, index + 1);
            if (shouldDeleteCurrentNode) {
                current.getChildren().remove(ch);
                return current.getChildren().isEmpty() && !current.isEndOfWord();
            }
            return false;
        }

        @Override
        public long count() {
            return _size;
        }

        @Override
        public Mutable<V> empty() {
            return new Mutable<V>(_meta);
        }

        @Override
        public Iterator<String> iterator() {
            return new TrieIterator<>(root);
        }

        @Override
        public Mutable<V> conj(String s) {
            return assoc(s, null);
        }

        @Override
        public Standard<V> toPersistent() {
             Standard<V> s = new Standard<>(_meta, new Node<>(), 0);
             Iterator<Entry<String, V>> it = new TrieEntryIterator<>(root);
             while(it.hasNext()) {
                 Entry<String, V> e = it.next();
                 s = s.assoc(e.getKey(), e.getValue());
             }
             return s;
        }
    }
}
