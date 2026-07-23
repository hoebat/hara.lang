use std::cell::Cell;
use std::cmp::Ordering;
use std::rc::Rc;

use crate::lang::protocol::{
    IAssoc, ICount, IDissoc, IEmpty, IFind, IIndexedKV, ILookup, IMetadata, IMutable, INth,
    IPersistent, IToMutable, IToPersistent,
};

type Link<K, V> = Option<Rc<Node<K, V>>>;

#[derive(Debug, Clone)]
pub struct Node<K, V> {
    pub key: K,
    pub value: V,
    left: Link<K, V>,
    right: Link<K, V>,
    height: i16,
    size: usize,
}

fn height<K, V>(node: &Link<K, V>) -> i16 {
    node.as_ref().map_or(0, |n| n.height)
}
fn size<K, V>(node: &Link<K, V>) -> usize {
    node.as_ref().map_or(0, |n| n.size)
}
fn node<K, V>(left: Link<K, V>, key: K, value: V, right: Link<K, V>) -> Rc<Node<K, V>> {
    Rc::new(Node {
        height: 1 + height(&left).max(height(&right)),
        size: 1 + size(&left) + size(&right),
        key,
        value,
        left,
        right,
    })
}
fn rotate_left<K: Clone, V: Clone>(root: Rc<Node<K, V>>) -> Rc<Node<K, V>> {
    let right = root.right.as_ref().expect("right-heavy node");
    let left = node(
        root.left.clone(),
        root.key.clone(),
        root.value.clone(),
        right.left.clone(),
    );
    node(
        Some(left),
        right.key.clone(),
        right.value.clone(),
        right.right.clone(),
    )
}
fn rotate_right<K: Clone, V: Clone>(root: Rc<Node<K, V>>) -> Rc<Node<K, V>> {
    let left = root.left.as_ref().expect("left-heavy node");
    let right = node(
        left.right.clone(),
        root.key.clone(),
        root.value.clone(),
        root.right.clone(),
    );
    node(
        left.left.clone(),
        left.key.clone(),
        left.value.clone(),
        Some(right),
    )
}
fn balance<K: Clone, V: Clone>(root: Rc<Node<K, V>>) -> Rc<Node<K, V>> {
    let factor = height(&root.left) - height(&root.right);
    if factor > 1 {
        let mut root = root;
        let left = root.left.as_ref().expect("left-heavy node");
        if height(&left.left) < height(&left.right) {
            root = node(
                Some(rotate_left(left.clone())),
                root.key.clone(),
                root.value.clone(),
                root.right.clone(),
            );
        }
        rotate_right(root)
    } else if factor < -1 {
        let mut root = root;
        let right = root.right.as_ref().expect("right-heavy node");
        if height(&right.right) < height(&right.left) {
            root = node(
                root.left.clone(),
                root.key.clone(),
                root.value.clone(),
                Some(rotate_right(right.clone())),
            );
        }
        rotate_left(root)
    } else {
        root
    }
}
fn assoc<K: Clone + Ord, V: Clone>(root: &Link<K, V>, key: K, value: V) -> Link<K, V> {
    Some(match root {
        None => node(None, key, value, None),
        Some(current) => match key.cmp(&current.key) {
            Ordering::Less => balance(node(
                assoc(&current.left, key, value),
                current.key.clone(),
                current.value.clone(),
                current.right.clone(),
            )),
            Ordering::Greater => balance(node(
                current.left.clone(),
                current.key.clone(),
                current.value.clone(),
                assoc(&current.right, key, value),
            )),
            Ordering::Equal => node(current.left.clone(), key, value, current.right.clone()),
        },
    })
}
fn remove_min<K: Clone, V: Clone>(root: &Rc<Node<K, V>>) -> (Link<K, V>, K, V) {
    match &root.left {
        None => (root.right.clone(), root.key.clone(), root.value.clone()),
        Some(left) => {
            let (new_left, key, value) = remove_min(left);
            (
                Some(balance(node(
                    new_left,
                    root.key.clone(),
                    root.value.clone(),
                    root.right.clone(),
                ))),
                key,
                value,
            )
        }
    }
}
fn dissoc<K: Clone + Ord, V: Clone>(root: &Link<K, V>, key: &K) -> Link<K, V> {
    let Some(current) = root else {
        return None;
    };
    match key.cmp(&current.key) {
        Ordering::Less => Some(balance(node(
            dissoc(&current.left, key),
            current.key.clone(),
            current.value.clone(),
            current.right.clone(),
        ))),
        Ordering::Greater => Some(balance(node(
            current.left.clone(),
            current.key.clone(),
            current.value.clone(),
            dissoc(&current.right, key),
        ))),
        Ordering::Equal => match (&current.left, &current.right) {
            (None, _) => current.right.clone(),
            (_, None) => current.left.clone(),
            (_, Some(right)) => {
                let (new_right, next_key, next_value) = remove_min(right);
                Some(balance(node(
                    current.left.clone(),
                    next_key,
                    next_value,
                    new_right,
                )))
            }
        },
    }
}

