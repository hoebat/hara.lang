pub mod assoc;
pub mod coll;
pub mod conj;
pub mod cons;
pub mod count;
pub mod deref;
pub mod display;
pub mod dissoc;
pub mod empty;
pub mod equality;
pub mod find;
pub mod hash;
pub mod lookup;
pub mod metadata;
pub mod mutable;
pub mod nth;
pub mod persistent;
pub mod pop_first;
pub mod pop_last;
pub mod push_first;
pub mod push_last;
pub mod to_mutable;
pub mod to_persistent;

pub use assoc::IAssoc;
pub use coll::IColl;
pub use conj::IConj;
pub use cons::ICons;
pub use count::ICount;
pub use deref::IDeref;
pub use display::IDisplay;
pub use dissoc::IDissoc;
pub use empty::IEmpty;
pub use equality::IEquality;
pub use find::IFind;
pub use hash::IHash;
pub use lookup::ILookup;
pub use metadata::IMetadata;
pub use mutable::IMutable;
pub use nth::INth;
pub use persistent::IPersistent;
pub use pop_first::IPopFirst;
pub use pop_last::IPopLast;
pub use push_first::IPushFirst;
pub use push_last::IPushLast;
pub use to_mutable::IToMutable;
pub use to_persistent::IToPersistent;

#[cfg(test)]
mod tests {
    use super::IFind;

    struct Entries(Vec<(u8, Option<u8>)>);

    impl IFind<u8> for Entries {
        type Output = (u8, Option<u8>);

        fn find(&self, key: &u8) -> Option<Self::Output> {
            self.0.iter().find(|(candidate, _)| candidate == key).cloned()
        }
    }

    #[test]
    fn find_has_distinguishes_absence_from_a_nil_value() {
        let entries = Entries(vec![(1, None), (2, Some(7))]);
        assert_eq!(entries.find(&1), Some((1, None)));
        assert!(entries.has(&1));
        assert!(entries.has(&2));
        assert!(!entries.has(&3));
    }
}
