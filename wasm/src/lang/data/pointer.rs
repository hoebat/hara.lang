use crate::lang::data::Symbol;
use crate::lang::protocol::{IDisplay, INamespaced};
use std::fmt;
use std::hash::{Hash, Hasher};
#[derive(Debug, Clone, Eq, PartialEq)]
pub struct Pointer(Symbol);
impl Pointer {
    pub fn create(namespace: Option<&str>, name: &str) -> Self {
        Self(Symbol::create(namespace, name))
    }
    pub fn parse(path: &str) -> Self {
        Self(Symbol::parse(path))
    }
    pub fn path(&self) -> &str {
        self.0.as_str()
    }
}
impl INamespaced for Pointer {
    fn get_name(&self) -> &str {
        self.0.get_name()
    }
    fn get_namespace(&self) -> Option<&str> {
        self.0.get_namespace()
    }
}
impl IDisplay for Pointer {
    fn display(&self) -> String {
        format!("#'{}", self.path())
    }
}
impl Hash for Pointer {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.0.hash(state)
    }
}
impl fmt::Display for Pointer {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.display())
    }
}
