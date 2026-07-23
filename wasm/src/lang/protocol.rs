#[path = "protocol/assoc.rs"]
pub mod assoc;
#[path = "protocol/coll.rs"]
pub mod coll;
#[path = "protocol/conj.rs"]
pub mod conj;
#[path = "protocol/cons.rs"]
pub mod cons;
#[path = "protocol/count.rs"]
pub mod count;
#[path = "protocol/deref.rs"]
pub mod deref;
#[path = "protocol/display.rs"]
pub mod display;
#[path = "protocol/dissoc.rs"]
pub mod dissoc;
#[path = "protocol/empty.rs"]
pub mod empty;
#[path = "protocol/equality.rs"]
pub mod equality;
#[path = "protocol/find.rs"]
pub mod find;
#[path = "protocol/hash.rs"]
pub mod hash;
#[path = "protocol/indexed.rs"]
pub mod indexed;
#[path = "protocol/indexed_kv.rs"]
pub mod indexed_kv;
#[path = "protocol/lookup.rs"]
pub mod lookup;
#[path = "protocol/metadata.rs"]
pub mod metadata;
#[path = "protocol/mutable.rs"]
pub mod mutable;
#[path = "protocol/namespaced.rs"]
pub mod namespaced;
#[path = "protocol/nth.rs"]
pub mod nth;
#[path = "protocol/obj_type.rs"]
pub mod obj_type;
#[path = "protocol/pair.rs"]
pub mod pair;
#[path = "protocol/peek_first.rs"]
pub mod peek_first;
#[path = "protocol/peek_last.rs"]
pub mod peek_last;
#[path = "protocol/persistent.rs"]
pub mod persistent;
#[path = "protocol/pointer.rs"]
pub mod pointer;
#[path = "protocol/pop_first.rs"]
pub mod pop_first;
#[path = "protocol/pop_last.rs"]
pub mod pop_last;
#[path = "protocol/push_first.rs"]
pub mod push_first;
#[path = "protocol/push_last.rs"]
pub mod push_last;
#[path = "protocol/ranged.rs"]
pub mod ranged;
#[path = "protocol/realize.rs"]
pub mod realize;
#[path = "protocol/reset.rs"]
pub mod reset;
#[path = "protocol/to_mutable.rs"]
pub mod to_mutable;
#[path = "protocol/to_persistent.rs"]
pub mod to_persistent;
#[path = "protocol/validate.rs"]
pub mod validate;
#[path = "protocol/watch.rs"]
pub mod watch;

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
pub use hash::{HashType, IHash, IHashCached};
pub use indexed::IIndexed;
pub use indexed_kv::IIndexedKV;
pub use lookup::ILookup;
pub use metadata::{IMetadata, MetaType};
pub use mutable::IMutable;
pub use namespaced::INamespaced;
pub use nth::INth;
pub use obj_type::{IObjType, ObjType};
pub use pair::IPair;
pub use peek_first::IPeekFirst;
pub use peek_last::IPeekLast;
pub use persistent::IPersistent;
pub use pointer::IPointer;
pub use pop_first::IPopFirst;
pub use pop_last::IPopLast;
pub use push_first::IPushFirst;
pub use push_last::IPushLast;
pub use ranged::IRanged;
pub use realize::IRealize;
pub use reset::IReset;
pub use to_mutable::IToMutable;
pub use to_persistent::IToPersistent;
pub use validate::IValidate;
pub use watch::IWatch;

#[cfg(test)]
mod tests {
    use super::{IFind, IObjType, ObjType};
    use crate::lang::data::{Cons, List, Queue, Seq, Tuple, Vector};

    struct Entries(Vec<(u8, Option<u8>)>);

    impl IFind<u8> for Entries {
        type Output = (u8, Option<u8>);

        fn find(&self, key: &u8) -> Option<Self::Output> {
            self.0
                .iter()
                .find(|(candidate, _)| candidate == key)
                .cloned()
        }
    }

    #[test]
    fn sequential_family_uses_java_protocol_category() {
        assert_eq!(List::<i32>::new().obj_type(), ObjType::Sequential);
        assert_eq!(Vector::<i32>::new().obj_type(), ObjType::Sequential);
        assert_eq!(Tuple::<i32>::Tup0.obj_type(), ObjType::Sequential);
        assert_eq!(Queue::<i32>::new().obj_type(), ObjType::Sequential);
        assert_eq!(Cons::new(1, List::new()).obj_type(), ObjType::Sequential);
        assert_eq!(Seq::new([1].into_iter()).obj_type(), ObjType::Sequential);
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
