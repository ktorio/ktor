# Ktor Throughput Benchmark & Profiling Guide

## Benchmark Module

**Location:** `ktor-throughput-benchmark/`

End-to-end throughput benchmarks for Ktor client and server engines. Measures requests/sec, throughput, and latency percentiles.

## Gradle Tasks

```bash
# Run Netty + Apache5 throughput benchmark
./gradlew :ktor-throughput-benchmark:profileNettyApache

# Run big file transfer benchmark (100MB default)
./gradlew :ktor-throughput-benchmark:runBigFile

# Run benchmark tests
./gradlew :ktor-throughput-benchmark:test
```

## Configuration (System Properties)

| Property | Default | Description |
|----------|---------|-------------|
| `benchmark.warmup.seconds` | 10 | Warmup duration before measurement |
| `benchmark.duration.seconds` | 30 (throughput) / 60 (big file) | Measurement duration |
| `benchmark.concurrency` | `availableProcessors * 4` | Number of concurrent connections |
| `benchmark.payload.bytes` | 32768 (32KB) | Payload size for throughput benchmark |
| `benchmark.filesize.mb` | 100 | File size for big file transfer benchmark |

Example with custom config:
```bash
./gradlew :ktor-throughput-benchmark:profileNettyApache \
  -Dbenchmark.warmup.seconds=5 \
  -Dbenchmark.duration.seconds=60 \
  -Dbenchmark.concurrency=128 \
  -Dbenchmark.payload.bytes=1024
```

## Profiling Scripts

### Automated Profiling (async-profiler)

```bash
# CPU profiling (default) — runs Netty+Apache5 benchmark with async-profiler attached
python ktor-throughput-benchmark/scripts/profile_benchmark.py

# Memory allocation profiling
python ktor-throughput-benchmark/scripts/profile_benchmark.py --alloc

# Big file transfer profiling
python ktor-throughput-benchmark/scripts/profile_benchmark.py --bigfile

# Custom durations
python ktor-throughput-benchmark/scripts/profile_benchmark.py --warmup 10 --duration 60 --profile-duration 50
```

### Hotspot Analysis

```bash
# Analyze default profile output (method-level hotspots)
python ktor-throughput-benchmark/scripts/analyze_hotspots.py

# Analyze specific file
python ktor-throughput-benchmark/scripts/analyze_hotspots.py build/profile-cpu.collapsed

# Class-level aggregation
python ktor-throughput-benchmark/scripts/analyze_hotspots.py --by-class

# Package-level aggregation
python ktor-throughput-benchmark/scripts/analyze_hotspots.py --by-package

# Filter to Ktor packages only
python ktor-throughput-benchmark/scripts/analyze_hotspots.py --filter io/ktor

# Exclude JDK internals
python ktor-throughput-benchmark/scripts/analyze_hotspots.py --exclude java/,sun/,jdk/
```

### Manual async-profiler

```bash
# CPU profiling (attach to running JVM)
asprof -d 30 -f profile.html <PID>

# Allocation profiling
asprof -d 30 -e alloc -f alloc-profile.html <PID>
```

## Key Performance-Sensitive Files

| File | Area | Optimization |
|------|------|-------------|
| `ktor-utils/common/src/io/ktor/util/CaseInsensitiveMap.kt` | Header map | Open-addressing hash table, no wrapper objects |
| `ktor-utils/common/src/io/ktor/util/StringValues.kt` | Header/parameter values | Parallel arrays, zero-alloc forEach |
| `ktor-utils/jvm/src/io/ktor/util/date/DateJvm.kt` | GMTDate factory | Civil-from-days algorithm, no Calendar allocation |
| `ktor-http/common/src/io/ktor/http/HttpProtocolVersion.kt` | Protocol version parsing | Cached constants for common versions |
| `ktor-server/ktor-server-netty/` | Netty call handling | Skip redundant cancel, lazy routing trace |

## Baseline Numbers

### Throughput (Netty + Apache5, 256B payload, 64 concurrency)

| Scenario | Requests/sec | Throughput | p50 | p99 |
|----------|-------------|------------|-----|-----|
| Download | 22,713 | 5.55 MB/s | 2.2ms | 9.2ms |
| Upload | 23,529 | 5.74 MB/s | 2.2ms | 9.0ms |

### Big File Transfer (100MB, 4 concurrency)

| Metric | Value |
|--------|-------|
| Throughput | 1,332 MB/s (10.66 Gbps) |

### GC Impact

| Metric | Before | After |
|--------|--------|-------|
| GC % of CPU | ~10.5% | 0.70% |

**GC overhead reduced by ~93%.** Server remains I/O bound.

## Workflow

1. **Profile first** — identify hotspot with `profile_benchmark.py`
2. **Analyze** — use `analyze_hotspots.py` to find top allocators/CPU consumers
3. **Implement fix** — target the specific hotspot
4. **Re-profile** — verify the fix reduced the hotspot
5. **Check history** — compare against `ktor-throughput-benchmark/BENCHMARK_HISTORY.md`
