use std::collections::{HashMap, HashSet};

use super::Form;

const LIBRARIES: &[(&str, &str, &str)] = &[
    ("string", "hara.lib.string", "str"),
    ("promise", "hara.lib.promise", "promise"),
    ("bytes", "hara.lib.bytes", "bytes"),
    ("socket", "hara.lib.socket", "socket"),
    ("file", "hara.lib.file", "file"),
];

#[derive(Debug, Clone, Default)]
pub struct GeneratedNamespaceConfig {
    aliases: HashMap<String, String>,
    refers: HashMap<String, String>,
    required_namespaces: Vec<String>,
}

impl GeneratedNamespaceConfig {
    pub fn defaults() -> Self {
        let aliases = LIBRARIES
            .iter()
            .map(|(_, namespace, alias)| ((*alias).into(), (*namespace).into()))
            .collect();
        Self {
            aliases,
            refers: HashMap::new(),
            required_namespaces: Vec::new(),
        }
    }

    pub fn configure(clauses: &[Form]) -> Result<Self, String> {
        Self::configure_with(clauses, known_namespace)
    }

    pub fn configure_with(
        clauses: &[Form],
        available: impl Fn(&str) -> bool,
    ) -> Result<Self, String> {
        let mut excluded = HashSet::new();
        let mut overrides = HashMap::new();
        let mut requires = Vec::new();
        let mut intrinsics_seen = false;

        for clause in clauses {
            let values = list(clause, "ns clauses must be non-empty lists")?;
            let head = values.first().ok_or("ns clauses must be non-empty lists")?;
            let name = keyword(head, "ns clause must start with a keyword")?;
            match name {
                "intrinsics" => {
                    if intrinsics_seen {
                        return Err("ns accepts only one :intrinsics clause".into());
                    }
                    intrinsics_seen = true;
                    if values.len() != 2 {
                        return Err(":intrinsics expects :all or an options map".into());
                    }
                    if !matches!(&values[1], Form::Keyword(name) if name == "all") {
                        parse_intrinsics(&values[1], &mut excluded, &mut overrides)?;
                    }
                }
                "require" => requires.extend(values[1..].iter().cloned()),
                "flavor" | "import" => {}
                other => return Err(format!("Unsupported ns clause: :{other}")),
            }
        }

        for library in overrides.keys() {
            if excluded.contains(library) {
                return Err(format!(
                    "Intrinsic library cannot be both excluded and aliased: {library}"
                ));
            }
        }

        let mut config = Self::default();
        for (library, namespace, default_alias) in LIBRARIES {
            if excluded.contains(*library) {
                continue;
            }
            let alias = overrides
                .get(*library)
                .map_or(*default_alias, String::as_str);
            config.put_alias(alias, namespace)?;
        }
        for require in requires {
            config.apply_require(&require, &available)?;
        }
        Ok(config)
    }

    pub fn required_namespaces(&self) -> &[String] {
        &self.required_namespaces
    }

    pub fn rewrite(&self, form: Form) -> Form {
        match form {
            Form::Symbol(name) => Form::Symbol(self.resolve_symbol(&name)),
            Form::List(values) => {
                if matches!(values.first(), Some(Form::Symbol(name)) if name == "quote") {
                    Form::List(values)
                } else {
                    Form::List(
                        values
                            .into_iter()
                            .map(|value| self.rewrite(value))
                            .collect(),
                    )
                }
            }
            Form::Vector(values) => Form::Vector(
                values
                    .into_iter()
                    .map(|value| self.rewrite(value))
                    .collect(),
            ),
            Form::Set(values) => Form::Set(
                values
                    .into_iter()
                    .map(|value| self.rewrite(value))
                    .collect(),
            ),
            Form::Map(values) => Form::Map(
                values
                    .into_iter()
                    .map(|(key, value)| (self.rewrite(key), self.rewrite(value)))
                    .collect(),
            ),
            Form::Tagged(tag, value) => Form::Tagged(tag, Box::new(self.rewrite(*value))),
            Form::Metadata(meta, value) => Form::Metadata(meta, Box::new(self.rewrite(*value))),
            value => value,
        }
    }