#[derive(Debug, Clone)]
pub struct Standard<K, V> {
    metadata: Option<Rc<str>>,
    root: Link<K, V>,
}
impl<K, V> Default for Standard<K, V> {
    fn default() -> Self {
        Self {
            metadata: None,
            root: None,
        }
    }
}
impl<K: Clone + Ord, V: Clone> Standard<K, V> {
    pub fn new() -> Self {
        Self::default()
    }
    pub fn len(&self) -> usize {
        size(&self.root)
    }
    pub fn is_empty(&self) -> bool {
        self.root.is_none()
    }
    pub fn get(&self, key: &K) -> Option<&V> {
        self.find_entry(key).map(|(_, value)| value)
    }
    pub fn find_entry(&self, key: &K) -> Option<(&K, &V)> {
        let mut cursor = self.root.as_deref();
        while let Some(current) = cursor {
            match key.cmp(&current.key) {
                Ordering::Less => cursor = current.left.as_deref(),
                Ordering::Greater => cursor = current.right.as_deref(),
                Ordering::Equal => return Some((&current.key, &current.value)),
            }
        }
        None
    }
    pub fn assoc_value(&self, key: K, value: V) -> Self {
        Self {
            metadata: self.metadata.clone(),
            root: assoc(&self.root, key, value),
        }
    }
    pub fn dissoc_value(&self, key: &K) -> Self {
        Self {
            metadata: self.metadata.clone(),
            root: dissoc(&self.root, key),
        }
    }
    pub fn iter(&self) -> Iter<'_, K, V> {
        Iter::new(self.root.as_deref())
    }
    pub fn nth_entry(&self, mut index: usize) -> Option<&Node<K, V>> {
        let mut cursor = self.root.as_deref();
        while let Some(current) = cursor {
            let left_size = size(&current.left);
            if index < left_size {
                cursor = current.left.as_deref();
            } else if index == left_size {
                return Some(current);
            } else {
                index -= left_size + 1;
                cursor = current.right.as_deref();
            }
        }
        None
    }
    pub fn index_of_key(&self, key: &K) -> Option<usize> {
        let mut offset = 0;
        let mut cursor = self.root.as_deref();
        while let Some(current) = cursor {
            match key.cmp(&current.key) {
                Ordering::Less => cursor = current.left.as_deref(),
                Ordering::Greater => {
                    offset += size(&current.left) + 1;
                    cursor = current.right.as_deref();
                }
                Ordering::Equal => return Some(offset + size(&current.left)),
            }
        }
        None
    }
    pub fn inclusive_floor_index(&self, key: &K) -> Option<usize> {
        let mut best = None;
        for (index, (candidate, _)) in self.iter().enumerate() {
            if candidate <= key {
                best = Some(index)
            } else {
                break;
            }
        }
        best
    }
    pub fn ceil_index(&self, key: &K) -> Option<usize> {
        self.iter().position(|(candidate, _)| candidate >= key)
    }
    pub fn slice(&self, min: &K, max: &K) -> Self {
        self.iter()
            .filter(|(key, _)| *key >= min && *key <= max)
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect::<Self>()
            .with_meta(self.metadata.clone())
    }
    pub fn map_values<U: Clone>(&self, f: impl Fn(&K, &V) -> U) -> Standard<K, U> {
        self.iter()
            .map(|(k, v)| (k.clone(), f(k, v)))
            .collect::<Standard<K, U>>()
            .with_meta(self.metadata.clone())
    }
}
impl<K: Clone + Ord, V: Clone> FromIterator<(K, V)> for Standard<K, V> {
    fn from_iter<T: IntoIterator<Item = (K, V)>>(it: T) -> Self {
        it.into_iter()
            .fold(Self::new(), |m, (k, v)| m.assoc_value(k, v))
    }
}
impl<K: Clone + Ord, V: Clone> ICount for Standard<K, V> {
    fn count(&self) -> usize {
        self.len()
    }
}
impl<K: Clone + Ord, V: Clone> IFind<K> for Standard<K, V> {
    type Output = (K, V);
    fn find(&self, k: &K) -> Option<Self::Output> {
        self.find_entry(k).map(|(k, v)| (k.clone(), v.clone()))
    }
}
impl<K: Clone + Ord, V: Clone> ILookup<K, V> for Standard<K, V> {
    type Keys = std::vec::IntoIter<K>;
    type Values = std::vec::IntoIter<V>;
    fn keys(&self) -> Self::Keys {
        self.iter()
            .map(|(k, _)| k.clone())
            .collect::<Vec<_>>()
            .into_iter()
    }
    fn vals(&self) -> Self::Values {
        self.iter()
            .map(|(_, v)| v.clone())
            .collect::<Vec<_>>()
            .into_iter()
    }
}
impl<K: Clone + Ord, V: Clone> IAssoc<K, V> for Standard<K, V> {
    fn assoc(&self, k: K, v: V) -> Self {
        self.assoc_value(k, v)
    }
}
impl<K: Clone + Ord, V: Clone> IDissoc<K> for Standard<K, V> {
    fn dissoc(&self, k: &K) -> Self {
        self.dissoc_value(k)
    }
}
impl<K: Clone + Ord, V: Clone> INth<Node<K, V>> for Standard<K, V> {
    fn nth(&self, index: usize) -> Option<&Node<K, V>> {
        self.nth_entry(index)
    }
}
impl<K: Clone + Ord, V: Clone + PartialEq> IIndexedKV<K, V> for Standard<K, V> {
    fn index_of_key(&self, key: &K) -> Option<usize> {
        Standard::index_of_key(self, key)
    }
    fn index_of_val(&self, value: &V) -> Option<usize> {
        self.iter().position(|(_, candidate)| candidate == value)
    }
}
impl<K: Clone + Ord, V: Clone> IEmpty for Standard<K, V> {
    type Output = Self;
    fn empty(&self) -> Self {
        Self::new().with_meta(self.metadata.clone())
    }
}
impl<K: Clone + Ord, V: Clone> IMetadata for Standard<K, V> {
    type Metadata = Rc<str>;
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
impl<K: Clone + Ord, V: Clone> IPersistent for Standard<K, V> {}
impl<K: Clone + Ord, V: Clone> IToMutable for Standard<K, V> {
    type Mutable = Mutable<K, V>;
    fn to_mutable(&self) -> Self::Mutable {
        Mutable {
            editable: Cell::new(true),
            map: self.clone(),
        }
    }
}

pub struct Iter<'a, K, V> {
    stack: Vec<&'a Node<K, V>>,
}
impl<'a, K, V> Iter<'a, K, V> {
    fn new(root: Option<&'a Node<K, V>>) -> Self {
        let mut it = Self { stack: Vec::new() };
        it.push_left(root);
        it
    }
    fn push_left(&mut self, mut n: Option<&'a Node<K, V>>) {
        while let Some(current) = n {
            self.stack.push(current);
            n = current.left.as_deref();
        }
    }
}
impl<'a, K, V> Iterator for Iter<'a, K, V> {
    type Item = (&'a K, &'a V);
    fn next(&mut self) -> Option<Self::Item> {
        let n = self.stack.pop()?;
        self.push_left(n.right.as_deref());
        Some((&n.key, &n.value))
    }
}

