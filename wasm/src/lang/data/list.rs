use std::rc::Rc;

use crate::lang::protocol::{
    IAssoc, IConj, ICons, ICount, IEmpty, INth, IPersistent, IPopFirst, IPopLast, IPushFirst,
    IPushLast,
};

const CHUNK_SIZE: usize = 32;

#[derive(Debug, Clone)]
struct Chunk<E> {
    values: Rc<Vec<E>>,
    next: Option<Rc<Chunk<E>>>,
}

#[derive(Debug, Clone)]
pub struct Standard<E> {
    head: Option<Rc<Chunk<E>>>,
    size: usize,
}

impl<E> Default for Standard<E> {
    fn default() -> Self {
        Self {
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
                    head: Some(Rc::new(Chunk {
                        values: Rc::new(values),
                        next: head.next.clone(),
                    })),
                    size: self.size + 1,
                }
            }
            _ => Self {
                head: Some(Rc::new(Chunk {
                    values: Rc::new(vec![value]),
                    next: self.head.clone(),
                })),
                size: self.size + 1,
            },
        }
    }

    pub fn push_last(&self, value: E) -> Self {
        self.iter().cloned().chain(std::iter::once(value)).collect()
    }
    pub fn pop_first_value(&self) -> Self {
        let Some(head) = &self.head else {
            return self.clone();
        };
        if head.values.len() == 1 {
            Self {
                head: head.next.clone(),
                size: self.size - 1,
            }
        } else {
            Self {
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
            .collect()
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
impl<E: Clone> IPushFirst<E> for Standard<E> {
    fn push_first(&self, value: E) -> Self {
        Standard::push_first(self, value)
    }
}
impl<E: Clone> IPushLast<E> for Standard<E> {
    fn push_last(&self, value: E) -> Self {
        Standard::push_last(self, value)
    }
}
impl<E: Clone> IPopFirst for Standard<E> {
    fn pop_first(&self) -> Self {
        self.pop_first_value()
    }
}
impl<E: Clone> IPopLast for Standard<E> {
    fn pop_last(&self) -> Self {
        self.pop_last_value()
    }
}
impl<E: Clone> ICons<E> for Standard<E> {
    fn cons(&self, value: E) -> Self {
        self.push_first(value)
    }
}
impl<E: Clone> IConj<E> for Standard<E> {
    fn conj(&self, value: E) -> Self {
        self.push_last(value)
    }
}
impl<E: Clone> IEmpty for Standard<E> {
    fn empty(&self) -> Self {
        Self::new()
    }
}
impl<E: Clone> IAssoc<usize, E> for Standard<E> {
    fn assoc(&self, index: usize, value: E) -> Self {
        self.iter()
            .cloned()
            .enumerate()
            .map(|(i, v)| if i == index { value.clone() } else { v })
            .collect()
    }
}
impl<E: Clone> IPersistent for Standard<E> {}

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
}
