use crate::lang::data::Symbol;
use crate::lang::protocol::{IDisplay, IMetadata, INamespaced, IObjType, ObjType};
use std::fmt;
use std::hash::{Hash, Hasher};
use std::rc::Rc;
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
impl IMetadata for Pointer {
    type Metadata = Rc<str>;
    fn meta(&self) -> Option<&Self::Metadata> {
        self.0.meta()
    }
    fn with_meta(&self, metadata: Option<Self::Metadata>) -> Self {
        Self(self.0.with_meta(metadata))
    }
}
impl IDisplay for Pointer {
    fn display(&self) -> String {
        format!("#'{}", self.path())
    }
}
impl IObjType for Pointer {
    fn obj_type(&self) -> ObjType {
        ObjType::Pointer
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
