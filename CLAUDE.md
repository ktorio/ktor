# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands
- Build: `./gradlew assemble`
- Run all JVM tests: `./gradlew jvmTest`
- Run specific test: `./gradlew :module-name:jvmTest --tests "fully.qualified.TestClassName"`
- Run all tests: `./gradlew allTests`
- Code coverage: `./gradlew koverHtmlReport`
- Linting: `./gradlew lintKotlin`
- Format code: `./gradlew formatKotlin`

## Code Style Guidelines
- Follow Kotlin [official style guide](https://kotlinlang.org/docs/coding-conventions.html)
- Use star imports for `io.ktor.*` packages (configured in .editorconfig)
- Max line length: 120 characters
- Indent: 4 spaces (2 for JSON, YAML)
- Include copyright header in new files
- Document all public APIs including parameters, return types, and exceptions
- Mark internal APIs with `@InternalAPI` annotation
- Prefix commit messages with KTOR-{NUM} (YouTrack issue)
- Write commit messages in present tense, imperative mood

## Development Practices
- Always use Test-Driven Development (TDD) approach
- Write tests before implementing new features or fixing issues
- Ensure all code is covered by appropriate tests
- Follow test naming pattern: `DescribeWhatIsBeingTested`
- Always verify compilation with `./gradlew assemble` before submitting changes
- Run relevant tests with `./gradlew jvmTest` to verify functionality
- Run `./gradlew lintKotlin` and fix all linting issues before giving control back to the user
- Use `./gradlew formatKotlin` to automatically fix formatting issues
- Run `./gradlew apiDump` after making API changes to update API signature files
- CRITICAL: Never return control to the user without ensuring code compiles, tests pass, and ALL linting issues are fixed
- Start with JVM-only implementation and tests unless the user specifically requests otherwise
- Focus on core functionality first before expanding to other platforms (JS, Native)

## Project Requirements
- JDK 21 required for building
- Multiplatform project (JVM, JS, Native)
- Tests organized by platform in module's test directories
- Error handling follows Kotlin conventions with specific Ktor exceptions

## API Compatibility
- Binary compatibility is enforced using the binary-compatibility-validator plugin
- All public API changes must be tracked in the `/api/` directories
- Run `./gradlew apiDump` to update all API signature files after making API changes
- Module-specific API dumps: `./gradlew :module-name:apiDump`
- Platform-specific dumps: `./gradlew jvmApiDump` or `./gradlew klibApiDump`
- API changes must be intentional and well-documented
- Breaking changes are only allowed in major version releases