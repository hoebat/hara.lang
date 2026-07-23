use crate::lang::data::{List, Vector};
use crate::lang::protocol::{
    IConj, ICount, IEmpty, INth, IPersistent, IPopFirst, IPopLast, IPushLast,
};

pub const MAX_LENGTH: usize = 1024;

#[derive(Debug, Clone)]
pub struct Standard<E> {
    size: usize,
    offset: usize,
    head: Vector<E>,
    tail: Vector<E>,
    buffer: List<Vector<E>>,
}

impl<E: Clone> Default for Standard<E> {
    fn default() -> Self {
        Self {
            size: 0,
            offset: 0,
            head: Vector::new(),
            tail: Vector::new(),
            buffer: List::new(),
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
    pub fn peek_first(&self) -> Option<&E> {
        if self.size == 0 {
            None
        } else {
            self.head.get(self.offset)
        }
    }
    pub fn peek_last(&self) -> Option<&E> {
        if self.size == 0 {
            None
        } else if !self.tail.is_empty() {
            self.tail.get(self.tail.len() - 1)
        } else if !self.buffer.is_empty() {
            self.buffer
                .get(self.buffer.len() - 1)
                .and_then(|v| v.get(v.len() - 1))
        } else {
            self.head.get(self.head.len() - 1)
        }
    }
    pub fn get(&self, index: usize) -> Option<&E> {
        if index >= self.size {
            return None;
        }
        let head_remaining = self.head.len().saturating_sub(self.offset);
        if index < head_remaining {
            return self.head.get(self.offset + index);
        }
        let after = index - head_remaining;
        let chunk = after / MAX_LENGTH;
        let column = after % MAX_LENGTH;
        if chunk < self.buffer.len() {
            self.buffer.get(chunk).and_then(|v| v.get(column))
        } else {
            self.tail.get(column)
        }
    }
    pub fn push_last(&self, value: E) -> Self {
        let space = MAX_LENGTH - self.offset;
        if self.size < space {
            return Self {
                size: self.size + 1,
                head: self.head.push_last(value),
                ..self.clone()
            };
        }
        let tail = self.tail.push_last(value);
        if tail.len() < MAX_LENGTH {
            Self {
                size: self.size + 1,
                tail,
                ..self.clone()
            }
        } else {
            Self {
                size: self.size + 1,
                tail: Vector::new(),
                buffer: self.buffer.push_last(tail),
                ..self.clone()
            }
        }
    }
    pub fn pop_first_value(&self) -> Self {
        if self.size == 0 {
            return self.clone();
        }
        let offset = self.offset + 1;
        if offset < MAX_LENGTH {
            return Self {
                size: self.size - 1,
                offset,
                ..self.clone()
            };
        }
        if self.buffer.is_empty() {
            Self {
                size: self.size - 1,
                offset: 0,
                head: self.tail.clone(),
                tail: Vector::new(),
                buffer: self.buffer.clone(),
            }
        } else {
            Self {
                size: self.size - 1,
                offset: 0,
                head: self.buffer[0].clone(),
                buffer: self.buffer.pop_first_value(),
                ..self.clone()
            }
        }
    }
    pub fn pop_last_value(&self) -> Self {
        if self.size == 0 {
            return self.clone();
        }
        if !self.tail.is_empty() {
            Self {
                size: self.size - 1,
                tail: self.tail.pop_last_value().expect("nonempty tail"),
                ..self.clone()
            }
        } else if self.buffer.is_empty() {
            Self {
                size: self.size - 1,
                head: self.head.pop_last_value().unwrap_or_else(Vector::new),
                ..self.clone()
            }
        } else {
            Self {
                size: self.size - 1,
                tail: self.buffer[self.buffer.len() - 1].clone(),
                buffer: self.buffer.pop_last_value(),
                ..self.clone()
            }
        }
    }
    pub fn iter(&self) -> impl Iterator<Item = &E> {
        (0..self.size).map(|i| self.get(i).expect("valid queue index"))
    }
}
impl<E: Clone> FromIterator<E> for Standard<E> {
    fn from_iter<T: IntoIterator<Item = E>>(iter: T) -> Self {
        iter.into_iter().fold(Self::new(), |q, v| q.push_last(v))
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
impl<E: Clone> IPushLast<E> for Standard<E> {
    fn push_last(&self, value: E) -> Self {
        Standard::push_last(self, value)
    }
}
impl<E: Clone> IConj<E> for Standard<E> {
    fn conj(&self, value: E) -> Self {
        self.push_last(value)
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
impl<E: Clone> IEmpty for Standard<E> {
    fn empty(&self) -> Self {
        Self::new()
    }
}
impl<E: Clone> IPersistent for Standard<E> {}

#[cfg(test)]
mod tests {
    use super::{Standard, MAX_LENGTH};
    #[test]
    fn crosses_head_buffer_tail_boundaries() {
        let q = (0..(MAX_LENGTH * 2 + 9)).collect::<Standard<_>>();
        assert_eq!(q.get(0), Some(&0));
        assert_eq!(q.get(MAX_LENGTH), Some(&MAX_LENGTH));
        assert_eq!(q.peek_last(), Some(&(MAX_LENGTH * 2 + 8)));
        let p = q.pop_first_value();
        assert_eq!(p.peek_first(), Some(&1));
        assert_eq!(q.peek_first(), Some(&0));
    }
    #[test]
    fn pop_last_preserves_previous() {
        let q = (0..1030).collect::<Standard<_>>();
        let p = q.pop_last_value();
        assert_eq!(q.peek_last(), Some(&1029));
        assert_eq!(p.peek_last(), Some(&1028));
    }
}
