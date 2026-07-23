use std::cell::Cell;
use std::hash::Hash;

use crate::lang::data::{Map, Vector};
use crate::lang::protocol::{
    IConj, ICount, IDissoc, IEmpty, IFind, IMetadata, IMutable, INth, IPersistent, IToMutable,
    IToPersistent,
};

const COMPACT_MINIMUM: usize = 32;

#[derive(Debug, Clone)]
pub struct Standard<E> {
    lookup: Map<E, usize>,
    order: Vector<Option<E>>,
}
impl<E: Clone + Eq + Hash> Default for Standard<E> {
    fn default() -> Self {
        Self {
            lookup: Map::new(),
            order: Vector::new(),
        }
    }
}
impl<E: Clone + Eq + Hash> Standard<E> {
    pub fn new() -> Self {
        Self::default()
    }
    pub fn len(&self) -> usize {
        self.lookup.len()
    }
    pub fn is_empty(&self) -> bool {
        self.lookup.is_empty()
    }
    pub fn get(&self, value: &E) -> Option<&E> {
        self.lookup.find_entry(value).map(|(stored, _)| stored)
    }
    pub fn iter(&self) -> impl Iterator<Item = &E> {
        self.order.iter().filter_map(Option::as_ref)
    }
    pub fn conj_value(&self, value: E) -> Self {
        if self.lookup.get(&value).is_some() {
            return self.clone();
        }
        let index = self.order.len();
        Self {
            lookup: self.lookup.assoc_value(value.clone(), index),
            order: self.order.push_last(Some(value)),
        }
    }
    pub fn dissoc_value(&self, value: &E) -> Self {
        let Some(index) = self.lookup.get(value) else {
            return self.clone();
        };
        let set = Self {
            lookup: self.lookup.dissoc_value(value),
            order: self
                .order
                .assoc_value(*index, None)
                .expect("ordered-set index"),
        };
        set.compact_if_sparse()
    }
    fn compact_if_sparse(self) -> Self {
        if self.order.len() <= COMPACT_MINIMUM || self.order.len() <= 2 * self.lookup.len() {
            return self;
        }
        self.iter()
            .cloned()
            .collect::<Self>()
            .with_meta(self.lookup.meta().cloned())
    }
}
impl<E: Clone + Eq + Hash> FromIterator<E> for Standard<E> {
    fn from_iter<T: IntoIterator<Item = E>>(iter: T) -> Self {
        iter.into_iter()
            .fold(Self::new(), |set, value| set.conj_value(value))
    }
}
impl<E: Clone + Eq + Hash> ICount for Standard<E> {
    fn count(&self) -> usize {
        self.len()
    }
}
impl<E: Clone + Eq + Hash> IFind<E> for Standard<E> {
    type Output = E;
    fn find(&self, key: &E) -> Option<E> {
        self.get(key).cloned()
    }
}
impl<E: Clone + Eq + Hash> IConj<E> for Standard<E> {
    fn conj(&self, value: E) -> Self {
        self.conj_value(value)
    }
}
impl<E: Clone + Eq + Hash> IDissoc<E> for Standard<E> {
    fn dissoc(&self, key: &E) -> Self {
        self.dissoc_value(key)
    }
}
impl<E: Clone + Eq + Hash> INth<E> for Standard<E> {
    fn nth(&self, index: usize) -> Option<&E> {
        self.iter().nth(index)
    }
}
impl<E: Clone + Eq + Hash> IEmpty for Standard<E> {
    fn empty(&self) -> Self {
        Self::new().with_meta(self.lookup.meta().cloned())
    }
}
impl<E: Clone + Eq + Hash> IMetadata for Standard<E> {
    type Metadata = std::rc::Rc<str>;
    fn meta(&self) -> Option<&Self::Metadata> {
        self.lookup.meta()
    }
    fn with_meta(&self, metadata: Option<Self::Metadata>) -> Self {
        Self {
            lookup: self.lookup.with_meta(metadata.clone()),
            order: self.order.with_meta(metadata),
        }
    }
}
impl<E: Clone + Eq + Hash> IPersistent for Standard<E> {}
impl<E: Clone + Eq + Hash> IToMutable for Standard<E> {
    type Mutable = Mutable<E>;
    fn to_mutable(&self) -> Self::Mutable {
        Mutable {
            editable: Cell::new(true),
            set: self.clone(),
        }
    }
}

#[derive(Debug, Clone)]
pub struct Mutable<E> {
    editable: Cell<bool>,
    set: Standard<E>,
}
impl<E: Clone + Eq + Hash> Mutable<E> {
    fn check(&self) {
        assert!(
            self.editable.get(),
            "mutable ordered set used after to_persistent"
        );
    }
    pub fn conj(&mut self, value: E) -> &mut Self {
        self.check();
        self.set = self.set.conj_value(value);
        self
    }
    pub fn dissoc(&mut self, value: &E) -> &mut Self {
        self.check();
        self.set = self.set.dissoc_value(value);
        self
    }
}
impl<E> IMutable for Mutable<E> {}
impl<E: Clone + Eq + Hash> IToPersistent for Mutable<E> {
    type Persistent = Standard<E>;
    fn to_persistent(&mut self) -> Self::Persistent {
        self.check();
        self.editable.set(false);
        self.set.clone()
    }
}

#[cfg(test)]
mod tests {
    use super::Standard;
    #[test]
    fn compaction_and_empty_preserve_metadata() {
        use crate::lang::protocol::{IEmpty, IMetadata};
        use std::rc::Rc;
        let original = (0..80)
            .collect::<Standard<_>>()
            .with_meta(Some(Rc::from("doc")));
        let reduced = (0..60).fold(original, |set, value| set.dissoc_value(&value));
        assert_eq!(reduced.meta().map(|m| m.as_ref()), Some("doc"));
        assert_eq!(reduced.empty().meta().map(|m| m.as_ref()), Some("doc"));
    }

    #[test]
    fn is_unique_ordered_and_persistent() {
        let original = [3, 1, 3, 2].into_iter().collect::<Standard<_>>();
        let reduced = original.dissoc_value(&1);
        assert_eq!(original.iter().copied().collect::<Vec<_>>(), vec![3, 1, 2]);
        assert_eq!(reduced.iter().copied().collect::<Vec<_>>(), vec![3, 2]);
    }
}