    fn resolve_symbol(&self, symbol: &str) -> String {
        if let Some((namespace, method)) = symbol.rsplit_once('/') {
            if known_namespace(namespace) {
                return canonical(namespace, method);
            }
        }
        if let Some(canonical) = self.refers.get(symbol) {
            return canonical.clone();
        }
        let Some((alias, method)) = symbol.split_once('/') else {
            return symbol.into();
        };
        if LIBRARIES
            .iter()
            .any(|(_, _, default_alias)| *default_alias == alias)
            && !self.aliases.contains_key(alias)
        {
            return format!("unavailable/{symbol}");
        }
        self.aliases
            .get(alias)
            .map_or_else(|| symbol.into(), |namespace| canonical(namespace, method))
    }

    fn put_alias(&mut self, alias: &str, namespace: &str) -> Result<(), String> {
        if alias.is_empty() {
            return Err("Namespace alias cannot be empty".into());
        }
        if let Some(previous) = self.aliases.get(alias) {
            if previous != namespace {
                return Err(format!(
                    "Namespace alias already refers to {previous}: {alias}"
                ));
            }
            return Ok(());
        }
        self.aliases.insert(alias.into(), namespace.into());
        Ok(())
    }

    fn apply_require(
        &mut self,
        form: &Form,
        available: &impl Fn(&str) -> bool,
    ) -> Result<(), String> {
        let spec = vector(
            form,
            ":require expects vectors such as [hara.lib.string :as str]",
        )?;
        let target = match spec.first() {
            Some(Form::Symbol(target)) => target.as_str(),
            _ => return Err(":require namespace must be a symbol".into()),
        };
        let target = if target == "core" {
            "hara.lib.core"
        } else {
            target
        };
        if !known_namespace(target) && !available(target) {
            return Err(format!(
                "Cannot require missing generated namespace: {target}"
            ));
        }
        if !self.required_namespaces.iter().any(|value| value == target) {
            self.required_namespaces.push(target.into());
        }
        if (spec.len() - 1) % 2 != 0 {
            return Err(format!("Malformed :require options for {target}"));
        }
        for option in spec[1..].chunks(2) {
            let name = keyword(&option[0], "Malformed :require options")?;
            match name {
                "as" => {
                    let alias = symbol(&option[1], ":require :as expects an unqualified symbol")?;
                    if alias.contains('/') {
                        return Err(":require :as expects an unqualified symbol".into());
                    }
                    self.put_alias(alias, target)?;
                }
                "refer" => {
                    let names = vector(&option[1], ":require :refer expects a vector of symbols")?;
                    for value in names {
                        let name = symbol(value, ":require :refer expects unqualified symbols")?;
                        if name.contains('/') {
                            return Err(":require :refer expects unqualified symbols".into());
                        }
                        let canonical = canonical(target, name);
                        if let Some(previous) = self.refers.insert(name.into(), canonical) {
                            return Err(format!(
                                "Referred symbol already exists: {name} ({previous})"
                            ));
                        }
                    }
                }
                other => return Err(format!("Unsupported :require option: :{other}")),
            }
        }
        Ok(())
    }
}

fn parse_intrinsics(
    form: &Form,
    excluded: &mut HashSet<String>,
    overrides: &mut HashMap<String, String>,
) -> Result<(), String> {
    let options = match form {
        Form::Map(options) => options,
        _ => return Err(":intrinsics expects :all or an options map".into()),
    };
    for (key, value) in options {
        match keyword(key, ":intrinsics option keys must be keywords")? {
            "exclude" => {
                for item in vector(
                    value,
                    ":intrinsics :exclude expects a vector of library symbols",
                )? {
                    let library = library(symbol(
                        item,
                        ":intrinsics :exclude expects unqualified library symbols",
                    )?)?;
                    if !excluded.insert(library.into()) {
                        return Err(format!("Duplicate intrinsic exclusion: {library}"));
                    }
                }
            }
            "aliases" => {
                let aliases = match value {
                    Form::Map(aliases) => aliases,
                    _ => return Err(":intrinsics :aliases expects a map".into()),
                };
                for (library_form, alias_form) in aliases {
                    let library = library(symbol(
                        library_form,
                        ":intrinsics :aliases expects library symbols",
                    )?)?;
                    let alias =
                        symbol(alias_form, "Intrinsic aliases must be unqualified symbols")?;
                    if alias.contains('/') {
                        return Err("Intrinsic aliases must be unqualified symbols".into());
                    }
                    if overrides.insert(library.into(), alias.into()).is_some() {
                        return Err(format!("Duplicate intrinsic alias: {library}"));
                    }
                }
            }
            other => return Err(format!("Unsupported :intrinsics option: :{other}")),
        }
    }
    Ok(())
}

