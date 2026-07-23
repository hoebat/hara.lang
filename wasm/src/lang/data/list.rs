use std::rc::Rc;

use crate::lang::protocol::{
    IAssoc, IConj, ICons, ICount, IEmpty, IMetadata, IMutable, INth, IPeekFirst, IPeekLast,
    IPersistent, IPopFirst, IPopLast, IPushFirst, IPushLast, IToMutable, IToPersistent,
};

const CHUNK_SIZE: usize = 32;

#[derive(Debug, Clone)]
struct Chunk<E> {
    values: Rc<Vec<E>>,
    next: Option<Rc<Chunk<E>>>,
}

#[derive(Debug, Clone)]
pub struct Standard<E> {
    metadata: Option<Rc<str>>,
    head: Option<Rc<Chunk<E>>>,
    size: usize,
}

impl<E> Default for Standard<E> {
    fn default() -> Self {
        Self {
            metadata: None,
            head: None,
            size: 0,
        }
    }
}

impl<E: Clone> Standard<E> {
    pub fn new() -> Self {
        Self::default()
    }
    pub fn len(&self) -> usize {
        self.size
    }
    pub fn is_empty(&self) -> bool {
        self.size == 0
    }

    pub fn push_first(&self, value: E) -> Self {
        match &self.head {
            Some(head) if head.values.len() < CHUNK_SIZE => {
                let mut values = Vec::with_capacity(head.values.len() + 1);
                values.push(value);
                values.extend(head.values.iter().cloned());
                Self {
                    metadata: self.metadata.clone(),
                    head: Some(Rc::new(Chunk {
                        values: Rc::new(values),
                        next: head.next.clone(),
                    })),
                    size: self.size + 1,
                }
            }
            _ => Self {
                metadata: self.metadata.clone(),
                head: Some(Rc::new(Chunk {
                    values: Rc::new(vec![value]),
                    next: self.head.clone(),
                })),
                size: self.size + 1,
            },
        }
    }

    pub fn push_last(&self, value: E) -> Self {
        self.iter()
            .cloned()
            .chain(std::iter::once(value))
            .collect::<Self>()
            .with_meta(self.metadata.clone())
    }
    pub fn pop_first_value(&self) -> Self {
        let Some(head) = &self.head else {
            return self.clone();
        };
        if head.values.len() == 1 {
            Self {
                metadata: self.metadata.clone(),
                head: head.next.clone(),
                size: self.size - 1,
            }
        } else {
            Self {
                metadata: self.metadata.clone(),
                head: Some(Rc::new(Chunk {
                    values: Rc::new(head.values[1..].to_vec()),
                    next: head.next.clone(),
                })),
                size: self.size - 1,
            }
        }
    }
    pub fn pop_last_value(&self) -> Self {
        self.iter()
            .take(self.size.saturating_sub(1))
            .cloned()
            .collect::<Self>()
            .with_meta(self.metadata.clone())
    }
    pub fn get(&self, index: usize) -> Option<&E> {
        if index >= self.size {
            return None;
        }
        let mut offset = index;
        let mut chunk = self.head.as_deref();
        while let Some(current) = chunk {
            if offset < current.values.len() {
                return current.values.get(offset);
            }
            offset -= current.values.len();
            chunk = current.next.as_deref();
        }
        None
    }
    pub fn iter(&self) -> Iter<'_, E> {
        Iter {
            chunk: self.head.as_deref(),
            index: 0,
        }
    }
}

pub struct Iter<'a, E> {
    chunk: Option<&'a Chunk<E>>,
    index: usize,
}
impl<'a, E> Iterator for Iter<'a, E> {
    type Item = &'a E;
    fn next(&mut self) -> Option<Self::Item> {
        loop {
            let chunk = self.chunk?;
            if let Some(value) = chunk.values.get(self.index) {
                self.index += 1;
                return Some(value);
            }
            self.chunk = chunk.next.as_deref();
            self.index = 0;
        }
    }
}

