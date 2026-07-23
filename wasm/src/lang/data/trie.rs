use std::cell::Cell;
use std::collections::BTreeMap;
use std::rc::Rc;

use crate::lang::protocol::{
    IAssoc, IConj, ICount, IDissoc, IEmpty, IFind, ILookup, IMutable, IPersistent, IToMutable,
    IToPersistent,
};

#[derive(Debug, Clone)]
struct Node<V> {
    children: BTreeMap<char, Rc<Node<V>>>,
    value: Option<V>,
}
impl<V> Default for Node<V> {
    fn default() -> Self {
        Self {
            children: BTreeMap::new(),
            value: None,
        }
    }
}
fn assoc_node<V: Clone>(node: &Rc<Node<V>>, chars: &[char], value: V) -> Rc<Node<V>> {
    if chars.is_empty() {
        return Rc::new(Node {
            children: node.children.clone(),
            value: Some(value),
        });
    }
    let mut children = node.children.clone();
    let child = children
        .get(&chars[0])
        .cloned()
        .unwrap_or_else(|| Rc::new(Node::default()));
    children.insert(chars[0], assoc_node(&child, &chars[1..], value));
    Rc::new(Node {
        children,
        value: node.value.clone(),
    })
}
fn dissoc_node<V: Clone>(node: &Rc<Node<V>>, chars: &[char]) -> Option<Rc<Node<V>>> {
    let mut children = node.children.clone();
    let value = if chars.is_empty() {
        None
    } else {
        let child = children.get(&chars[0])?;
        match dissoc_node(child, &chars[1..]) {
            Some(next) => {
                children.insert(chars[0], next);
            }
            None => {
                children.remove(&chars[0]);
            }
        }
        node.value.clone()
    };
    if value.is_none() && children.is_empty() {
        None
    } else {
        Some(Rc::new(Node { children, value }))
    }
}
#[derive(Debug, Clone)]
pub struct Standard<V> {
    root: Rc<Node<V>>,
    size: usize,
}
impl<V> Default for Standard<V> {
    fn default() -> Self {
        Self {
            root: Rc::new(Node::default()),
            size: 0,
        }
    }
}
impl<V: Clone> Standard<V> {
    pub fn new() -> Self {
        Self::default()
    }
    pub fn len(&self) -> usize {
        self.size
    }
    pub fn is_empty(&self) -> bool {
        self.size == 0
    }
    pub fn get(&self, key: &str) -> Option<&V> {
        let mut node = self.root.as_ref();
        for ch in key.chars() {
            node = node.children.get(&ch)?.as_ref();
        }
        node.value.as_ref()
    }
    pub fn assoc_value(&self, key: impl Into<String>, value: V) -> Self {
        let key = key.into();
        let added = self.get(&key).is_none();
        let chars = key.chars().collect::<Vec<_>>();
        Self {
            root: assoc_node(&self.root, &chars, value),
            size: self.size + usize::from(added),
        }
    }
    pub fn dissoc_value(&self, key: &str) -> Self {
        if self.get(key).is_none() {
            return self.clone();
        }
        let chars = key.chars().collect::<Vec<_>>();
        Self {
            root: dissoc_node(&self.root, &chars).unwrap_or_else(|| Rc::new(Node::default())),
            size: self.size - 1,
        }
    }
    pub fn entries(&self) -> Vec<(String, &V)> {
        fn collect<'a, V>(n: &'a Node<V>, prefix: &mut String, out: &mut Vec<(String, &'a V)>) {
            if let Some(v) = &n.value {
                out.push((prefix.clone(), v));
            }
            for (ch, child) in &n.children {
                prefix.push(*ch);
                collect(child, prefix, out);
                prefix.pop();
            }
        }
        let mut out = Vec::with_capacity(self.size);
        collect(&self.root, &mut String::new(), &mut out);
        out
    }
    pub fn iter(&self) -> impl Iterator<Item = String> + '_ {
        self.entries().into_iter().map(|(key, _)| key)
    }
}
impl<V: Clone> ICount for Standard<V> {
    fn count(&self) -> usize {
        self.len()
    }
}
impl<V: Clone> IFind<String> for Standard<V> {
    type Output = (String, V);
    fn find(&self, key: &String) -> Option<Self::Output> {
        self.get(key).map(|v| (key.clone(), v.clone()))
    }
}
impl<V: Clone> ILookup<String, V> for Standard<V> {
    type Keys = std::vec::IntoIter<String>;
    type Values = std::vec::IntoIter<V>;
    fn keys(&self) -> Self::Keys {
        self.iter().collect::<Vec<_>>().into_iter()
    }
    fn vals(&self) -> Self::Values {
        self.entries()
            .into_iter()
            .map(|(_, v)| v.clone())
            .collect::<Vec<_>>()
            .into_iter()
    }
}
impl<V: Clone> IAssoc<String, V> for Standard<V> {
    fn assoc(&self, k: String, v: V) -> Self {
        self.assoc_value(k, v)
    }
}
impl<V: Clone> IDissoc<String> for Standard<V> {
    fn dissoc(&self, k: &String) -> Self {
        self.dissoc_value(k)
    }
}
impl<V: Clone + Default> IConj<String> for Standard<V> {
    fn conj(&self, k: String) -> Self {
        self.assoc_value(k, V::default())
    }
}
impl<V: Clone> IEmpty for Standard<V> {
    fn empty(&self) -> Self {
        Self::new()
    }
}
impl<V: Clone> IPersistent for Standard<V> {}
impl<V: Clone> IToMutable for Standard<V> {
    type Mutable = Mutable<V>;
    fn to_mutable(&self) -> Self::Mutable {
        Mutable {
            editable: Cell::new(true),
            trie: self.clone(),
        }
    }
}
#[derive(Debug, Clone)]
pub struct Mutable<V> {
    editable: Cell<bool>,
    trie: Standard<V>,
}
impl<V: Clone> Mutable<V> {
    fn check(&self) {
        assert!(self.editable.get(), "mutable trie used after to_persistent")
    }
    pub fn assoc(&mut self, k: impl Into<String>, v: V) -> &mut Self {
        self.check();
        self.trie = self.trie.assoc_value(k, v);
        self
    }
    pub fn dissoc(&mut self, k: &str) -> &mut Self {
        self.check();
        self.trie = self.trie.dissoc_value(k);
        self
    }
}
impl<V> IMutable for Mutable<V> {}
impl<V: Clone> IToPersistent for Mutable<V> {
    type Persistent = Standard<V>;
    fn to_persistent(&mut self) -> Self::Persistent {
        self.check();
        self.editable.set(false);
        self.trie.clone()
    }
}
#[cfg(test)]
mod tests {
    use super::Standard;
    #[test]
    fn shares_prefixes_and_iterates_lexically() {
        let a = Standard::new()
            .assoc_value("car", 1)
            .assoc_value("cat", 2)
            .assoc_value("dog", 3);
        assert_eq!(a.iter().collect::<Vec<_>>(), vec!["car", "cat", "dog"]);
        let b = a.dissoc_value("cat");
        assert_eq!(b.get("cat"), None);
        assert_eq!(a.get("cat"), Some(&2));
        assert_eq!(b.get("car"), Some(&1));
    }
}
