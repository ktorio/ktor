---
name: fix-bug
description: "End-to-end bug fix workflow for the Ktor project. Accepts a GitHub issue (#NUMBER), YouTrack issue (KTOR-NUMBER), or YouTrack URL (https://youtrack.jetbrains.com/issue/KTOR-NUMBER). Fetches the issue, creates a failing reproducer test, commits on a new branch, implements the fix, validates, and opens a PR. Use this skill whenever the user wants to fix a bug from an issue tracker, mentions a KTOR issue number, or references a GitHub issue to fix."
user_invocable: true
---

# Fix Bug Skill

Automates the full bug-fix lifecycle for the Ktor project: understand issue, reproduce, fix, validate, and open a PR.

## Input Parsing

The user provides one of:
- `#123` — GitHub issue in `ktorio/ktor`
- `KTOR-9352` — YouTrack issue ID
- `https://youtrack.jetbrains.com/issue/KTOR-9352` or `https://youtrack.jetbrains.com/issue/KTOR-9352/some-slug` — YouTrack URL

Parse the input to determine the source:

1. **GitHub**: Extract the number, fetch via `gh issue view NUMBER --repo ktorio/ktor`
2. **YouTrack ID** (pattern `KTOR-\d+`): Fetch via the YouTrack REST API (see below)
3. **YouTrack URL**: Extract the `KTOR-XXXX` ID from the URL, then fetch via the YouTrack REST API (see below)

### Fetching YouTrack Issues via REST API

Use `curl` (via Bash tool) to call the YouTrack REST API. No authentication is needed for public JetBrains issues:

```bash
curl -s -H 'Accept: application/json' \
  'https://youtrack.jetbrains.com/api/issues/KTOR-XXXX?fields=idReadable,summary,description,customFields(name,value(name))'
```

This returns structured JSON with:
- `idReadable`: the issue ID (e.g., `KTOR-9352`)
- `summary`: issue title
- `description`: full issue description (Markdown)
- `customFields`: array of custom fields (Type, Priority, State, Subsystem, etc.)

Parse the JSON response to extract the information needed for the fix.

From the issue, extract:
- **Title and description** of the bug
- **Steps to reproduce** (if provided)
- **Expected vs actual behavior**
- **Affected module(s)** — identify which Ktor Gradle module is relevant
- **Issue ID** for branch naming and commit messages (e.g., `KTOR-9352` or `#123`)

## Step 1: Create Branch

Create a new branch from the current `main`:

```
git checkout main && git pull && git checkout -b claude/<issue-id>-<short-description>
```

Branch naming rules:
- For YouTrack issues: `claude/KTOR-9352-short-description`
- For GitHub issues: `claude/123-short-description`
- The short description is 2 words max, lowercase, hyphenated, derived from the issue title

## Step 2: Understand the Codebase Context

Before writing a reproducer, understand the affected area:
- Identify the Gradle module from the issue description or affected APIs
- Read existing tests in that module to understand test patterns and conventions
- Identify whether this needs a unit test or integration test based on the bug nature
- Remember: this project uses a **flattened Gradle structure** (e.g., `ktor-client/ktor-client-curl` → `:ktor-client-curl`)

Use the Explore agent or direct file reads to understand:
- The relevant source code where the bug likely lives
- Existing test infrastructure (test utilities, base classes, server setup patterns)
- How similar tests are structured in the same module

## Step 3: Write a Failing Reproducer Test

Write a test that demonstrates the bug as described in the issue. The goal is:
- **Minimal**: Only test the specific buggy behavior, nothing extra
- **Clear**: Test name in backticks should describe the bug (e.g., `` `KTOR-9352 request with empty body causes NPE` ``)
- **Failing**: The test MUST fail on the current codebase to confirm the bug exists

Place the test appropriately:
- Near existing tests for the same module/feature
- If no test suite exists for this area, create a new test class following the module's conventions
- For integration tests, place near other integration tests in the module
- For unit tests, place near other unit tests

