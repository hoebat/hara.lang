use std::cell::Cell;
use std::hash::{Hash, Hasher};
use std::rc::Rc;

use crate::lang::protocol::{
    HashType, IAssoc, IColl, IConj, ICount, IDisplay, IDissoc, IEmpty, IEquality, IFind, IHash,
    ILookup, IMetadata, IMutable, IObjType, IPersistent, IToMutable, IToPersistent, ObjType,
};

const SHIFT: usize = 5;
const MASK: u64 = 0x1f;
#[derive(Debug, Clone)]
enum Slot<K, V> {
    Entry { hash: u64, key: K, value: V },
    Node(Rc<Node<K, V>>),
}
#[derive(Debug, Clone)]
enum Node<K, V> {
    Bitmap { bitmap: u32, slots: Vec<Slot<K, V>> },
    Collision { hash: u64, entries: Vec<(K, V)> },
}
impl<K, V> Node<K, V> {
    fn empty() -> Rc<Self> {
        Rc::new(Self::Bitmap {
            bitmap: 0,
            slots: Vec::new(),
        })
    }
}
fn key_hash<K: Hash>(key: &K) -> u64 {
    let mut h = std::collections::hash_map::DefaultHasher::new();
    key.hash(&mut h);
    h.finish()
}
fn mask(hash: u64, shift: usize) -> usize {
    ((hash >> shift) & MASK) as usize
}
fn bit(hash: u64, shift: usize) -> u32 {
    1u32 << mask(hash, shift)
}
fn index(bitmap: u32, bit: u32) -> usize {
    (bitmap & (bit - 1)).count_ones() as usize
}

