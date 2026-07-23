use std::cell::RefCell;
use std::rc::Rc;
use std::time::{Duration, Instant};

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
    deferred: Rc<RefCell<Option<(Instant, Rc<dyn Fn() -> Result<Value, String>>)>>>,
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
            deferred: Rc::new(RefCell::new(None)),
        }
    }

    pub fn state(&self) -> PromiseState {
        self.run_deferred_if_ready();
        self.state.borrow().clone()
    }

    fn run_deferred_if_ready(&self) {
        let ready = self
            .deferred
            .borrow()
            .as_ref()
            .is_some_and(|(at, _)| Instant::now() >= *at);
        if !ready {
            return;
        }
        let task = self.deferred.borrow_mut().take().map(|(_, task)| task);
        if let Some(task) = task {
            settle_result(self, task());
        }
    }

    pub fn schedule(&self, delay: Duration, task: Rc<dyn Fn() -> Result<Value, String>>) {
        if delay.is_zero() {
            settle_result(self, task());
        } else {
            *self.deferred.borrow_mut() = Some((Instant::now() + delay, task));
        }
    }

    pub fn resolve(&self, value: Value) -> bool {
        self.settle(PromiseState::Fulfilled(value))
    }

    pub fn reject(&self, error: impl Into<String>) -> bool {
        self.settle(PromiseState::Rejected(error.into()))
    }

    fn settle(&self, next: PromiseState) -> bool {
        self.deferred.borrow_mut().take();
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

pub fn settle_result(destination: &Promise, result: Result<Value, String>) {
    match result {
        Ok(Value::Promise(source)) => {
            destination.adopt(&source);
        }
        Ok(value) => {
            destination.resolve(value);
        }
        Err(error) => {
            destination.reject(error);
        }
    }
}

pub trait PromiseProvider {
    fn native(&self) -> bool;
    fn run(&self, task: Rc<dyn Fn() -> Result<Value, String>>) -> Promise;
    fn delay(&self, duration: Duration, task: Rc<dyn Fn() -> Result<Value, String>>) -> Promise;
}

#[derive(Debug, Clone, Copy, Default)]
pub struct LocalPromiseProvider;

impl PromiseProvider for LocalPromiseProvider {
    fn native(&self) -> bool {
        true
    }

    fn run(&self, task: Rc<dyn Fn() -> Result<Value, String>>) -> Promise {
        let promise = Promise::new();
        settle_result(&promise, task());
        promise
    }

    fn delay(&self, duration: Duration, task: Rc<dyn Fn() -> Result<Value, String>>) -> Promise {
        let promise = Promise::new();
        promise.schedule(duration, task);
        promise
    }
}
