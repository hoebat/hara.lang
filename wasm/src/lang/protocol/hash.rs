#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HashType {
    Rapid,
}

pub trait IHash {
    fn hash_calc(&self, hash_type: HashType) -> u64;

    fn hash(&self) -> u64 {
        self.hash_calc(HashType::Rapid)
    }
}