fn merge_entries<K: Clone, V: Clone>(
    shift: usize,
    a: (u64, K, V),
    b: (u64, K, V),
) -> Rc<Node<K, V>> {
    if a.0 == b.0 {
        return Rc::new(Node::Collision {
            hash: a.0,
            entries: vec![(a.1, a.2), (b.1, b.2)],
        });
    }
    let abit = bit(a.0, shift);
    let bbit = bit(b.0, shift);
    if abit == bbit {
        return Rc::new(Node::Bitmap {
            bitmap: abit,
            slots: vec![Slot::Node(merge_entries(shift + SHIFT, a, b))],
        });
    }
    let mut slots = Vec::with_capacity(2);
    if mask(a.0, shift) < mask(b.0, shift) {
        slots.push(Slot::Entry {
            hash: a.0,
            key: a.1,
            value: a.2,
        });
        slots.push(Slot::Entry {
            hash: b.0,
            key: b.1,
            value: b.2,
        })
    } else {
        slots.push(Slot::Entry {
            hash: b.0,
            key: b.1,
            value: b.2,
        });
        slots.push(Slot::Entry {
            hash: a.0,
            key: a.1,
            value: a.2,
        })
    }
    Rc::new(Node::Bitmap {
        bitmap: abit | bbit,
        slots,
    })
}
fn merge_node<K: Clone, V: Clone>(
    shift: usize,
    node_hash: u64,
    node: Rc<Node<K, V>>,
    hash: u64,
    key: K,
    value: V,
) -> Rc<Node<K, V>> {
    let abit = bit(node_hash, shift);
    let bbit = bit(hash, shift);
    if abit == bbit {
        return Rc::new(Node::Bitmap {
            bitmap: abit,
            slots: vec![Slot::Node(merge_node(
                shift + SHIFT,
                node_hash,
                node,
                hash,
                key,
                value,
            ))],
        });
    }
    let entry = Slot::Entry { hash, key, value };
    let slots = if mask(node_hash, shift)
        < mask(
            match &entry {
                Slot::Entry { hash, .. } => *hash,
                _ => 0,
            },
            shift,
        ) {
        vec![Slot::Node(node), entry]
    } else {
        vec![entry, Slot::Node(node)]
    };
    Rc::new(Node::Bitmap {
        bitmap: abit | bbit,
        slots,
    })
}
fn assoc_node<K: Clone + Eq, V: Clone>(
    node: &Rc<Node<K, V>>,
    shift: usize,
    hash: u64,
    key: K,
    value: V,
) -> (Rc<Node<K, V>>, bool) {
    match node.as_ref() {
        Node::Collision {
            hash: collision,
            entries,
        } => {
            if *collision == hash {
                let mut out = entries.clone();
                if let Some((_, v)) = out.iter_mut().find(|(k, _)| k == &key) {
                    *v = value;
                    (Rc::new(Node::Collision { hash, entries: out }), false)
                } else {
                    out.push((key, value));
                    (Rc::new(Node::Collision { hash, entries: out }), true)
                }
            } else {
                (
                    merge_node(shift, *collision, node.clone(), hash, key, value),
                    true,
                )
            }
        }
        Node::Bitmap { bitmap, slots } => {
            let b = bit(hash, shift);
            let i = index(*bitmap, b);
            if bitmap & b == 0 {
                let mut out = slots.clone();
                out.insert(i, Slot::Entry { hash, key, value });
                return (
                    Rc::new(Node::Bitmap {
                        bitmap: bitmap | b,
                        slots: out,
                    }),
                    true,
                );
            }
            let mut out = slots.clone();
            match &slots[i] {
                Slot::Entry {
                    hash: old_hash,
                    key: old_key,
                    value: old_value,
                } => {
                    if old_key == &key {
                        out[i] = Slot::Entry { hash, key, value };
                        (
                            Rc::new(Node::Bitmap {
                                bitmap: *bitmap,
                                slots: out,
                            }),
                            false,
                        )
                    } else {
                        out[i] = Slot::Node(merge_entries(
                            shift + SHIFT,
                            (*old_hash, old_key.clone(), old_value.clone()),
                            (hash, key, value),
                        ));
                        (
                            Rc::new(Node::Bitmap {
                                bitmap: *bitmap,
                                slots: out,
                            }),
                            true,
                        )
                    }
                }
                Slot::Node(child) => {
                    let (next, added) = assoc_node(child, shift + SHIFT, hash, key, value);
                    out[i] = Slot::Node(next);
                    (
                        Rc::new(Node::Bitmap {
                            bitmap: *bitmap,
                            slots: out,
                        }),
                        added,
                    )
                }
            }
        }
    }
}
fn find_node<'a, K: Eq, V>(
    node: &'a Node<K, V>,
    shift: usize,
    hash: u64,
    key: &K,
) -> Option<(&'a K, &'a V)> {
    match node {
        Node::Collision { hash: h, entries } if *h == hash => {
            entries.iter().find(|(k, _)| k == key).map(|(k, v)| (k, v))
        }
        Node::Collision { .. } => None,
        Node::Bitmap { bitmap, slots } => {
            let b = bit(hash, shift);
            if bitmap & b == 0 {
                return None;
            }
            match &slots[index(*bitmap, b)] {
                Slot::Entry {
                    key: k, value: v, ..
                } if k == key => Some((k, v)),
                Slot::Entry { .. } => None,
                Slot::Node(child) => find_node(child, shift + SHIFT, hash, key),
            }
        }
    }
}
fn dissoc_node<K: Clone + Eq, V: Clone>(
    node: &Rc<Node<K, V>>,
    shift: usize,
    hash: u64,
    key: &K,
) -> (Rc<Node<K, V>>, bool) {
    match node.as_ref() {
        Node::Collision { hash: h, entries } => {
            if *h != hash {
                return (node.clone(), false);
            }
            let mut out = entries.clone();
            let before = out.len();
            out.retain(|(k, _)| k != key);
            if out.len() == before {
                return (node.clone(), false);
            }
            if out.len() == 1 {
                let (k, v) = out.pop().unwrap();
                (
                    Rc::new(Node::Bitmap {
                        bitmap: bit(hash, shift),
                        slots: vec![Slot::Entry {
                            hash,
                            key: k,
                            value: v,
                        }],
                    }),
                    true,
                )
            } else {
                (Rc::new(Node::Collision { hash, entries: out }), true)
            }
        }
        Node::Bitmap { bitmap, slots } => {
            let b = bit(hash, shift);
            if bitmap & b == 0 {
                return (node.clone(), false);
            }
            let i = index(*bitmap, b);
            match &slots[i] {
                Slot::Entry { key: k, .. } if k == key => {
                    let mut out = slots.clone();
                    out.remove(i);
                    (
                        Rc::new(Node::Bitmap {
                            bitmap: bitmap ^ b,
                            slots: out,
                        }),
                        true,
                    )
                }
                Slot::Entry { .. } => (node.clone(), false),
                Slot::Node(child) => {
                    let (next, removed) = dissoc_node(child, shift + SHIFT, hash, key);
                    if !removed {
                        return (node.clone(), false);
                    }
                    let mut out = slots.clone();
                    if matches!(next.as_ref(), Node::Bitmap { bitmap: 0, .. }) {
                        out.remove(i);
                        (
                            Rc::new(Node::Bitmap {
                                bitmap: bitmap ^ b,
                                slots: out,
                            }),
                            true,
                        )
                    } else {
                        out[i] = Slot::Node(next);
                        (
                            Rc::new(Node::Bitmap {
                                bitmap: *bitmap,
                                slots: out,
                            }),
                            true,
                        )
                    }
                }
            }
        }
    }
}
fn collect<'a, K, V>(node: &'a Node<K, V>, out: &mut Vec<(&'a K, &'a V)>) {
    match node {
        Node::Collision { entries, .. } => out.extend(entries.iter().map(|(k, v)| (k, v))),
        Node::Bitmap { slots, .. } => {
            for slot in slots {
                match slot {
                    Slot::Entry { key, value, .. } => out.push((key, value)),
                    Slot::Node(node) => collect(node, out),
                }
            }
        }
    }
}