After writing the test, run it to confirm it fails:
```bash
./gradlew :module-name:jvmTest --tests "fully.qualified.TestClassName.methodName"
```

If the test passes (bug is already fixed or test doesn't reproduce correctly):
- Re-read the issue carefully
- Adjust the test to more precisely match the reported scenario
- If the bug truly cannot be reproduced, inform the user and stop

## Step 4: Commit the Reproducer

Stage and commit the failing test:
```
git add <test-file>
git commit -m "<ISSUE-ID> Add failing test for <short bug description>

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

For GitHub issues, use `#NUMBER` in the commit message. For YouTrack, use `KTOR-XXXX`.

## Step 5: Plan and Implement the Fix

Analyze the bug based on what you learned from the issue and the reproducer test:
- Trace the code path that leads to the failure
- Identify the root cause
- Plan the minimal fix

Implement the fix directly — do not present the plan to the user for approval. Keep changes minimal and focused:
- Fix only the bug, do not refactor surrounding code
- Do not add features beyond what's needed
- Preserve existing comments and code style

## Step 6: Validate the Fix

Run the reproducer test to confirm it passes:
```bash
./gradlew :module-name:jvmTest --tests "fully.qualified.TestClassName.methodName"
```

Then run the full test suite for the affected module:
```bash
./gradlew :module-name:jvmTest
```

If any tests fail, investigate and fix. Do not skip or disable tests.

## Step 7: Format and Lint

```bash
./gradlew :module-name:formatKotlin
./gradlew :module-name:lintKotlin
```

Fix any lint issues before proceeding.

## Step 8: ABI Validation

If the fix changed any `public` or `protected` API (new methods, changed signatures, etc.):
```bash
./gradlew :module-name:checkLegacyAbi
```

If it fails, update the ABI dumps:
```bash
./gradlew :module-name:updateLegacyAbi
```

Stage the updated `.api` files along with the fix.

If no public API changed, skip this step.

## Step 9: Commit the Fix

```
git add <changed-files>
git commit -m "<ISSUE-ID> <Imperative description of the fix>

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

## Step 10: Push and Create PR

Push the branch and create a PR:

```bash
git push -u origin claude/<issue-id>-<short-description>
```

Create the PR targeting `main`:
```bash
gh pr create --title "<ISSUE-ID> <Short fix description>" --body "$(cat <<'EOF'
## Summary
- Fixes <link-to-issue>
- <1-2 bullet points describing the root cause and fix>

## Test plan
- Added failing reproducer test that validates the fix
- All existing tests in the module continue to pass

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

For YouTrack issues, link as: `https://youtrack.jetbrains.com/issue/KTOR-XXXX`
For GitHub issues, link as: `#NUMBER` (GitHub auto-links these)

Report the PR URL to the user when done.

## Step 11: Documentation Issue (if needed)

Assess whether the fix changes behavior that users rely on or that is described in the Ktor documentation. A documentation update is needed when:
- A public API signature changed (new parameter, changed default, new overload)
- Behavior that users observe changed (different error message, different default, different timing)
- A workaround that users might have adopted is no longer necessary
- A new feature or configuration option was added as part of the fix

If none of the above apply (e.g., an internal-only fix, a crash fix with no API change), skip this step.

When documentation is needed, create a GitHub issue in the `ktorio/ktor-documentation` repository:

```bash
gh issue create --repo ktorio/ktor-documentation \
  --title "Document behavior change: <ISSUE-ID> <short description>" \
  --body "$(cat <<'EOF'
## Context

PR: <link-to-the-PR-created-in-step-10>
Issue: <link-to-the-original-issue>

## What changed

<1-3 sentences explaining what behavior changed and why>

## What should be documented

<Describe specifically what a technical writer should add or update in the docs.
Include the affected API, module, and any relevant configuration options.>

## Suggested code snippet

```kotlin
// Include a short usage example if relevant, showing the correct new usage
```

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Report the documentation issue URL to the user alongside the PR URL.
