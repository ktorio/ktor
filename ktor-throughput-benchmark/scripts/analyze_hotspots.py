#!/usr/bin/env python3
# ABOUTME: Analyzes async-profiler collapsed stack output to identify CPU hotspots.
# ABOUTME: Provides method-level, class-level, and package-level aggregations.

"""
Hotspot Analysis for async-profiler collapsed stack output.

Usage:
    # Analyze default profile output
    python scripts/analyze_hotspots.py

    # Analyze specific file
    python scripts/analyze_hotspots.py build/profile-cpu.collapsed

    # Show top 30 hotspots
    python scripts/analyze_hotspots.py --top 30

    # Filter by package
    python scripts/analyze_hotspots.py --filter io/ktor

    # Show class-level aggregation
    python scripts/analyze_hotspots.py --by-class

    # Show package-level aggregation
    python scripts/analyze_hotspots.py --by-package

    # Exclude specific packages (e.g., JDK internals)
    python scripts/analyze_hotspots.py --exclude java/,sun/,jdk/
"""

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Tuple


def find_project_root() -> Path:
    """Find the Ktor project root by looking for settings.gradle.kts."""
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / "settings.gradle.kts").exists():
            return current
        current = current.parent
    raise RuntimeError("Could not find project root (settings.gradle.kts)")


def parse_collapsed_stacks(file_path: Path) -> List[Tuple[List[str], int]]:
    """Parse collapsed stack file into list of (stack_frames, sample_count)."""
    stacks = []
    with open(file_path) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue

            # Format: frame1;frame2;...;frameN count
            parts = line.rsplit(" ", 1)
            if len(parts) != 2:
                continue

            stack_str, count_str = parts
            try:
                count = int(count_str)
            except ValueError:
                continue

            frames = stack_str.split(";")
            stacks.append((frames, count))

    return stacks


def aggregate_by_method(
    stacks: List[Tuple[List[str], int]],
    filter_pattern: str = None,
    exclude_patterns: List[str] = None
) -> Dict[str, int]:
    """Aggregate sample counts by method (self time)."""
    method_counts = defaultdict(int)

    for frames, count in stacks:
        if not frames:
            continue

        # The last frame is where time was spent (self time)
        method = frames[-1]

        # Apply filters
        if filter_pattern and filter_pattern not in method:
            continue

        if exclude_patterns:
            if any(pattern in method for pattern in exclude_patterns):
                continue

        method_counts[method] += count

    return dict(method_counts)


def aggregate_by_method_total(
    stacks: List[Tuple[List[str], int]],
    filter_pattern: str = None,
    exclude_patterns: List[str] = None
) -> Dict[str, int]:
    """Aggregate sample counts by method (total time - method appears anywhere in stack)."""
    method_counts = defaultdict(int)

    for frames, count in stacks:
        seen = set()
        for method in frames:
            # Apply filters
            if filter_pattern and filter_pattern not in method:
                continue

            if exclude_patterns:
                if any(pattern in method for pattern in exclude_patterns):
                    continue

            # Count each method only once per stack
            if method not in seen:
                method_counts[method] += count
                seen.add(method)

    return dict(method_counts)


def aggregate_by_class(
    stacks: List[Tuple[List[str], int]],
    filter_pattern: str = None,
    exclude_patterns: List[str] = None
) -> Dict[str, int]:
    """Aggregate sample counts by class."""
    class_counts = defaultdict(int)

    for frames, count in stacks:
        seen = set()
        for frame in frames:
            # Extract class name (everything before the last dot/method)
            if "." in frame:
                class_name = frame.rsplit(".", 1)[0]
            else:
                class_name = frame

            # Apply filters
            if filter_pattern and filter_pattern not in class_name:
                continue

            if exclude_patterns:
                if any(pattern in class_name for pattern in exclude_patterns):
                    continue

            if class_name not in seen:
                class_counts[class_name] += count
                seen.add(class_name)

    return dict(class_counts)


def aggregate_by_package(
    stacks: List[Tuple[List[str], int]],
    filter_pattern: str = None,
    exclude_patterns: List[str] = None,
    depth: int = 3
) -> Dict[str, int]:
    """Aggregate sample counts by package (first N path segments)."""
    package_counts = defaultdict(int)

    for frames, count in stacks:
        seen = set()
        for frame in frames:
            # Extract package (first N segments)
            parts = frame.split("/")
            if len(parts) > depth:
                package = "/".join(parts[:depth])
            else:
                package = "/".join(parts[:-1]) if len(parts) > 1 else frame

            # Apply filters
            if filter_pattern and filter_pattern not in package:
                continue

            if exclude_patterns:
                if any(pattern in package for pattern in exclude_patterns):
                    continue

            if package not in seen:
                package_counts[package] += count
                seen.add(package)

    return dict(package_counts)


def format_percentage(count: int, total: int) -> str:
    """Format count as percentage of total."""
    if total == 0:
        return "0.00%"
    return f"{100.0 * count / total:.2f}%"


def print_hotspots(
    counts: Dict[str, int],
    total_samples: int,
    top_n: int,
    title: str
):
    """Print hotspot table."""
    sorted_counts = sorted(counts.items(), key=lambda x: x[1], reverse=True)[:top_n]

    if not sorted_counts:
        print(f"No hotspots found for: {title}")
        return

    # Calculate column widths
    max_name_len = max(len(name) for name, _ in sorted_counts)
    max_name_len = min(max_name_len, 100)  # Cap at 100 chars

    print(f"\n{'=' * 80}")
    print(f"  {title}")
    print(f"  Total samples: {total_samples}")
    print(f"{'=' * 80}")
    print(f"{'Samples':>10}  {'Percent':>8}  {'Method/Class/Package'}")
    print(f"{'-' * 10}  {'-' * 8}  {'-' * 60}")

    for name, count in sorted_counts:
        pct = format_percentage(count, total_samples)
        # Truncate long names
        display_name = name if len(name) <= 100 else f"...{name[-97:]}"
        print(f"{count:>10}  {pct:>8}  {display_name}")