#[derive(Debug, Clone)]
pub struct Standard<K, V> {
    metadata: Option<Rc<crate::lang::data::Metadata>>,
    root: Rc<Node<K, V>>,
    size: usize,
}
impl<K, V> Default for Standard<K, V> {
    fn default() -> Self {
        Self {
            metadata: None,
            root: Node::empty(),
            size: 0,
        }
    }
}
impl<K: Clone + Eq + Hash, V: Clone> Standard<K, V> {
    pub fn new() -> Self {
        Self::default()
    }
    pub fn len(&self) -> usize {
        self.size
    }
    pub fn is_empty(&self) -> bool {
        self.size == 0
    }
    pub fn get(&self, key: &K) -> Option<&V> {
        find_node(&self.root, 0, key_hash(key), key).map(|(_, v)| v)
    }
    pub fn find_entry(&self, key: &K) -> Option<(&K, &V)> {
        find_node(&self.root, 0, key_hash(key), key)
    }
    pub fn assoc_value(&self, key: K, value: V) -> Self {
        let (root, added) = assoc_node(&self.root, 0, key_hash(&key), key, value);
        Self {
            metadata: self.metadata.clone(),
            root,
            size: self.size + usize::from(added),
        }
    }
    pub fn dissoc_value(&self, key: &K) -> Self {
        let (root, removed) = dissoc_node(&self.root, 0, key_hash(key), key);
        if removed {
            Self {
                metadata: self.metadata.clone(),
                root,
                size: self.size - 1,
            }
        } else {
            self.clone()
        }
    }
    pub fn iter(&self) -> std::vec::IntoIter<(&K, &V)> {
        self.entries().into_iter()
    }
    pub fn entries(&self) -> Vec<(&K, &V)> {
        let mut out = Vec::with_capacity(self.size);
        collect(&self.root, &mut out);
        out
    }
    pub fn shares_root_with(&self, other: &Self) -> bool {
        Rc::ptr_eq(&self.root, &other.root)
    }
}
impl<K: Clone + Eq + Hash, V: Clone> FromIterator<(K, V)> for Standard<K, V> {
    fn from_iter<T: IntoIterator<Item = (K, V)>>(iter: T) -> Self {
        iter.into_iter()
            .fold(Self::new(), |map, (k, v)| map.assoc_value(k, v))
    }
}
impl<K: Clone + Eq + Hash, V: Clone> IntoIterator for Standard<K, V> {
    type Item = (K, V);
    type IntoIter = std::vec::IntoIter<(K, V)>;
    fn into_iter(self) -> Self::IntoIter {
        self.entries()
            .into_iter()
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect::<Vec<_>>()
            .into_iter()
    }
}
impl<K: Clone + Eq + Hash, V: Clone + PartialEq> PartialEq for Standard<K, V> {
    fn eq(&self, other: &Self) -> bool {
        self.size == other.size && self.entries().iter().all(|(k, v)| other.get(k) == Some(*v))
    }
}
impl<K: Clone + Eq + Hash, V: Clone> ICount for Standard<K, V> {
    fn count(&self) -> usize {
        self.size
    }
}
impl<K: Clone + Eq + Hash, V: Clone> IAssoc<K, V> for Standard<K, V> {
    type Output = Self;
    fn assoc(&self, key: K, value: V) -> Self {
        self.assoc_value(key, value)
    }
}
impl<K: Clone + Eq + Hash, V: Clone> IDissoc<K> for Standard<K, V> {
    type Output = Self;
    fn dissoc(&self, key: &K) -> Self {
        self.dissoc_value(key)
    }
}
impl<K: Clone + Eq + Hash, V: Clone> IFind<K> for Standard<K, V> {
    type Output = (K, V);
    fn find(&self, key: &K) -> Option<Self::Output> {
        self.find_entry(key).map(|(k, v)| (k.clone(), v.clone()))
    }
}
impl<K: Clone + Eq + Hash, V: Clone> ILookup<K, V> for Standard<K, V> {
    type Keys = std::vec::IntoIter<K>;
    type Values = std::vec::IntoIter<V>;
    fn keys(&self) -> Self::Keys {
        self.entries()
            .into_iter()
            .map(|(k, _)| k.clone())
            .collect::<Vec<_>>()
            .into_iter()
    }
    fn vals(&self) -> Self::Values {
        self.entries()
            .into_iter()
            .map(|(_, v)| v.clone())
            .collect::<Vec<_>>()
            .into_iter()
    }
}
impl<K: Clone + Eq + Hash, V: Clone> IEmpty for Standard<K, V> {
    type Output = Self;
    fn empty(&self) -> Self {
        Self::new().with_meta(self.metadata.clone())
    }
}
impl<K: Clone + Eq + Hash, V: Clone> IMetadata for Standard<K, V> {
    type Metadata = Rc<crate::lang::data::Metadata>;
    fn meta(&self) -> Option<&Self::Metadata> {
        self.metadata.as_ref()
    }
    fn with_meta(&self, metadata: Option<Self::Metadata>) -> Self {
        Self {
            metadata,
            ..self.clone()
        }
    }
}
impl<K: Clone + Eq + Hash, V: Clone> IPersistent for Standard<K, V> {}
impl<K: Clone + Eq + Hash, V: Clone> IConj<(K, V)> for Standard<K, V> {
    type Output = Self;
    fn conj(&self, (key, value): (K, V)) -> Self {
        self.assoc_value(key, value)
    }
}
impl<K: Clone + Eq + Hash, V: Clone + PartialEq> IEquality for Standard<K, V> {
    fn equality(&self, other: &Self) -> bool {
        self == other
    }
}
impl<K: Clone + Eq + Hash + std::fmt::Debug, V: Clone + std::fmt::Debug> IDisplay
    for Standard<K, V>
{
    fn display(&self) -> String {
        format!(
            "{{{}}}",
            self.entries()
                .iter()
                .map(|(k, v)| format!("{k:?} {v:?}"))
                .collect::<Vec<_>>()
                .join(" ")
        )
    }
}
impl<K: Clone + Eq + Hash, V: Clone + Hash> IHash for Standard<K, V> {
    fn hash_calc(&self, _hash_type: HashType) -> u64 {
        self.entries()
            .iter()
            .map(|(k, v)| {
                let mut s = std::collections::hash_map::DefaultHasher::new();
                k.hash(&mut s);
                v.hash(&mut s);
                s.finish()
            })
            .fold(0u64, u64::wrapping_add)
    }
}
impl<K: Clone + Eq + Hash + std::fmt::Debug, V: Clone + std::fmt::Debug> IObjType
    for Standard<K, V>
{
    fn obj_type(&self) -> ObjType {
        ObjType::Map
    }
}
impl<K, V> IColl<(K, V)> for Standard<K, V>
where
    K: Clone + Eq + Hash + std::fmt::Debug,
    V: Clone + PartialEq + Hash + std::fmt::Debug,
{
    fn start_string(&self) -> &'static str {
        "{"
    }
    fn end_string(&self) -> &'static str {
        "}"
    }
}
impl<K: Clone + Eq + Hash, V: Clone> IToMutable for Standard<K, V> {
    type Mutable = Mutable<K, V>;
    fn to_mutable(&self) -> Self::Mutable {
        Mutable {
            editable: Cell::new(true),
            map: self.clone(),
        }
    }
}
#[derive(Debug, Clone)]
pub struct Mutable<K, V> {
    editable: Cell<bool>,
    map: Standard<K, V>,
}
impl<K: Clone + Eq + Hash, V: Clone> Mutable<K, V> {
    fn check(&self) {
        assert!(self.editable.get(), "mutable map used after to_persistent")
    }
    pub fn assoc(&mut self, key: K, value: V) -> &mut Self {
        self.check();
        self.map = self.map.assoc_value(key, value);
        self
    }
    pub fn dissoc(&mut self, key: &K) -> &mut Self {
        self.check();
        self.map = self.map.dissoc_value(key);
        self
    }
}
impl<K, V> IMutable for Mutable<K, V> {}
impl<K: Clone + Eq + Hash, V: Clone> IToPersistent for Mutable<K, V> {
    type Persistent = Standard<K, V>;
    fn to_persistent(&mut self) -> Self::Persistent {
        self.check();
        self.editable.set(false);
        self.map.clone()
    }
}

