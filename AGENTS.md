# Agent Guidelines (Ktor)

This file is the primary playbook for agentic coding tools working in this repository.

## Critical Workflow Rule

**NEVER return control to the user without ensuring:**
1. Code compiles: `./gradlew :module-name:assemble`
2. Tests pass: `./gradlew :module-name:jvmTest` (and other platforms if touched)
3. Code is formatted and linting passes: run `./gradlew :module-name:formatKotlin`, then `./gradlew :module-name:lintKotlin`
4. ABI validated: `./gradlew :module-name:updateLegacyAbi` (if public/protected API changed)

Always prefer **module-specific** commands (`:module-name:task`) over project-wide commands.

## Project Requirements

- JDK 21 is required.
- Kotlin Multiplatform: JVM, JS, Native.
- Apple targets require Xcode + command line tools.
- Android targets are optional and enabled when Android SDK is available (`ANDROID_HOME` or `local.properties` `sdk.dir`).

## Project Layout

### Gradle module structure (flattened)
The project uses a **flattened Gradle structure** (see `settings.gradle.kts`). Nested directory paths do NOT translate to nested Gradle project names.
Example: `ktor-client/ktor-client-curl` → `:ktor-client-curl` (not `:ktor-client:ktor-client-curl`).

### Source set layout (platform-centric)
Kotlin Multiplatform sources use a flattened, platform-centric layout (do not re-introduce `src/<platform>Main/...`).

```text
common/src
jvm/src
jvm/resources
jvm/test
jvm/test-resources
...
```

Build logic enforces "no surprise source sets": manually registering extra source sets is rejected.
To add a new target/source set, add the directory or enable the target via `gradle.properties` (`target.<name>=true`).

## Build Commands

**Important**: Always prefer running tasks on the specific module you touched, not the entire project.

### Common tasks
```bash
./gradlew :module-name:assemble          # Build the module
```

Run tests:
```bash
./gradlew :module-name:jvmTest           # Run JVM tests for the module
./gradlew :module-name:allTests          # Run tests across all platforms for the module
./gradlew :module-name:jvmTest --tests "fully.qualified.TestClassName" # Run a specific test
./gradlew :module-name:jvmTest --tests "fully.qualified.TestClassName.methodName" # Run a specific test method
```

Linting:
```bash
./gradlew :module-name:formatKotlin      # Format the module
./gradlew :module-name:lintKotlin        # Lint the module
```

## Code Style Guidelines

### Formatting (EditorConfig is authoritative)
- Kotlin style: IntelliJ + `KOTLIN_OFFICIAL` (`.editorconfig`).
- Indent: 4 spaces (JSON/YAML: 2); max line length: 120; end of line: LF.
- Use `./gradlew :module-name:formatKotlin` rather than manual formatting.

### Imports
- Star imports are preferred for `io.ktor.*` (configured in `.editorconfig`).

### Naming
- Follow Kotlin conventions unless the surrounding package has a strong established pattern.
- Tests: prefer descriptive test names in backticks: `describe what is being tested`.

### Types and API design
- Prefer `internal` by default; keep the public surface intentional.
- Public API requires KDoc (parameters, return, and notable exceptions).
- Public-but-not-for-users APIs that cannot be `internal` should use `@InternalAPI`.
- Keep `@OptIn(...)` scope minimal.
- All types used as receivers in DSL should be annotated with `@KtorDsl` (for example, all plugin configs).

### Error handling
- `require(...)` for argument validation, `check(...)` for state validation, `error("...")` for unreachable states.
- Throw specific exceptions appropriate to the layer (IO parsing: `IOException`/`EOFException`; validation: Ktor exceptions like `BadRequestException`).
- Make error messages actionable; include the problematic value/context.

### Logging
- Prefer Ktor log helpers where present.
- Avoid noisy logs in hot paths.

### Documentation and comments
- Avoid redundant comments; add them only for tricky invariants or platform-specific behavior.
- Keep KDoc correct when behavior/signatures change.
- New source files must include the repository copyright header.

## Development Practices

- Prefer TDD where feasible: add/adjust tests, then implement.
- For multiplatform changes: start JVM-first unless the task requires another platform.
- Keep local-only build knobs (for example, developer `gradle.properties` overrides) out of commits.
- If asked to create commits: use imperative mood and include `KTOR-<NUM>` when there is a related YouTrack issue.

### Adding or removing modules

- Module names must start with `ktor-`.
- The project uses a flattened Gradle structure with custom DSL in `settings.gradle.kts` (see Project Layout section).
- When modules are added/removed or new targets enabled, run `./update-artifact-dumps.sh` to update published artifact lists in `gradle/artifacts`.
  Publishing will fail if these dumps are stale.

## Binary Compatibility and ABI Validation

Binary compatibility is **enforced** using Kotlin Gradle Plugin ABI validation.
All public API changes must be tracked in `/api/` directories within modules.

### Release branches and API policy

- Patch releases are maintained in `release/<major>.x` (for example, v3 uses `release/3.x`, v4 uses `release/4.x`).
- The next minor release is developed on `main`.
- Public API changes are allowed only for minor/major releases (typically on `main`), not for patch releases (on `release/<major>.x`).
- Breaking changes are **only allowed in major version releases**.
- The repo includes an interactive helper `./switch-base-branch.sh` for switching a feature branch base between `main` and `release/<major>.x`.
  For agents: use `--dry-run` to print the git commands, then run them after user approval.

### Validation commands
```bash
./gradlew :module-name:checkLegacyAbi    # Validate ABI compatibility
./gradlew :module-name:updateLegacyAbi   # Update ABI signature files after changes
```

### Rules
- **All** `public`/`protected` API changes require updating `api/*.api` dumps.
- API changes must be **intentional and well-documented**.
