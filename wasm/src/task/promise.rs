use std::cell::RefCell;
use std::rc::Rc;

use crate::core::Value;

#[derive(Debug, Clone, PartialEq)]
pub enum PromiseState {
    Pending,
    Fulfilled(Value),
    Rejected(String),
}

#[derive(Clone)]
pub struct Promise {
    pub(crate) state: Rc<RefCell<PromiseState>>,
    continuations: Rc<RefCell<Vec<Rc<dyn Fn(PromiseState)>>>>,
}

impl std::fmt::Debug for Promise {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter
            .debug_struct("Promise")
            .field("state", &self.state())
            .finish()
    }
}

impl Default for Promise {
    fn default() -> Self {
        Self::new()
    }
}

impl Promise {
    pub fn new() -> Self {
        Self {
            state: Rc::new(RefCell::new(PromiseState::Pending)),
            continuations: Rc::new(RefCell::new(Vec::new())),
        }
    }

    pub fn state(&self) -> PromiseState {
        self.state.borrow().clone()
    }

    pub fn resolve(&self, value: Value) -> bool {
        self.settle(PromiseState::Fulfilled(value))
    }

    pub fn reject(&self, error: impl Into<String>) -> bool {
        self.settle(PromiseState::Rejected(error.into()))
    }

    fn settle(&self, next: PromiseState) -> bool {
        let continuations = {
            let mut state = self.state.borrow_mut();
            if !matches!(*state, PromiseState::Pending) {
                return false;
            }
            *state = next.clone();
            std::mem::take(&mut *self.continuations.borrow_mut())
        };
        for continuation in continuations {
            continuation(next.clone());
        }
        true
    }

    pub fn on_settle(&self, continuation: Rc<dyn Fn(PromiseState)>) {
        let state = self.state();
        if matches!(state, PromiseState::Pending) {
            self.continuations.borrow_mut().push(continuation);
        } else {
            continuation(state);
        }
    }

    pub fn adopt(&self, other: &Promise) -> bool {
        match other.state() {
            PromiseState::Pending => {
                if !matches!(self.state(), PromiseState::Pending) {
                    return false;
                }
                let destination = self.clone();
                other.on_settle(Rc::new(move |state| match state {
                    PromiseState::Fulfilled(value) => {
                        destination.resolve(value);
                    }
                    PromiseState::Rejected(error) => {
                        destination.reject(error);
                    }
                    PromiseState::Pending => {}
                }));
                true
            }
            PromiseState::Fulfilled(value) => self.resolve(value),
            PromiseState::Rejected(error) => self.reject(error),
        }
    }

    pub fn same_identity(&self, other: &Self) -> bool {
        Rc::ptr_eq(&self.state, &other.state)
    }

    pub fn identity_address(&self) -> *const RefCell<PromiseState> {
        Rc::as_ptr(&self.state)
    }
}
