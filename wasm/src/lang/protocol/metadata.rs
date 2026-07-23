pub trait IMetadata: Sized {
    type Metadata: Clone;

    fn meta(&self) -> Option<&Self::Metadata>;
    fn with_meta(&self, metadata: Option<Self::Metadata>) -> Self;
}