impl<E: Clone> FromIterator<E> for Standard<E> {
    fn from_iter<T: IntoIterator<Item = E>>(iter: T) -> Self {
        let values = iter.into_iter().collect::<Vec<_>>();
        values
            .into_iter()
            .rev()
            .fold(Self::new(), |list, value| list.push_first(value))
    }
}
impl<E: Clone> From<Vec<E>> for Standard<E> {
    fn from(values: Vec<E>) -> Self {
        values.into_iter().collect()
    }
}
impl<E: Clone> IntoIterator for Standard<E> {
    type Item = E;
    type IntoIter = std::vec::IntoIter<E>;
    fn into_iter(self) -> Self::IntoIter {
        self.iter().cloned().collect::<Vec<_>>().into_iter()
    }
}
impl<'a, E: Clone> IntoIterator for &'a Standard<E> {
    type Item = &'a E;
    type IntoIter = Iter<'a, E>;
    fn into_iter(self) -> Self::IntoIter {
        self.iter()
    }
}
impl<E: Clone> std::ops::Index<usize> for Standard<E> {
    type Output = E;
    fn index(&self, index: usize) -> &E {
        self.get(index).expect("list index out of bounds")
    }
}
impl<E: Clone + PartialEq> PartialEq for Standard<E> {
    fn eq(&self, other: &Self) -> bool {
        self.size == other.size && self.iter().eq(other.iter())
    }
}
impl<E: Clone> ICount for Standard<E> {
    fn count(&self) -> usize {
        self.size
    }
}
impl<E: Clone> INth<E> for Standard<E> {
    fn nth(&self, index: usize) -> Option<&E> {
        self.get(index)
    }
}
impl<E: Clone> IPeekFirst<E> for Standard<E> {
    fn peek_first(&self) -> Option<E> {
        self.get(0).cloned()
    }
}
impl<E: Clone> IPeekLast<E> for Standard<E> {
    fn peek_last(&self) -> Option<E> {
        self.size
            .checked_sub(1)
            .and_then(|index| self.get(index))
            .cloned()
    }
}
impl<E: Clone> IPushFirst<E> for Standard<E> {
    type Output = Self;
    fn push_first(&self, value: E) -> Self {
        Standard::push_first(self, value)
    }
}
impl<E: Clone> IPushLast<E> for Standard<E> {
    type Output = Self;
    fn push_last(&self, value: E) -> Self {
        Standard::push_last(self, value)
    }
}
impl<E: Clone> IPopFirst for Standard<E> {
    type Output = Self;
    fn pop_first(&self) -> Self {
        self.pop_first_value()
    }
}
impl<E: Clone> IPopLast for Standard<E> {
    type Output = Self;
    fn pop_last(&self) -> Self {
        self.pop_last_value()
    }
}
impl<E: Clone> ICons<E> for Standard<E> {
    type Output = Self;
    fn cons(&self, value: E) -> Self {
        self.push_first(value)
    }
}
impl<E: Clone> IConj<E> for Standard<E> {
    type Output = Self;
    fn conj(&self, value: E) -> Self {
        self.push_last(value)
    }
}
impl<E: Clone> IEmpty for Standard<E> {
    type Output = Self;
    fn empty(&self) -> Self {
        Self::new().with_meta(self.metadata.clone())
    }
}
impl<E: Clone> IAssoc<usize, E> for Standard<E> {
    type Output = Self;
    fn assoc(&self, index: usize, value: E) -> Self {
        self.iter()
            .cloned()
            .enumerate()
            .map(|(i, v)| if i == index { value.clone() } else { v })
            .collect::<Self>()
            .with_meta(self.metadata.clone())
    }
}
impl<E: Clone> IMetadata for Standard<E> {
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
impl<E: Clone> IPersistent for Standard<E> {}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct Mutable<E> {
    values: Vec<E>,
}
impl<E> Mutable<E> {
    pub fn new() -> Self {
        Self { values: Vec::new() }
    }
    pub fn len(&self) -> usize {
        self.values.len()
    }
    pub fn is_empty(&self) -> bool {
        self.values.is_empty()
    }
    pub fn get(&self, index: usize) -> Option<&E> {
        self.values.get(index)
    }
    pub fn iter(&self) -> std::slice::Iter<'_, E> {
        self.values.iter()
    }
    pub fn push_first(&mut self, value: E) -> &mut Self {
        self.values.insert(0, value);
        self
    }
    pub fn push_last(&mut self, value: E) -> &mut Self {
        self.values.push(value);
        self
    }
    pub fn pop_first(&mut self) -> Option<E> {
        (!self.values.is_empty()).then(|| self.values.remove(0))
    }
    pub fn pop_last(&mut self) -> Option<E> {
        self.values.pop()
    }
    pub fn assoc(&mut self, index: usize, value: E) -> Option<E> {
        self.values
            .get_mut(index)
            .map(|slot| std::mem::replace(slot, value))
    }
    pub fn empty(&mut self) -> &mut Self {
        self.values.clear();
        self
    }
}
impl<E> FromIterator<E> for Mutable<E> {
    fn from_iter<T: IntoIterator<Item = E>>(iter: T) -> Self {
        Self {
            values: iter.into_iter().collect(),
        }
    }
}
impl<E> IMutable for Mutable<E> {}
impl<E: Clone> IToPersistent for Mutable<E> {
    type Persistent = Standard<E>;
    fn to_persistent(&mut self) -> Self::Persistent {
        self.values.iter().cloned().collect()
    }
}
impl<E: Clone> IToMutable for Standard<E> {
    type Mutable = Mutable<E>;
    fn to_mutable(&self) -> Self::Mutable {
        self.iter().cloned().collect()
    }
}

#[cfg(test)]
mod tests {
    use super::Standard;
    #[test]
    fn chunk_boundaries_and_persistence() {
        let list = (0..65).collect::<Standard<_>>();
        let prefixed = list.push_first(-1);
        let appended = list.push_last(65);
        assert_eq!(list.len(), 65);
        assert_eq!(list[0], 0);
        assert_eq!(prefixed[0], -1);
        assert_eq!(appended[65], 65);
    }
    #[test]
    fn persistent_operations_preserve_metadata() {
        use crate::lang::protocol::{IAssoc, IEmpty, IMetadata};
        use std::rc::Rc;
        let list = Standard::from(vec![1, 2, 3]).with_meta(Some(Rc::from("doc")));
        let values = [
            list.push_first(0),
            list.push_last(4),
            list.pop_first_value(),
            list.pop_last_value(),
            list.assoc(1, 20),
            list.empty(),
        ];
        assert!(values
            .iter()
            .all(|value| value.meta().map(|m| m.as_ref()) == Some("doc")));
    }

    #[test]
    fn mutable_round_trip_preserves_original_and_updates_edges() {
        use crate::lang::protocol::{IToMutable, IToPersistent};
        let original = (0..65).collect::<Standard<_>>();
        let mut mutable = original.to_mutable();
        assert_eq!(mutable.assoc(32, 320), Some(32));
        mutable.push_first(-1).push_last(65);
        assert_eq!(mutable.pop_first(), Some(-1));
        assert_eq!(mutable.pop_last(), Some(65));
        let persistent = mutable.to_persistent();
        assert_eq!(persistent.get(32), Some(&320));
        assert_eq!(original.get(32), Some(&32));
    }
}
