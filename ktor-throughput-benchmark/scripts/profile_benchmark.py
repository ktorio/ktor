#!/usr/bin/env python3
# ABOUTME: Async-profiler automation script for Ktor throughput benchmarks.
# ABOUTME: Profiles Netty+Apache5 by default, outputs collapsed stack format for flame graphs.

"""
Profile Ktor throughput benchmarks with async-profiler.

Usage:
    # CPU profiling (default)
    python scripts/profile_benchmark.py

    # Memory allocation profiling
    python scripts/profile_benchmark.py --alloc

    # Custom durations
    python scripts/profile_benchmark.py --warmup 10 --duration 60 --profile-duration 50

    # Big file transfer benchmark (test max throughput)
    python scripts/profile_benchmark.py --bigfile
    python scripts/profile_benchmark.py --bigfile --filesize 500

    # Generate flame graph SVG (requires flamegraph.pl)
    python scripts/profile_benchmark.py | flamegraph.pl > profile.svg
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path


def find_project_root() -> Path:
    """Find the Ktor project root by looking for settings.gradle.kts."""
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / "settings.gradle.kts").exists():
            return current
        current = current.parent
    raise RuntimeError("Could not find project root (settings.gradle.kts)")


def find_async_profiler() -> str:
    """Find async-profiler executable."""
    # Check common locations
    candidates = [
        "asprof",  # In PATH (Homebrew, etc.)
        "/usr/local/bin/asprof",
        "/opt/homebrew/bin/asprof",
        os.path.expanduser("~/async-profiler/bin/asprof"),
        os.path.expanduser("~/tools/async-profiler/bin/asprof"),
    ]

    for candidate in candidates:
        if shutil.which(candidate):
            return candidate

    # Check ASYNC_PROFILER_HOME env var
    home = os.environ.get("ASYNC_PROFILER_HOME")
    if home:
        asprof = os.path.join(home, "bin", "asprof")
        if os.path.isfile(asprof):
            return asprof

    raise RuntimeError(
        "async-profiler not found. Install via:\n"
        "  macOS: brew install async-profiler\n"
        "  Linux: https://github.com/async-profiler/async-profiler/releases\n"
        "  Or set ASYNC_PROFILER_HOME environment variable"
    )


def find_benchmark_pid(timeout: int = 30) -> int:
    """Wait for and find the benchmark JVM process."""
    print("Waiting for benchmark JVM to start...", file=sys.stderr)

    start_time = time.time()
    while time.time() - start_time < timeout:
        try:
            result = subprocess.run(
                ["jps", "-l"],
                capture_output=True,
                text=True,
                check=True
            )

            for line in result.stdout.strip().split("\n"):
                # Look for Gradle worker running our benchmark
                if "GradleWorkerMain" in line or "ProfileNettyApache" in line or "ProfileBigFile" in line:
                    # Verify it's actually running our benchmark by checking the process
                    pid = int(line.split()[0])

                    # Double-check with ps to see command line
                    ps_result = subprocess.run(
                        ["ps", "-p", str(pid), "-o", "args="],
                        capture_output=True,
                        text=True
                    )
                    if "throughput-benchmark" in ps_result.stdout or "GradleWorkerMain" in ps_result.stdout:
                        print(f"Found benchmark process: PID {pid}", file=sys.stderr)
                        return pid

        except subprocess.CalledProcessError:
            pass

        time.sleep(0.5)

    raise RuntimeError(f"Benchmark JVM not found within {timeout}s")


def run_profiler(
    pid: int,
    asprof: str,
    duration: int,
    event: str,
    output_file: Path
) -> None:
    """Run async-profiler and output collapsed stacks."""
    print(f"Profiling PID {pid} for {duration}s (event: {event})...", file=sys.stderr)

    # Use collapsed stack output format for flame graph compatibility
    cmd = [
        asprof,
        "-d", str(duration),
        "-e", event,
        "-o", "collapsed",  # Text format: collapsed stacks
        "-f", str(output_file),
        str(pid)
    ]

    print(f"Running: {' '.join(cmd)}", file=sys.stderr)

    try:
        subprocess.run(cmd, check=True)
    except subprocess.CalledProcessError as e:
        raise RuntimeError(f"async-profiler failed: {e}")


def main():
    parser = argparse.ArgumentParser(
        description="Profile Ktor throughput benchmarks with async-profiler",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # CPU profiling (default)
  python scripts/profile_benchmark.py

  # Memory allocation profiling
  python scripts/profile_benchmark.py --alloc

  # Big file transfer benchmark (measures max throughput)
  python scripts/profile_benchmark.py --bigfile
  python scripts/profile_benchmark.py --bigfile --filesize 500 --duration 60

  # Generate SVG flame graph
  python scripts/profile_benchmark.py > profile.txt
  cat profile.txt | flamegraph.pl > profile.svg

  # Or pipe directly
  python scripts/profile_benchmark.py 2>/dev/null | flamegraph.pl > profile.svg
"""
    )

    parser.add_argument(
        "--alloc",
        action="store_true",
        help="Profile memory allocations instead of CPU"
    )
    parser.add_argument(
        "--wall",
        action="store_true",
        help="Profile wall-clock time (includes I/O waits, locks, etc.)"
    )
    parser.add_argument(
        "--warmup",
        type=int,
        default=10,
        help="Benchmark warmup duration in seconds (default: 10)"
    )
    parser.add_argument(
        "--duration",
        type=int,
        default=30,
        help="Benchmark measurement duration in seconds (default: 30)"
    )
    parser.add_argument(
        "--profile-duration",
        type=int,
        default=None,
        help="Profiling duration in seconds (default: duration - 5)"
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="Output file path (default: stdout)"
    )
    parser.add_argument(
        "--payload",
        type=int,
        default=None,
        help="Payload size in bytes (default: 32768)"
    )
    parser.add_argument(
        "--concurrency",
        type=int,
        default=None,
        help="Number of concurrent coroutines (default: availableProcessors * 4)"
    )
    parser.add_argument(
        "--bigfile",
        action="store_true",
        help="Run big file transfer benchmark (tests max throughput)"
    )
    parser.add_argument(
        "--filesize",
        type=int,
        default=None,
        help="File size in MB for big file benchmark (default: 100)"
    )
    parser.add_argument(
        "--use-memory",
        action="store_true",
        help="Use in-memory ByteArray instead of file for big file benchmark"
    )

    args = parser.parse_args()

    # Calculate profile duration (leave buffer for startup/shutdown)
    profile_duration = args.profile_duration or max(args.duration - 5, 5)

    # Find tools and paths
    project_root = find_project_root()
    asprof = find_async_profiler()

    print(f"Project root: {project_root}", file=sys.stderr)
    print(f"async-profiler: {asprof}", file=sys.stderr)

    # Determine event type
    if args.alloc:
        event = "alloc"
    elif args.wall:
        event = "wall"
    else:
        event = "cpu"

    # Create temp output file
    output_file = Path(args.output) if args.output else project_root / "build" / f"profile-{event}.collapsed"
    output_file.parent.mkdir(parents=True, exist_ok=True)

    # Start the benchmark in background
    if args.bigfile:
        gradle_task = ":ktor-throughput-benchmark:runBigFile"
        gradle_cmd = [
            str(project_root / "gradlew"),
            gradle_task,
            f"-Dbenchmark.warmup.seconds={args.warmup}",
            f"-Dbenchmark.duration.seconds={args.duration}",
        ]
        if args.filesize is not None:
            gradle_cmd.append(f"-Dbenchmark.filesize.mb={args.filesize}")
        if args.concurrency is not None:
            gradle_cmd.append(f"-Dbenchmark.concurrency={args.concurrency}")
        if args.use_memory:
            gradle_cmd.append("-Dbenchmark.use.file=false")
    else:
        gradle_task = ":ktor-throughput-benchmark:profileNettyApache"
        gradle_cmd = [
            str(project_root / "gradlew"),
            gradle_task,
            f"-Dbenchmark.warmup.seconds={args.warmup}",
            f"-Dbenchmark.duration.seconds={args.duration}",
        ]
        if args.payload is not None:
            gradle_cmd.append(f"-Dbenchmark.payload.bytes={args.payload}")
        if args.concurrency is not None:
            gradle_cmd.append(f"-Dbenchmark.concurrency={args.concurrency}")

    print(f"Starting benchmark: {' '.join(gradle_cmd)}", file=sys.stderr)

    benchmark_proc = subprocess.Popen(
        gradle_cmd,
        cwd=project_root,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL
    )

    try:
        # Wait for JVM to start and find its PID
        time.sleep(3)  # Give Gradle time to start the worker
        pid = find_benchmark_pid(timeout=60)

        # Wait for warmup to complete before profiling
        print(f"Waiting {args.warmup}s for warmup to complete...", file=sys.stderr)
        time.sleep(args.warmup)

        # Run the profiler
        run_profiler(pid, asprof, profile_duration, event, output_file)

        # Output the results
        if output_file.exists():
            with open(output_file) as f:
                content = f.read()
                print(content)

            print(f"\nProfile saved to: {output_file}", file=sys.stderr)
            print(f"To generate flame graph SVG:", file=sys.stderr)
            print(f"  cat {output_file} | flamegraph.pl > profile.svg", file=sys.stderr)
        else:
            print("Warning: No profile output generated", file=sys.stderr)

    finally:
        # Clean up: terminate the benchmark if still running
        if benchmark_proc.poll() is None:
            print("Terminating benchmark process...", file=sys.stderr)
            benchmark_proc.terminate()
            try:
                benchmark_proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                benchmark_proc.kill()


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        print("\nInterrupted", file=sys.stderr)
        sys.exit(130)
