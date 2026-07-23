use crate::lang::data::{List, Tuple};
use crate::lang::protocol::{
    ICons, ICount, IEmpty, IMetadata, INth, IPersistent, IPopFirst, IPushFirst,
};
use std::rc::Rc;

#[derive(Debug, Clone)]
pub struct Cons<E> {
    metadata: Option<Rc<str>>,
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
        Self {
            metadata: None,
            first,
            more,
        }
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
        Self {
            metadata: self.metadata.clone(),
            first: value,
            more: self.to_list(),
        }
    }
}
impl<E: Clone> ICons<E> for Cons<E> {
    fn cons(&self, value: E) -> Self {
        Self::new(value, self.to_list())
    }
}
impl<E: Clone> IPopFirst for Cons<E> {
    type Output = List<E>;
    fn pop_first(&self) -> Self::Output {
        self.more.clone()
    }
}
impl<E: Clone> IEmpty for Cons<E> {
    type Output = Tuple<E>;
    fn empty(&self) -> Self::Output {
        Tuple::Tup0
    }
}
impl<E: Clone> IMetadata for Cons<E> {
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
impl<E: Clone> IPersistent for Cons<E> {}

#[cfg(test)]
mod tests {
    use super::Cons;
    use crate::lang::data::List;
    use crate::lang::protocol::{ICons, ICount, IMetadata, INth, IPopFirst, IPushFirst};
    use std::rc::Rc;
    #[test]
    fn linked_navigation() {
        let c = Cons::new(1, vec![2, 3].into_iter().collect::<List<_>>());
        assert_eq!(c.count(), 3);
        assert_eq!(c.nth(2), Some(&3));
        assert_eq!(c.pop_first(), List::from(vec![2, 3]));
        assert!(Cons::new(1, List::new()).pop_first().is_empty());

        let documented = c.with_meta(Some(Rc::from("doc")));
        assert_eq!(
            documented.push_first(0).meta().map(|m| m.as_ref()),
            Some("doc")
        );
        assert_eq!(documented.cons(0).meta(), None);
    }
}
