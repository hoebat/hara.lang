use std::collections::HashMap;
use std::fmt;

use crate::lang::data::{Atom, Symbol};
use crate::lang::protocol::{IDeref, IDisplay, INamespaced, IReset};

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct VarMetadata {
    pub control: bool,
    pub dynamic: bool,
    pub macro_form: bool,
    pub doc: Option<String>,
    pub arglists: Vec<String>,
    pub extra: HashMap<String, String>,
}

#[derive(Debug, Clone)]
pub struct Var<V> {
    symbol: Symbol,
    value: Atom<V>,
    metadata: VarMetadata,
}
impl<V> Var<V> {
    pub fn new(path: impl AsRef<str>, value: V) -> Self {
        Self {
            symbol: Symbol::parse(path.as_ref()),
            value: Atom::new(value),
            metadata: VarMetadata::default(),
        }
    }
    pub fn with_metadata(path: impl AsRef<str>, value: V, metadata: VarMetadata) -> Self {
        Self {
            symbol: Symbol::parse(path.as_ref()),
            value: Atom::new(value),
            metadata,
        }
    }
    pub fn symbol(&self) -> &Symbol {
        &self.symbol
    }
    pub fn metadata(&self) -> &VarMetadata {
        &self.metadata
    }
    pub fn is_control(&self) -> bool {
        self.metadata.control
    }
    pub fn is_dynamic(&self) -> bool {
        self.metadata.dynamic
    }
    pub fn is_macro(&self) -> bool {
        self.metadata.macro_form
    }
}
impl<V: Clone> Var<V> {
    pub fn deref_value(&self) -> V {
        self.value.deref_value()
    }
    pub fn reset_value(&self, value: V) -> V {
        self.value.reset(value).expect("unvalidated var")
    }
}
impl<V: Clone> IDeref for Var<V> {
    type Output = V;
    fn deref(&self) -> V {
        self.deref_value()
    }
}
impl<V: Clone> IReset<V> for Var<V> {
    type Error = String;
    fn reset(&self, value: V) -> Result<V, String> {
        Ok(self.reset_value(value))
    }
}
impl<V> INamespaced for Var<V> {
    fn get_name(&self) -> &str {
        self.symbol.get_name()
    }
    fn get_namespace(&self) -> Option<&str> {
        self.symbol.get_namespace()
    }
}
impl<V> IDisplay for Var<V> {
    fn display(&self) -> String {
        format!("#'{}", self.symbol.as_str())
    }
}
impl<V> fmt::Display for Var<V> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.display())
    }
}

#[cfg(test)]
mod tests {
    use super::{Var, VarMetadata};
    use crate::lang::protocol::{IDeref, IDisplay, INamespaced};
    #[test]
    fn is_namespaced_resettable_and_metadata_driven() {
        let var = Var::with_metadata(
            "hello/value",
            1,
            VarMetadata {
                dynamic: true,
                ..Default::default()
            },
        );
        assert_eq!(var.get_namespace(), Some("hello"));
        assert_eq!(var.display(), "#'hello/value");
        assert_eq!(var.reset_value(2), 2);
        assert_eq!(var.deref(), 2);
        assert!(var.is_dynamic());
    }
}
