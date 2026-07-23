pub trait IPushFirst<E>: Sized {
    fn push_first(&self, value: E) -> Self;
}
