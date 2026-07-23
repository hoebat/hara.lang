use std::cell::Cell;
use std::hash::Hash;

use crate::lang::data::{Map, Vector};
use crate::lang::protocol::{
    IAssoc, ICount, IDissoc, IEmpty, IFind, ILookup, IMutable, INth, IPersistent, IToMutable,
    IToPersistent,
};

const COMPACT_MINIMUM: usize = 32;

#[derive(Debug, Clone)]
pub struct Standard<K, V> {
    lookup: Map<K, (usize, V)>,
    order: Vector<Option<(K, V)>>,
}

impl<K: Clone + Eq + Hash, V: Clone> Default for Standard<K, V> {
    fn default() -> Self {
        Self {
            lookup: Map::new(),
            order: Vector::new(),
        }
    }
}

impl<K: Clone + Eq + Hash, V: Clone> Standard<K, V> {
    pub fn new() -> Self {
        Self::default()
    }
    pub fn len(&self) -> usize {
        self.lookup.len()
    }
    pub fn is_empty(&self) -> bool {
        self.lookup.is_empty()
    }
    pub fn get(&self, key: &K) -> Option<&V> {
        self.lookup.get(key).map(|(_, value)| value)
    }
    pub fn find_entry(&self, key: &K) -> Option<(&K, &V)> {
        self.lookup
            .find_entry(key)
            .map(|(stored, (_, value))| (stored, value))
    }
    pub fn iter(&self) -> impl Iterator<Item = &(K, V)> {
        self.order.iter().filter_map(Option::as_ref)
    }
    pub fn assoc_value(&self, key: K, value: V) -> Self {
        match self.lookup.get(&key) {
            None => {
                let index = self.order.len();
                Self {
                    lookup: self.lookup.assoc_value(key.clone(), (index, value.clone())),
                    order: self.order.push_last(Some((key, value))),
                }
            }
            Some((index, _)) => Self {
                lookup: self
                    .lookup
                    .assoc_value(key.clone(), (*index, value.clone())),
                order: self
                    .order
                    .assoc_value(*index, Some((key, value)))
                    .expect("ordered-map index"),
            },
        }
    }
    pub fn dissoc_value(&self, key: &K) -> Self {
        let Some((index, _)) = self.lookup.get(key) else {
            return self.clone();
        };
        let map = Self {
            lookup: self.lookup.dissoc_value(key),
            order: self
                .order
                .assoc_value(*index, None)
                .expect("ordered-map index"),
        };
        map.compact_if_sparse()
    }
    fn compact_if_sparse(self) -> Self {
        if self.order.len() <= COMPACT_MINIMUM || self.order.len() <= 2 * self.lookup.len() {
            return self;
        }
        self.iter().cloned().collect()
    }
}

impl<K: Clone + Eq + Hash, V: Clone> FromIterator<(K, V)> for Standard<K, V> {
    fn from_iter<T: IntoIterator<Item = (K, V)>>(iter: T) -> Self {
        iter.into_iter()
            .fold(Self::new(), |map, (key, value)| map.assoc_value(key, value))
    }
}
impl<K: Clone + Eq + Hash, V: Clone> ICount for Standard<K, V> {
    fn count(&self) -> usize {
        self.len()
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
        self.iter()
            .map(|(key, _)| key.clone())
            .collect::<Vec<_>>()
            .into_iter()
    }
    fn vals(&self) -> Self::Values {
        self.iter()
            .map(|(_, value)| value.clone())
            .collect::<Vec<_>>()
            .into_iter()
    }
}
impl<K: Clone + Eq + Hash, V: Clone> IAssoc<K, V> for Standard<K, V> {
    fn assoc(&self, k: K, v: V) -> Self {
        self.assoc_value(k, v)
    }
}
impl<K: Clone + Eq + Hash, V: Clone> IDissoc<K> for Standard<K, V> {
    fn dissoc(&self, k: &K) -> Self {
        self.dissoc_value(k)
    }
}
impl<K: Clone + Eq + Hash, V: Clone> INth<(K, V)> for Standard<K, V> {
    fn nth(&self, i: usize) -> Option<&(K, V)> {
        self.iter().nth(i)
    }
}
impl<K: Clone + Eq + Hash, V: Clone> IEmpty for Standard<K, V> {
    fn empty(&self) -> Self {
        Self::new()
    }
}
impl<K: Clone + Eq + Hash, V: Clone> IPersistent for Standard<K, V> {}
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
        assert!(
            self.editable.get(),
            "mutable ordered map used after to_persistent"
        );
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
    #[test]
    fn replacement_keeps_position_and_deletion_compacts() {
        let map = Standard::new()
            .assoc_value("a", 1)
            .assoc_value("b", 2)
            .assoc_value("a", 3);
        assert_eq!(
            map.iter().cloned().collect::<Vec<_>>(),
            vec![("a", 3), ("b", 2)]
        );
        let original = (0..80).map(|n| (n, n)).collect::<Standard<_, _>>();
        let reduced = (0..60).fold(original.clone(), |map, key| map.dissoc_value(&key));
        assert_eq!(original.len(), 80);
        assert_eq!(
            reduced.iter().map(|(key, _)| *key).collect::<Vec<_>>(),
            (60..80).collect::<Vec<_>>()
        );
    }
}