def print_ktor_summary(
    stacks: List[Tuple[List[str], int]],
    total_samples: int
):
    """Print summary focused on Ktor-specific hotspots."""
    ktor_patterns = [
        ("io/ktor/server", "Ktor Server"),
        ("io/ktor/client", "Ktor Client"),
        ("io/ktor/http", "Ktor HTTP"),
        ("io/ktor/utils", "Ktor Utils"),
        ("io/netty", "Netty"),
        ("kotlinx/coroutines", "Kotlinx Coroutines"),
        ("org/apache/hc", "Apache HttpClient"),
        ("java/", "JDK"),
        ("sun/", "JDK Internal"),
        ("kotlin/", "Kotlin Stdlib"),
    ]

    print(f"\n{'=' * 80}")
    print("  COMPONENT BREAKDOWN")
    print(f"  Total samples: {total_samples}")
    print(f"{'=' * 80}")
    print(f"{'Samples':>10}  {'Percent':>8}  {'Component'}")
    print(f"{'-' * 10}  {'-' * 8}  {'-' * 30}")

    component_counts = defaultdict(int)

    for frames, count in stacks:
        categorized = False
        for frame in frames:
            for pattern, label in ktor_patterns:
                if pattern in frame:
                    component_counts[label] += count
                    categorized = True
                    break
            if categorized:
                break

        if not categorized:
            component_counts["Other"] += count

    sorted_components = sorted(component_counts.items(), key=lambda x: x[1], reverse=True)

    for label, count in sorted_components:
        pct = format_percentage(count, total_samples)
        print(f"{count:>10}  {pct:>8}  {label}")


def main():
    parser = argparse.ArgumentParser(
        description="Analyze async-profiler hotspots from collapsed stack output",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )

    parser.add_argument(
        "input",
        nargs="?",
        default=None,
        help="Input collapsed stack file (default: build/profile-cpu.collapsed)"
    )
    parser.add_argument(
        "--top", "-n",
        type=int,
        default=20,
        help="Show top N hotspots (default: 20)"
    )
    parser.add_argument(
        "--filter", "-f",
        type=str,
        default=None,
        help="Filter to methods containing this pattern"
    )
    parser.add_argument(
        "--exclude", "-e",
        type=str,
        default=None,
        help="Comma-separated patterns to exclude (e.g., 'java/,sun/')"
    )
    parser.add_argument(
        "--by-class",
        action="store_true",
        help="Aggregate by class instead of method"
    )
    parser.add_argument(
        "--by-package",
        action="store_true",
        help="Aggregate by package instead of method"
    )
    parser.add_argument(
        "--package-depth",
        type=int,
        default=3,
        help="Package aggregation depth (default: 3)"
    )
    parser.add_argument(
        "--total-time",
        action="store_true",
        help="Show total time (method anywhere in stack) instead of self time"
    )
    parser.add_argument(
        "--ktor-only",
        action="store_true",
        help="Show only Ktor-related methods"
    )
    parser.add_argument(
        "--summary",
        action="store_true",
        help="Show component breakdown summary"
    )

    args = parser.parse_args()

    # Find input file
    if args.input:
        input_path = Path(args.input)
    else:
        project_root = find_project_root()
        input_path = project_root / "build" / "profile-cpu.collapsed"

    if not input_path.exists():
        print(f"Error: Input file not found: {input_path}", file=sys.stderr)
        print("Run the profiler first: python scripts/profile_benchmark.py", file=sys.stderr)
        sys.exit(1)

    # Parse input
    print(f"Reading: {input_path}", file=sys.stderr)
    stacks = parse_collapsed_stacks(input_path)
    total_samples = sum(count for _, count in stacks)

    print(f"Parsed {len(stacks)} unique stacks, {total_samples} total samples", file=sys.stderr)

    # Parse exclude patterns
    exclude_patterns = None
    if args.exclude:
        exclude_patterns = [p.strip() for p in args.exclude.split(",")]

    # Apply ktor-only filter
    filter_pattern = args.filter
    if args.ktor_only:
        filter_pattern = "io/ktor"

    # Show summary if requested
    if args.summary:
        print_ktor_summary(stacks, total_samples)

    # Determine aggregation mode
    if args.by_package:
        counts = aggregate_by_package(
            stacks, filter_pattern, exclude_patterns, args.package_depth
        )
        title = "TOP HOTSPOTS BY PACKAGE"
    elif args.by_class:
        counts = aggregate_by_class(stacks, filter_pattern, exclude_patterns)
        title = "TOP HOTSPOTS BY CLASS"
    elif args.total_time:
        counts = aggregate_by_method_total(stacks, filter_pattern, exclude_patterns)
        title = "TOP HOTSPOTS BY METHOD (TOTAL TIME)"
    else:
        counts = aggregate_by_method(stacks, filter_pattern, exclude_patterns)
        title = "TOP HOTSPOTS BY METHOD (SELF TIME)"

    if filter_pattern:
        title += f" [filter: {filter_pattern}]"
    if exclude_patterns:
        title += f" [excluding: {','.join(exclude_patterns)}]"

    print_hotspots(counts, total_samples, args.top, title)


if __name__ == "__main__":
    main()
