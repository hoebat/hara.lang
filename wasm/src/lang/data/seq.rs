use std::cell::RefCell;
use std::rc::Rc;

#[derive(Clone)]
pub struct Seq<E> {
    state: Rc<RefCell<State<E>>>,
    offset: usize,
}
struct State<E> {
    source: Option<Box<dyn Iterator<Item = E>>>,
    realized: Vec<E>,
}

impl<E: Clone + 'static> Seq<E> {
    pub fn new(source: impl Iterator<Item = E> + 'static) -> Self {
        Self {
            state: Rc::new(RefCell::new(State {
                source: Some(Box::new(source)),
                realized: Vec::new(),
            })),
            offset: 0,
        }
    }
    fn realize(&self, index: usize) -> bool {
        let mut state = self.state.borrow_mut();
        while state.realized.len() <= index {
            let next = state.source.as_mut().and_then(Iterator::next);
            match next {
                Some(v) => state.realized.push(v),
                None => {
                    state.source = None;
                    return false;
                }
            }
        }
        true
    }
    pub fn peek_first(&self) -> Option<E> {
        self.realize(self.offset)
            .then(|| self.state.borrow().realized[self.offset].clone())
    }
    pub fn pop_first(&self) -> Self {
        Self {
            state: self.state.clone(),
            offset: self.offset + usize::from(self.peek_first().is_some()),
        }
    }
    pub fn count(&self) -> usize {
        let mut state = self.state.borrow_mut();
        while let Some(v) = state.source.as_mut().and_then(Iterator::next) {
            state.realized.push(v)
        }
        state.source = None;
        state.realized.len().saturating_sub(self.offset)
    }
    pub fn iter(&self) -> SeqIter<E> {
        SeqIter {
            seq: self.clone(),
            index: 0,
        }
    }
}
pub struct SeqIter<E> {
    seq: Seq<E>,
    index: usize,
}
impl<E: Clone + 'static> Iterator for SeqIter<E> {
    type Item = E;
    fn next(&mut self) -> Option<E> {
        let index = self.seq.offset + self.index;
        if !self.seq.realize(index) {
            return None;
        }
        self.index += 1;
        Some(self.seq.state.borrow().realized[index].clone())
    }
}
impl<E> std::fmt::Debug for Seq<E> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Seq")
            .field("offset", &self.offset)
            .finish_non_exhaustive()
    }
}

#[cfg(test)]
mod tests {
    use super::Seq;
    use std::cell::Cell;
    use std::rc::Rc;
    #[test]
    fn realizes_once_and_shares_state() {
        let calls = Rc::new(Cell::new(0));
        let c = calls.clone();
        let seq = Seq::new((0..3).map(move |v| {
            c.set(c.get() + 1);
            v
        }));
        assert_eq!(seq.peek_first(), Some(0));
        assert_eq!(seq.peek_first(), Some(0));
        assert_eq!(calls.get(), 1);
        assert_eq!(seq.pop_first().iter().collect::<Vec<_>>(), vec![1, 2]);
    }
}