#[cfg(test)]
mod tests {
    use super::Standard;
    use std::hash::{Hash, Hasher};
    #[derive(Clone, Debug, Eq, PartialEq)]
    struct Collision(i32);
    impl Hash for Collision {
        fn hash<H: Hasher>(&self, state: &mut H) {
            0.hash(state)
        }
    }
    #[test]
    fn persistent_operations_and_mutable_round_trip_preserve_metadata() {
        use crate::lang::protocol::{IEmpty, IMetadata, IToMutable, IToPersistent};
        let map = Standard::new()
            .assoc_value("a", 1)
            .with_meta(Some(crate::lang::data::Metadata::document("doc")));
        assert_eq!(
            map.assoc_value("b", 2).meta().map(|m| m.doc().unwrap()),
            Some("doc")
        );
        assert_eq!(
            map.dissoc_value(&"a").meta().map(|m| m.doc().unwrap()),
            Some("doc")
        );
        assert_eq!(map.empty().meta().map(|m| m.doc().unwrap()), Some("doc"));
        let mut mutable = map.to_mutable();
        mutable.assoc("b", 2);
        assert_eq!(
            mutable.to_persistent().meta().map(|m| m.doc().unwrap()),
            Some("doc")
        );
    }

    #[test]
    fn assoc_collision_removal_and_persistence() {
        let empty = Standard::new();
        let a = empty.assoc_value(Collision(1), 10);
        let b = a.assoc_value(Collision(2), 20);
        let c = b.dissoc_value(&Collision(1));
        assert_eq!(a.get(&Collision(1)), Some(&10));
        assert_eq!(b.get(&Collision(2)), Some(&20));
        assert_eq!(c.get(&Collision(1)), None);
        assert_eq!(c.get(&Collision(2)), Some(&20));
        assert!(empty.shares_root_with(&empty.dissoc_value(&Collision(9))));
    }
}
