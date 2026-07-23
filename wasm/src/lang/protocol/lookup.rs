use super::IFind;

pub trait ILookup<K, V>: IFind<K, Output = (K, V)>
where
    K: Clone,
    V: Clone,
{
    type Keys<'a>: Iterator<Item = &'a K>
    where
        Self: 'a,
        K: 'a;
    type Values<'a>: Iterator<Item = &'a V>
    where
        Self: 'a,
        V: 'a;

    fn keys(&self) -> Self::Keys<'_>;

    fn lookup(&self, key: &K) -> Option<V> {
        self.find(key).map(|(_, value)| value)
    }

    fn lookup_or(&self, key: &K, not_found: V) -> V {
        self.lookup(key).unwrap_or(not_found)
    }

    fn vals(&self) -> Self::Values<'_>;
}
