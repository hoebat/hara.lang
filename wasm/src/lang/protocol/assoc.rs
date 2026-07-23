pub trait IAssoc<K, V>: Sized {
    fn assoc(&self, key: K, value: V) -> Self;
}
