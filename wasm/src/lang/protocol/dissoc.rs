pub trait IDissoc<K>: Sized {
    fn dissoc(&self, key: &K) -> Self;
}
