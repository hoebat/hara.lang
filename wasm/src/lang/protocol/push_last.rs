pub trait IPushLast<E>: Sized {
    fn push_last(&self, value: E) -> Self;
}