#[derive(Debug, Clone)]
pub struct Mutable<K, V> {
    editable: Cell<bool>,
    map: Standard<K, V>,
}
impl<K: Clone + Ord, V: Clone> Mutable<K, V> {
    fn check(&self) {
        assert!(
            self.editable.get(),
            "mutable sorted map used after to_persistent"
        )
    }
    pub fn assoc(&mut self, k: K, v: V) -> &mut Self {
        self.check();
        self.map = self.map.assoc_value(k, v);
        self
    }
    pub fn dissoc(&mut self, k: &K) -> &mut Self {
        self.check();
        self.map = self.map.dissoc_value(k);
        self
    }
}
impl<K, V> IMutable for Mutable<K, V> {}
impl<K: Clone + Ord, V: Clone> IToPersistent for Mutable<K, V> {
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
    #[test]
    fn tree_updates_slices_maps_empty_and_mutable_preserve_metadata() {
        use crate::lang::protocol::{IEmpty, IMetadata, IToMutable, IToPersistent};
        use std::rc::Rc;
        let map = [(1, 10), (2, 20), (3, 30)]
            .into_iter()
            .collect::<Standard<_, _>>()
            .with_meta(Some(Rc::from("doc")));
        assert_eq!(
            map.assoc_value(4, 40).meta().map(|m| m.as_ref()),
            Some("doc")
        );
        assert_eq!(map.dissoc_value(&1).meta().map(|m| m.as_ref()), Some("doc"));
        assert_eq!(map.slice(&1, &2).meta().map(|m| m.as_ref()), Some("doc"));
        assert_eq!(
            map.map_values(|_, value| value + 1)
                .meta()
                .map(|m| m.as_ref()),
            Some("doc")
        );
        assert_eq!(map.empty().meta().map(|m| m.as_ref()), Some("doc"));
        let mut mutable = map.to_mutable();
        mutable.assoc(4, 40);
        assert_eq!(
            mutable.to_persistent().meta().map(|m| m.as_ref()),
            Some("doc")
        );
    }

    #[test]
    fn stays_sorted_indexed_and_persistent() {
        let a = [(5, "e"), (1, "a"), (3, "c"), (2, "b"), (4, "d")]
            .into_iter()
            .collect::<Standard<_, _>>();
        assert_eq!(
            a.iter().map(|(k, _)| *k).collect::<Vec<_>>(),
            vec![1, 2, 3, 4, 5]
        );
        assert_eq!(a.index_of_key(&3), Some(2));
        assert_eq!(a.nth_entry(2).map(|node| node.key), Some(3));
        assert_eq!(a.inclusive_floor_index(&0), None);
        assert_eq!(a.inclusive_floor_index(&6), Some(4));
        assert_eq!(a.ceil_index(&0), Some(0));
        let b = a.dissoc_value(&3);
        assert!(b.get(&3).is_none());
        assert!(a.get(&3).is_some());
    }
}
