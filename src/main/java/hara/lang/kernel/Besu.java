package hara.lang.kernel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.List;
import java.util.Optional;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import java.math.BigInteger;

public class Besu {

    private final EVM evm;
    private final MessageCallProcessor messageCallProcessor;
    private final ContractCreationProcessor contractCreationProcessor;
    private final InMemoryWorldState worldState;

    public Besu() {
        var gasCalculator = new LondonGasCalculator();
        this.evm = MainnetEVMs.london(
            gasCalculator,
            BigInteger.valueOf(1),
            EvmConfiguration.DEFAULT
        );

        var precompiledContractMap = new org.hyperledger.besu.evm.precompile.PrecompileContractRegistry();

        this.messageCallProcessor = new MessageCallProcessor(evm, precompiledContractMap);
        this.contractCreationProcessor = new ContractCreationProcessor(evm, true, List.of(), 0);
        this.worldState = new InMemoryWorldState();
    }

    public Object run(String sender, String receiver, String input, String value) {
        return run(
            Address.fromHexString(sender),
            Address.fromHexString(receiver),
            Bytes.fromHexString(input),
            Wei.fromHexString(value)
        );
    }

    public Object run(Address sender, Address receiver, Bytes input, Wei value) {
        WorldUpdater updater = worldState.updater();

        MutableAccount senderAccount = updater.getOrCreate(sender);
        if (senderAccount.getBalance().isZero()) {
            senderAccount.setBalance(Wei.of(UInt256.MAX_VALUE));
        }

        Account receiverAccount = updater.getOrCreate(receiver);
        org.hyperledger.besu.evm.Code code = evm.getCode(receiverAccount.getCodeHash(), receiverAccount.getCode());

        MessageFrame initialFrame = MessageFrame.builder()
            .type(MessageFrame.Type.MESSAGE_CALL)
            .worldUpdater(updater)
            .initialGas(10_000_000L)
            .contract(receiver)
            .address(receiver)
            .originator(sender)
            .sender(sender)
            .gasPrice(Wei.ZERO)
            .inputData(input)
            .value(value)
            .apparentValue(value)
            .code(code)
            .blockValues(new SimpleBlockValues())
            .completer(c -> {})
            .miningBeneficiary(Address.ZERO)
            .blockHashLookup(h -> null)
            .build();

        messageCallProcessor.process(initialFrame, OperationTracer.NO_TRACING);

        if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
            updater.commit();
            return initialFrame.getOutputData().toHexString();
        } else {
            if (initialFrame.getRevertReason().isPresent()) {
                 return "REVERT: " + initialFrame.getRevertReason().get().toHexString();
            }
            return "FAILURE: " + initialFrame.getState();
        }
    }

    static class SimpleBlockValues implements BlockValues {
        @Override public Optional<Wei> getBaseFee() { return Optional.empty(); }
        @Override public Bytes32 getMixHashOrPrevRandao() { return Bytes32.ZERO; }

        public long getNumber() { return 0; }
        public long getTimestamp() { return 0; }
        public long getGasLimit() { return 10_000_000L; }
        public Bytes getDifficultyBytes() { return Bytes.of(0); }
    }

    static class InMemoryWorldState {

        private final Map<Address, MutableAccount> accounts = new ConcurrentHashMap<>();

        public Account get(Address address) {
            return accounts.get(address);
        }

        public Hash rootHash() {
            return Hash.ZERO;
        }

        public Hash frontierRootHash() {
            return Hash.ZERO;
        }

        public WorldUpdater updater() {
            return new WorldUpdater() {
                 private Map<Address, MutableAccount> tracked = new ConcurrentHashMap<>();

                @Override
                public WorldUpdater updater() {
                    return this;
                }

                @Override
                public Account get(Address address) {
                     if (tracked.containsKey(address)) return tracked.get(address);
                     return accounts.get(address);
                }

                @Override
                public MutableAccount createAccount(Address address, long nonce, Wei balance) {
                     SimpleAccount a = new SimpleAccount(address, nonce, balance);
                     tracked.put(address, a);
                     return a;
                }

                @Override
                public MutableAccount getAccount(Address address) {
                    if (tracked.containsKey(address)) return tracked.get(address);
                    if (accounts.containsKey(address)) {
                         SimpleAccount original = (SimpleAccount) accounts.get(address);
                         SimpleAccount copy = new SimpleAccount(original);
                         tracked.put(address, copy);
                         return copy;
                    }
                    return null;
                }

                @Override
                public MutableAccount getOrCreate(Address address) {
                    MutableAccount a = getAccount(address);
                    if (a != null) return a;
                    return createAccount(address, 0, Wei.ZERO);
                }

                @Override
                public void deleteAccount(Address address) {
                    tracked.put(address, null);
                }

                @Override
                public Optional<WorldUpdater> parentUpdater() {
                    return Optional.empty();
                }

                @Override
                public void commit() {
                    for(var entry : tracked.entrySet()) {
                        if(entry.getValue() == null) {
                            accounts.remove(entry.getKey());
                        } else {
                            accounts.put(entry.getKey(), entry.getValue());
                        }
                    }
                }

                @Override
                public void revert() {
                    tracked.clear();
                }

                @Override
                public java.util.Collection<? extends Account> getTouchedAccounts() {
                    return tracked.values();
                }

                @Override
                public java.util.Collection<Address> getDeletedAccountAddresses() {
                    return java.util.Collections.emptyList();
                }
            };
        }
    }

    static class SimpleAccount implements MutableAccount {
        private final Address address;
        private long nonce;
        private Wei balance;
        private Bytes code = Bytes.EMPTY;
        private final Map<UInt256, UInt256> storage = new ConcurrentHashMap<>();

        public SimpleAccount(Address address, long nonce, Wei balance) {
            this.address = address;
            this.nonce = nonce;
            this.balance = balance;
        }

        public SimpleAccount(SimpleAccount other) {
             this.address = other.address;
             this.nonce = other.nonce;
             this.balance = other.balance;
             this.code = other.code;
             this.storage.putAll(other.storage);
        }

        @Override
        public void becomeImmutable() {
        }

        @Override
        public boolean isStorageEmpty() {
            return storage.isEmpty();
        }

        @Override public Address getAddress() { return address; }
        @Override public Hash getAddressHash() { return Hash.hash(address); }
        @Override public long getNonce() { return nonce; }
        @Override public void setNonce(long value) { this.nonce = value; }
        @Override public Wei getBalance() { return balance; }
        @Override public void setBalance(Wei value) { this.balance = value; }
        @Override public Bytes getCode() { return code; }
        @Override public Hash getCodeHash() {
            return Hash.wrap(Bytes32.wrap(org.hyperledger.besu.crypto.Hash.keccak256(code)));
        }
        @Override public void setCode(Bytes code) { this.code = code; }
        @Override public UInt256 getStorageValue(UInt256 key) { return storage.getOrDefault(key, UInt256.ZERO); }
        @Override public UInt256 getOriginalStorageValue(UInt256 key) { return getStorageValue(key); }
        @Override public Map<UInt256, UInt256> getUpdatedStorage() { return storage; }
        @Override public void setStorageValue(UInt256 key, UInt256 value) { storage.put(key, value); }
        @Override public void clearStorage() { storage.clear(); }

        @Override public boolean isEmpty() {
             return nonce == 0 && balance.isZero() && code.isEmpty() && storage.isEmpty();
        }

        @Override
        public java.util.NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(Bytes32 startKeyHash, int limit) {
             return new java.util.TreeMap<>();
        }
    }
}
