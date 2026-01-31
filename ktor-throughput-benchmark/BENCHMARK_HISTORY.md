# Benchmark History

Track throughput and allocation metrics across optimizations.

## Big File Transfer Benchmark

Tests maximum data transfer throughput with large files.

```bash
# Run benchmark (100MB file, 4 concurrent connections)
./gradlew :ktor-throughput-benchmark:runBigFile

# Custom configuration
./gradlew :ktor-throughput-benchmark:runBigFile \
  -Dbenchmark.filesize.mb=500 \
  -Dbenchmark.duration.seconds=60 \
  -Dbenchmark.concurrency=8

# Profile with async-profiler
python ktor-throughput-benchmark/scripts/profile_benchmark.py --bigfile
```

### Current Results (LocalFileContent)
| Metric | Value |
|--------|-------|
| Throughput | 1332 MB/s (10.66 Gbps) |
| File Size | 100 MB |
| Concurrency | 4 |
| Transfers | 202 in 15s |

Localhost loopback typically supports 10-40 Gbps. Results within expected range.

---

## Current Test Configuration
- Server: Netty
- Client: Apache5
- Payload: 256 bytes
- Concurrency: 64
- Warmup: 10s
- Duration: 30s
- Machine: macOS (Apple Silicon)

## Final Results (All Server Optimizations Applied)

| Scenario | Requests/sec | Throughput | p50 | p99 |
|----------|-------------|------------|-----|-----|
| Download | 22,713 | 5.55 MB/s | 2.2ms | 9.2ms |
| Upload | 23,529 | 5.74 MB/s | 2.2ms | 9.0ms |

---

## GC Impact

| Metric | Before Optimizations | After Optimizations |
|--------|---------------------|---------------------|
| GC % of CPU | ~10.5% | **0.70%** |
| System I/O (kevent) | ~63% | ~63% |

**GC overhead reduced by ~93%.** Server remains I/O bound.

---

## Server Optimizations Applied

### 1. Skip Redundant Job Cancellation
- File: `NettyApplicationCall.kt`
- Change: Check `responseWriteJob.isCompleted` before calling `cancel()`
- Effect: Eliminates `JobCancellationException` allocation on happy path

### 2. Lazy Routing Trace Registration
- File: `RoutingRoot.kt`
- Change: Only register tracer if TRACE logging is enabled
- Effect: Eliminates `RoutingResolveTrace` allocations (~300 samples)

### 3. CaseInsensitiveMap Open-Addressing Hash Table
- File: `CaseInsensitiveMap.kt`
- Changes:
  - Open-addressing hash table with linear probing (no wrapper objects)
  - Insertion order tracking for correct iteration (like LinkedHashMap)
- Effect: Eliminates `CaseInsensitiveString` allocations (2,174 samples, 1.27%)

### 4. HttpProtocolVersion.parse Fast Path
- File: `HttpProtocolVersion.kt`
- Change: Return cached constants for HTTP/1.0, HTTP/1.1, HTTP/2.0, HTTP/3.0
- Effect: Eliminates `split()` List + iterator allocation (~530 samples)

### 5. StringValuesImpl Parallel Arrays
- File: `StringValues.kt`
- Changes:
  - Parallel arrays for keys/values (zero-allocation forEach)
  - Hash table with collision chaining for O(1) lookup
- Effect: Eliminates `forEach` iterator allocation (~300 samples)

### 6. GMTDate Zero-Allocation Timestamp Conversion
- File: `DateJvm.kt`
- Changes:
  - Compute date fields directly from epoch milliseconds using arithmetic
  - Uses civil_from_days algorithm instead of Calendar.getInstance()
- Effect: Eliminates `GregorianCalendar` + `Gregorian$Date` allocations (~864 samples)

### 7. Lazy Thread Safety Mode Optimization
- Files: `NettyConnectionPoint.kt`, `RequestCookies.kt`, `RoutingNode.kt`, `BaseApplicationRequest.kt`, `BaseApplicationResponse.kt`
- Changes:
  - Convert `scheme` to getter (no caching needed)
  - Use `LazyThreadSafetyMode.NONE` for request-scoped lazy properties
- Effect: Reduces `SynchronizedLazyImpl` allocation overhead (~40 samples)

---

## Allocation Summary

| Source | Before (samples) | After |
|--------|-----------------|-------|
| CaseInsensitiveString | 2,174 (1.27%) | Eliminated |
| GregorianCalendar (GMTDate) | ~864 | Eliminated |
| HttpProtocolVersion.parse iterator | ~530 | Eliminated |
| StringValuesImpl.forEach iterator | ~300 | Eliminated |
| RoutingResolveTrace | ~300 | Eliminated |
| JobCancellationException | ~2,755 | Eliminated |
| SynchronizedLazyImpl | ~477 | ~440 (reduced)

---

## Historical Data (32KB payload, 4 concurrency)

Earlier client-side optimization tests used different parameters:

| Version | Download req/s | Upload req/s | Download MB/s |
|---------|---------------|--------------|---------------|
| Baseline (bodyAsBytes) | 10,503 | 10,425 | 328 |
| SavedResponseBody | 14,043 | 12,364 | 439 |
| Streaming API | 16,449 | 15,031 | 514 |

**Note**: These numbers are not directly comparable to current results due to different payload size and concurrency settings.
