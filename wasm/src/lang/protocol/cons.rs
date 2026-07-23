pub trait ICons<E>: Sized {
    fn cons(&self, value: E) -> Self;
}
