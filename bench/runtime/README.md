# Cross-runtime benchmark

`workloads.json` is the canonical corpus. Every adapter receives the exact same
`source` string and validates its displayed result before reporting timing.

```shell
scripts/run-runtime-benchmarks --profile smoke
scripts/run-runtime-benchmarks --profile standard --reference
```

The runner records process startup, first evaluation, warm-up samples,
convergence, steady-state throughput, peak resident memory when available, and
runtime payload size. Performance values are evidence for one machine, not CI
thresholds.
