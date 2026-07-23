use crate::lang::data::List;
use crate::lang::protocol::{ICons, ICount, IEmpty, INth, IPersistent, IPopFirst, IPushFirst};

#[derive(Debug, Clone)]
pub struct Cons<E> {
    first: E,
    more: List<E>,
}

impl<E: Clone + PartialEq> PartialEq for Cons<E> {
    fn eq(&self, other: &Self) -> bool {
        self.first == other.first && self.more == other.more
    }
}
impl<E: Clone> Cons<E> {
    pub fn new(first: E, more: List<E>) -> Self {
        Self { first, more }
    }
    pub fn peek_first(&self) -> &E {
        &self.first
    }
    pub fn iter(&self) -> impl Iterator<Item = &E> {
        std::iter::once(&self.first).chain(self.more.iter())
    }
    pub fn to_list(&self) -> List<E> {
        self.iter().cloned().collect()
    }
}
impl<E: Clone> ICount for Cons<E> {
    fn count(&self) -> usize {
        1 + self.more.len()
    }
}
impl<E: Clone> INth<E> for Cons<E> {
    fn nth(&self, index: usize) -> Option<&E> {
        if index == 0 {
            Some(&self.first)
        } else {
            self.more.get(index - 1)
        }
    }
}
impl<E: Clone> IPushFirst<E> for Cons<E> {
    fn push_first(&self, value: E) -> Self {
        Self::new(value, self.to_list())
    }
}
impl<E: Clone> ICons<E> for Cons<E> {
    fn cons(&self, value: E) -> Self {
        self.push_first(value)
    }
}
impl<E: Clone> IPopFirst for Cons<E> {
    fn pop_first(&self) -> Self {
        let list = self.more.clone();
        Self::new(
            list.get(0).expect("cannot represent empty Cons").clone(),
            list.pop_first_value(),
        )
    }
}
impl<E: Clone> IEmpty for Cons<E> {
    fn empty(&self) -> Self {
        panic!("Cons empty is Tuple.Tup0 in the Java contract")
    }
}
impl<E: Clone> IPersistent for Cons<E> {}

#[cfg(test)]
mod tests {
    use super::Cons;
    use crate::lang::data::List;
    use crate::lang::protocol::{ICount, INth};
    #[test]
    fn linked_navigation() {
        let c = Cons::new(1, vec![2, 3].into_iter().collect::<List<_>>());
        assert_eq!(c.count(), 3);
        assert_eq!(c.nth(2), Some(&3));
    }
}
