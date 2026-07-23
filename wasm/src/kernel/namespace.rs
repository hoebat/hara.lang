use std::cell::RefCell;
use std::collections::HashMap;
use std::rc::Rc;

use crate::kernel::Var;
use crate::lang::data::Symbol;
use crate::lang::protocol::INamespaced;

#[derive(Debug, Clone)]
pub struct Namespace<V> {
    name: Symbol,
    mappings: Rc<RefCell<HashMap<Symbol, Var<V>>>>,
    aliases: Rc<RefCell<HashMap<Symbol, Namespace<V>>>>,
    imports: Rc<RefCell<HashMap<Symbol, String>>>,
    native_flavor: Rc<RefCell<Option<String>>>,
}
impl<V> Namespace<V> {
    pub fn new(name: impl AsRef<str>) -> Self {
        Self {
            name: Symbol::parse(name.as_ref()),
            mappings: Rc::new(RefCell::new(HashMap::new())),
            aliases: Rc::new(RefCell::new(HashMap::new())),
            imports: Rc::new(RefCell::new(HashMap::new())),
            native_flavor: Rc::new(RefCell::new(None)),
        }
    }
    pub fn name(&self) -> &Symbol {
        &self.name
    }
    pub fn intern(&self, name: impl AsRef<str>, value: V) -> Var<V>
    where
        V: Clone,
    {
        let local = Symbol::parse(name.as_ref());
        let path = format!("{}/{}", self.name.as_str(), local.get_name());
        let var = Var::new(path, value);
        self.mappings.borrow_mut().insert(local, var.clone());
        var
    }
    pub fn map_var(&self, symbol: Symbol, var: Var<V>) {
        self.mappings.borrow_mut().insert(symbol, var);
    }
    pub fn resolve(&self, symbol: &Symbol) -> Option<Var<V>>
    where
        V: Clone,
    {
        if let Some(namespace) = symbol.get_namespace() {
            let alias = Symbol::parse(namespace);
            return self.aliases.borrow().get(&alias).and_then(|ns| {
                ns.mappings
                    .borrow()
                    .get(&Symbol::parse(symbol.get_name()))
                    .cloned()
            });
        }
        self.mappings.borrow().get(symbol).cloned()
    }
    pub fn alias(&self, alias: impl AsRef<str>, namespace: Namespace<V>) {
        self.aliases
            .borrow_mut()
            .insert(Symbol::parse(alias.as_ref()), namespace);
    }
    pub fn import(&self, name: impl AsRef<str>, host_type: impl Into<String>) {
        self.imports
            .borrow_mut()
            .insert(Symbol::parse(name.as_ref()), host_type.into());
    }
    pub fn imported(&self, name: &Symbol) -> Option<String> {
        self.imports.borrow().get(name).cloned()
    }
    pub fn set_native_flavor(&self, flavor: Option<String>) {
        *self.native_flavor.borrow_mut() = flavor;
    }
    pub fn native_flavor(&self) -> Option<String> {
        self.native_flavor.borrow().clone()
    }
    pub fn mappings(&self) -> Vec<(Symbol, Var<V>)>
    where
        V: Clone,
    {
        self.mappings
            .borrow()
            .iter()
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::Namespace;
    use crate::lang::data::Symbol;
    use crate::lang::protocol::IDeref;
    #[test]
    fn resolves_local_and_aliased_vars() {
        let source = Namespace::new("source");
        source.intern("answer", 42);
        let target = Namespace::new("target");
        target.alias("s", source.clone());
        assert_eq!(
            source.resolve(&Symbol::parse("answer")).unwrap().deref(),
            42
        );
        assert_eq!(
            target.resolve(&Symbol::parse("s/answer")).unwrap().deref(),
            42
        );
    }
}
