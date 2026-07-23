use std::cell::RefCell;
use std::collections::HashMap;
use std::rc::{Rc, Weak};

use crate::lang::protocol::{IDisplay, INamespaced};

thread_local! {
    static INTERNED: RefCell<HashMap<String, Weak<Data>>> = RefCell::new(HashMap::new());
}

#[derive(Debug)]
struct Data {
    namespace: Option<String>,
    name: String,
    full: String,
}

#[derive(Debug, Clone)]
pub struct Symbol(Rc<Data>);

impl Symbol {
    pub fn create(namespace: Option<&str>, name: &str) -> Self {
        Self::parse(
            &namespace
                .map(|ns| format!("{ns}/{name}"))
                .unwrap_or_else(|| name.into()),
        )
    }

    pub fn parse(full: &str) -> Self {
        INTERNED.with(|cache| {
            if let Some(value) = cache.borrow().get(full).and_then(Weak::upgrade) {
                return Self(value);
            }
            let slash = if full == "/" { None } else { full.find('/') };
            let data = Rc::new(Data {
                namespace: slash.map(|i| full[..i].into()),
                name: slash
                    .map(|i| full[i + 1..].into())
                    .unwrap_or_else(|| full.into()),
                full: full.into(),
            });
            cache.borrow_mut().insert(full.into(), Rc::downgrade(&data));
            Self(data)
        })
    }

    pub fn as_str(&self) -> &str {
        &self.0.full
    }
    pub fn same_identity(&self, other: &Self) -> bool {
        Rc::ptr_eq(&self.0, &other.0)
    }
}

impl INamespaced for Symbol {
    fn get_name(&self) -> &str {
        &self.0.name
    }
    fn get_namespace(&self) -> Option<&str> {
        self.0.namespace.as_deref()
    }
}
impl IDisplay for Symbol {
    fn display(&self) -> String {
        self.0.full.clone()
    }
}
impl PartialEq for Symbol {
    fn eq(&self, other: &Self) -> bool {
        self.0.full == other.0.full
    }
}
impl Eq for Symbol {}
impl std::hash::Hash for Symbol {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.0.full.hash(state);
    }
}

impl From<&str> for Symbol {
    fn from(value: &str) -> Self {
        Self::parse(value)
    }
}
impl From<String> for Symbol {
    fn from(value: String) -> Self {
        Self::parse(&value)
    }
}
impl std::fmt::Display for Symbol {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.as_str())
    }
}

#[cfg(test)]
mod tests {
    use super::Symbol;
    use crate::lang::protocol::{IDisplay, INamespaced};

    #[test]
    fn matches_java_namespace_and_interning() {
        let first = Symbol::parse("hara/name");
        let second = Symbol::create(Some("hara"), "name");
        assert!(first.same_identity(&second));
        assert_eq!(first.get_namespace(), Some("hara"));
        assert_eq!(first.get_name(), "name");
        assert_eq!(first.display(), "hara/name");
        assert_eq!(Symbol::parse("/").get_namespace(), None);
    }
}
