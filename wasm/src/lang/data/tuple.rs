#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum Tuple<E> {
    Tup0,
    Tup1([E; 1]),
    Tup2([E; 2]),
    Tup3([E; 3]),
    Tup4([E; 4]),
    Tup5([E; 5]),
    Tup6([E; 6]),
    Tup7([E; 7]),
    Tup8([E; 8]),
}

impl<E: Clone> Tuple<E> {
    pub fn len(&self) -> usize {
        match self {
            Self::Tup0 => 0,
            Self::Tup1(_) => 1,
            Self::Tup2(_) => 2,
            Self::Tup3(_) => 3,
            Self::Tup4(_) => 4,
            Self::Tup5(_) => 5,
            Self::Tup6(_) => 6,
            Self::Tup7(_) => 7,
            Self::Tup8(_) => 8,
        }
    }
    pub fn is_empty(&self) -> bool {
        matches!(self, Self::Tup0)
    }
    pub fn get(&self, index: usize) -> Option<&E> {
        match self {
            Self::Tup0 => None,
            Self::Tup1(v) => v.get(index),
            Self::Tup2(v) => v.get(index),
            Self::Tup3(v) => v.get(index),
            Self::Tup4(v) => v.get(index),
            Self::Tup5(v) => v.get(index),
            Self::Tup6(v) => v.get(index),
            Self::Tup7(v) => v.get(index),
            Self::Tup8(v) => v.get(index),
        }
    }
    pub fn iter(&self) -> impl Iterator<Item = &E> {
        (0..self.len()).map(|i| self.get(i).expect("valid tuple index"))
    }
    pub fn from_values(values: Vec<E>) -> Result<Self, String> {
        Ok(match values.as_slice() {
            [] => Self::Tup0,
            [a] => Self::Tup1([a.clone()]),
            [a, b] => Self::Tup2([a.clone(), b.clone()]),
            [a, b, c] => Self::Tup3([a.clone(), b.clone(), c.clone()]),
            [a, b, c, d] => Self::Tup4([a.clone(), b.clone(), c.clone(), d.clone()]),
            [a, b, c, d, e] => Self::Tup5([a.clone(), b.clone(), c.clone(), d.clone(), e.clone()]),
            [a, b, c, d, e, f] => Self::Tup6([
                a.clone(),
                b.clone(),
                c.clone(),
                d.clone(),
                e.clone(),
                f.clone(),
            ]),
            [a, b, c, d, e, f, g] => Self::Tup7([
                a.clone(),
                b.clone(),
                c.clone(),
                d.clone(),
                e.clone(),
                f.clone(),
                g.clone(),
            ]),
            [a, b, c, d, e, f, g, h] => Self::Tup8([
                a.clone(),
                b.clone(),
                c.clone(),
                d.clone(),
                e.clone(),
                f.clone(),
                g.clone(),
                h.clone(),
            ]),
            _ => return Err("tuple arity cannot exceed 8".into()),
        })
    }
    pub fn push_first(&self, value: E) -> Result<Self, String> {
        Self::from_values(std::iter::once(value).chain(self.iter().cloned()).collect())
    }
    pub fn push_last(&self, value: E) -> Result<Self, String> {
        Self::from_values(self.iter().cloned().chain(std::iter::once(value)).collect())
    }
    pub fn pop_first(&self) -> Self {
        Self::from_values(self.iter().skip(1).cloned().collect()).expect("smaller tuple")
    }
    pub fn pop_last(&self) -> Self {
        Self::from_values(
            self.iter()
                .take(self.len().saturating_sub(1))
                .cloned()
                .collect(),
        )
        .expect("smaller tuple")
    }
    pub fn peek_first(&self) -> Option<&E> {
        self.get(0)
    }
    pub fn peek_last(&self) -> Option<&E> {
        self.len().checked_sub(1).and_then(|i| self.get(i))
    }
}
impl<E: Clone> Default for Tuple<E> {
    fn default() -> Self {
        Self::Tup0
    }
}

#[cfg(test)]
mod tests {
    use super::Tuple;
    #[test]
    fn covers_all_java_arities_and_linear_operations() {
        let mut t = Tuple::Tup0;
        for i in 0..8 {
            t = t.push_last(i).unwrap();
            assert_eq!(t.len(), i + 1)
        }
        assert!(t.push_last(8).is_err());
        assert_eq!(t.peek_first(), Some(&0));
        assert_eq!(t.peek_last(), Some(&7));
        assert_eq!(t.pop_first().peek_first(), Some(&1));
        assert_eq!(t.pop_last().peek_last(), Some(&6));
    }
}
