#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HashType {
    System,
    Rapid,
    Murmur3,
    Sip,
}

pub trait IHash {
    fn hash_calc(&self, hash_type: HashType) -> u64;

    fn hash(&self) -> u64 {
        self.hash_calc(HashType::Rapid)
    }
}

pub trait IHashCached: IHash {
    fn hash_current(&self) -> u64;
    fn hash_put(&self, hash: u64);

    fn hash_cached(&self) -> u64 {
        let current = self.hash_current();
        if current != 0 {
            return current;
        }
        let hash = self.hash();
        self.hash_put(hash);
        hash
    }

    fn hash_cached_as(&self, hash_type: HashType) -> u64 {
        if hash_type == HashType::Rapid {
            self.hash_cached()
        } else {
            self.hash_calc(hash_type)
        }
    }
}
