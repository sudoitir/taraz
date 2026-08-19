# Delta: k6-tests

## ADDED Requirements

### Requirement: Sustained-load benchmark scenario
The k6 suite SHALL include a benchmark scenario that applies sustained load at a constant arrival rate with a realistic mix of operations (credits, debits, transfers, balance reads across many accounts), and SHALL report measured throughput and latency percentiles. Correctness invariants hold during the benchmark: no failed checks, no 5xx responses.

#### Scenario: Benchmark completes under sustained load
- **WHEN** the benchmark scenario runs against a running service with its dependencies up
- **THEN** the configured arrival rate is sustained for the full duration, every check passes, and the summary reports measured http_req_duration percentiles and request rate

#### Scenario: Benchmark operations are distinguishable
- **WHEN** the benchmark runs
- **THEN** every operation uses a unique idempotency key and responses are checked (201 applied / 200 read), so a correctness regression fails the run instead of hiding in averages
