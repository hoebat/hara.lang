pub trait IConj<E>: Sized {
    fn conj(&self, value: E) -> Self;
}