fn list<'a>(form: &'a Form, error: &str) -> Result<&'a [Form], String> {
    match form {
        Form::List(values) => Ok(values),
        _ => Err(error.into()),
    }
}
fn vector<'a>(form: &'a Form, error: &str) -> Result<&'a [Form], String> {
    match form {
        Form::Vector(values) => Ok(values),
        _ => Err(error.into()),
    }
}
fn keyword<'a>(form: &'a Form, error: &str) -> Result<&'a str, String> {
    match form {
        Form::Keyword(value) => Ok(value),
        _ => Err(error.into()),
    }
}
fn symbol<'a>(form: &'a Form, error: &str) -> Result<&'a str, String> {
    match form {
        Form::Symbol(value) => Ok(value),
        _ => Err(error.into()),
    }
}
fn library(value: &str) -> Result<&str, String> {
    if value.contains('/') {
        return Err("Intrinsic library names must be unqualified symbols".into());
    }
    LIBRARIES
        .iter()
        .find(|(library, _, _)| *library == value)
        .map(|(library, _, _)| *library)
        .ok_or_else(|| format!("Unknown intrinsic library: {value}"))
}
fn known_namespace(value: &str) -> bool {
    value == "hara.lib.core"
        || LIBRARIES
            .iter()
            .any(|(_, namespace, _)| *namespace == value)
}
fn canonical(namespace: &str, method: &str) -> String {
    match (namespace, method) {
        ("hara.lib.core", method) => method.into(),
        ("hara.lib.string", "len") => "str/count".into(),
        ("hara.lib.string", "to-upper") => "str/upper".into(),
        ("hara.lib.string", "to-lower") => "str/lower".into(),
        ("hara.lib.string", method) => format!("str/{method}"),
        ("hara.lib.promise", "then") => "promise/map".into(),
        ("hara.lib.promise", "catch") => "promise/recover".into(),
        ("hara.lib.promise", method) => format!("promise/{method}"),
        ("hara.lib.bytes", method) => format!("bytes/{method}"),
        ("hara.lib.socket", method) => format!("socket/{method}"),
        ("hara.lib.file", method) => format!("file/{method}"),
        (namespace, method) => format!("{namespace}/{method}"),
    }
}

#[cfg(test)]
mod tests {
    use super::GeneratedNamespaceConfig;
    use crate::kernel::parse_forms;

    #[test]
    fn configures_defaults_exclusions_aliases_and_requires_without_sources() {
        let forms = parse_forms(
            "(:intrinsics {:exclude [bytes] :aliases {string text}}) \
             (:require [hara.lib.string :as s :refer [trim]])",
        )
        .unwrap();
        let config = GeneratedNamespaceConfig::configure(&forms).unwrap();
        let rewritten = config.rewrite(
            parse_forms("(trim (s/trim (text/to-upper \" x \")))")
                .unwrap()
                .remove(0),
        );
        let display = format!("{rewritten:?}");
        assert!(display.contains("str/trim"));
        assert!(display.contains("str/upper"));
        assert!(display.contains("bytes/count") == false);
        assert!(GeneratedNamespaceConfig::configure(
            &parse_forms("(:require [missing.lib :as x])").unwrap()
        )
        .unwrap_err()
        .contains("missing generated namespace"));
    }
}
