pub trait IEmpty: Sized {
    fn empty(&self) -> Self;
}
