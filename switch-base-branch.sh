#!/bin/bash
#
# Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
#

# Interactive script to switch branch base between 'main' and 'release/*'
# Usage: ./switch-base-branch.sh --help

set -euo pipefail

# ============================================================================
# Colors and Logging
# ============================================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
BGREEN='\033[1;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
GRAY='\033[0;90m'
NC='\033[0m' # No Color

log() {
    echo -e "$1${NC}"
}

# ============================================================================
# Global Variables
# ============================================================================
DRY_RUN=false
MAIN_BRANCH="main"
RELEASE_BRANCH=""
CURRENT_BRANCH=""
BASE_REMOTE=""       # Remote name or URL of the canonical repository, never a fork
BASE_LABEL=""        # Human-readable form of BASE_REMOTE
BASE_REFS="refs/switch-base" # Base branches fetched when the canonical repo has no remote
MAIN_REF=""          # Ref of 'main' in the canonical repository
RELEASE_REF=""       # Ref of 'release/*' in the canonical repository
TARGET_REF=""        # Ref the branch is rebased onto
UPSTREAM_REPO=""     # 'owner/repo' of the canonical repository, used for gh calls
PUSH_REMOTE="origin" # Remote name or URL the feature branch belongs to
PUSH_LABEL=""        # Human-readable form of PUSH_REMOTE
PUSH_ARGS=()         # 'git push' arguments, built once the remote state is known
PUSHED=false
REMOTE_HEAD=""       # Remote branch SHA before the rewrite, used for --force-with-lease
CURRENT_BASE=""
TARGET_BASE=""
MERGE_BASE=""
COMMITS_COUNT=0
BACKUP_BRANCH=""
GH_AVAILABLE=false
PR_NUMBER=""
PR_BASE=""
PR_URL=""
PR_HEAD_REPO=""

# ============================================================================
# Functions
# ============================================================================

# Print and execute git if not in dry-run mode
# Only for state-changing git commands
git_exec() {
    # Filter out --quiet flag from display
    local display_args=()
    for arg; do [[ "$arg" != "--quiet" ]] && display_args+=("$arg"); done

    # Always print executed git commands for transparency
    log "${GRAY}\$ git ${display_args[*]}"
    if [[ "$DRY_RUN" = true ]]; then
        return 0 # Always succeed in dry-run mode
    else
        git "$@"
        return $? # Return actual exit code
    fi
}

# Print and execute gh if not in dry-run mode
# Only for state-changing gh commands
gh_exec() {
    log "${GRAY}\$ gh $*"
    if [[ "$DRY_RUN" = true ]]; then
        return 0 # Always succeed in dry-run mode
    else
        gh "$@"
        return $? # Return actual exit code
    fi
}

# Prompt for confirmation, answering with the given default in dry-run mode
confirm() {
    local prompt="$1 (y/N):" default="${2:-y}"
    if [[ "$DRY_RUN" = true ]]; then
        log "$prompt ${GRAY}$default (dry-run)"
        if [[ "$default" = "y" ]]; then
            return 0
        else
            return 1
        fi
    fi

    read -p "$prompt " -n 1 -r
    echo
    [[ $REPLY =~ ^[Yy]$ ]]
}

# Print the URL of a remote name, or the argument itself when it is already a URL
remote_url() {
    git remote get-url "$1" 2> /dev/null || echo "$1"
}

# Print 'owner/repo' for a remote name or URL, nothing if it can't be determined
repo_slug() {
    local url="$1"
    if [[ -z "$url" ]]; then return 0; fi

    url=$(remote_url "$url")
    url=${url%.git}
    url=${url#*github.com} # Drop scheme and host
    url=${url#:}
    while [[ "$url" == /* ]]; do url=${url#/}; done

    if [[ "$url" =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]]; then
        echo "$url"
    fi
}

# Print the name of the remote pointing to 'owner/repo', nothing if there is none
remote_for_repo() {
    local target="$1" name
    if [[ -z "$target" ]]; then return 0; fi

    for name in $(git remote); do
        if [[ "$(repo_slug "$name")" = "$target" ]]; then
            echo "$name"
            return 0
        fi
    done
}

# Succeed when both remote names or URLs point at the same repository
same_repository() {
    local slug_a slug_b
    slug_a=$(repo_slug "$1")
    slug_b=$(repo_slug "$2")

    if [[ -n "$slug_a" ]] && [[ -n "$slug_b" ]]; then
        [[ "$slug_a" = "$slug_b" ]]
    else
        [[ "$(remote_url "$1")" = "$(remote_url "$2")" ]]
    fi
}

# Build a clone URL for 'owner/repo', matching the protocol used by 'origin'
clone_url() {
    local origin_url
    origin_url=$(git remote get-url origin 2> /dev/null || true)

    if [[ "$origin_url" = http* ]]; then
        echo "https://github.com/$1.git"
    else
        echo "git@github.com:$1.git"
    fi
}

check_preconditions() {
    # Get current branch name
    CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

    # Read major version from VERSION file
    local major_version
    read -r -d "." major_version < "VERSION"
    RELEASE_BRANCH="release/${major_version}.x"

    if [ "$CURRENT_BRANCH" = "HEAD" ]; then
        log "${RED}✗ You are in detached HEAD state (no branch checked out)"
        echo "  Please checkout a branch first."
        exit 1
    fi

    if [[ "$CURRENT_BRANCH" = "$MAIN_BRANCH" ]] || [[ "$CURRENT_BRANCH" = "$RELEASE_BRANCH" ]]; then
        log "${RED}✗ You are currently on '$CURRENT_BRANCH' branch"
        echo "  Please checkout a feature branch first."
        exit 1
    fi

    if ! git diff-index --quiet HEAD --; then
        log "${RED}✗ You have uncommitted changes"
        echo "  Please commit or stash your changes first."
        exit 1
    fi
}

detect_gh() {
    if command -v gh > /dev/null 2>&1 && gh auth status > /dev/null 2>&1; then
        GH_AVAILABLE=true
    fi
}

# Locate the canonical repository: base branches must never be read from a fork,
# only the feature branch itself is pushed back to the fork it belongs to
detect_base_source() {
    if git remote | grep -q "^upstream$"; then
        BASE_REMOTE="upstream"
    else
        BASE_REMOTE="origin"
    fi
    UPSTREAM_REPO=$(repo_slug "$BASE_REMOTE")
    BASE_LABEL="$BASE_REMOTE"

    # 'upstream' is the canonical repository by convention, no need to check it
    if [[ "$BASE_REMOTE" = "upstream" ]] || [[ "$GH_AVAILABLE" = false ]] || [[ -z "$UPSTREAM_REPO" ]]; then
        return 0
    fi

    local parent
    parent=$(gh repo view "$UPSTREAM_REPO" --json parent \
        --jq '.parent | if . == null then "" else .owner.login + "/" + .name end' 2> /dev/null || true)
    if [[ -z "$parent" ]]; then
        return 0
    fi

    # 'origin' is a fork: its copies of the base branches may be outdated
    UPSTREAM_REPO="$parent"
    local parent_remote
    parent_remote=$(remote_for_repo "$parent")
    if [[ -n "$parent_remote" ]]; then
        BASE_REMOTE="$parent_remote"
        BASE_LABEL="$parent_remote"
    else
        BASE_REMOTE=$(clone_url "$parent")
        BASE_LABEL="$parent"
    fi
}

fetch_base_branches() {
    if git remote get-url "$BASE_REMOTE" > /dev/null 2>&1; then
        git fetch "$BASE_REMOTE" --quiet
        MAIN_REF="$BASE_REMOTE/$MAIN_BRANCH"
        RELEASE_REF="$BASE_REMOTE/$RELEASE_BRANCH"
        return 0
    fi

    # The canonical repository has no remote configured, so its base branches are
    # fetched into private refs instead of relying on the fork's copies of them
    log "${GRAY}Reading base branches from '$UPSTREAM_REPO', which has no remote configured"
    MAIN_REF="$BASE_REFS/$MAIN_BRANCH"
    RELEASE_REF="$BASE_REFS/$RELEASE_BRANCH"
    git fetch --quiet "$BASE_REMOTE" \
        "+refs/heads/$MAIN_BRANCH:$MAIN_REF" \
        "+refs/heads/$RELEASE_BRANCH:$RELEASE_REF"
}

detect_current_and_target_base() {
    fetch_base_branches
    log "[1/4] ${GREEN}✓${NC} Analyzed branches"

    # Find merge-base with both main and release
    local main_merge_base
    main_merge_base=$(git merge-base "$CURRENT_BRANCH" "$MAIN_REF")
    local release_merge_base
    release_merge_base=$(git merge-base "$CURRENT_BRANCH" "$RELEASE_REF")

    # Count commits from each merge-base to current branch
    local main_commits
    main_commits=$(git rev-list --count "$main_merge_base..$CURRENT_BRANCH")
    local release_commits
    release_commits=$(git rev-list --count "$release_merge_base..$CURRENT_BRANCH")

    # Determine current base by checking which merge-base is more recent
    # (the one that is reachable from current branch with fewer unique commits)
    if [ "$main_commits" -le "$release_commits" ]; then
        CURRENT_BASE=$MAIN_BRANCH
        TARGET_BASE=$RELEASE_BRANCH
        TARGET_REF=$RELEASE_REF
        MERGE_BASE=$main_merge_base
        COMMITS_COUNT=$main_commits
    else
        CURRENT_BASE=$RELEASE_BRANCH
        TARGET_BASE=$MAIN_BRANCH
        TARGET_REF=$MAIN_REF
        MERGE_BASE=$release_merge_base
        COMMITS_COUNT=$release_commits
    fi
}

# The branch may live in a fork: branch config wins, then remote.pushDefault, then origin.
# 'gh pr checkout' configures the fork URL per branch, so this also works for maintainers
# reviewing a contributor's PR.
detect_push_remote() {
    PUSH_REMOTE=$(git config --get "branch.$CURRENT_BRANCH.pushRemote" \
        || git config --get "remote.pushDefault" \
        || git config --get "branch.$CURRENT_BRANCH.remote" \
        || echo "origin")
}

# Forks are only visible through the GitHub CLI. Without it, a fork branch would be
# rebased onto the fork's own base branches and pushed to the canonical repository,
# so ask instead of guessing, and refuse to continue.
check_fork_support() {
    if [[ "$GH_AVAILABLE" = true ]]; then
        return 0
    fi

    # Distinct repositories for bases and pushes mean the fork is already configured
    if ! same_repository "$BASE_REMOTE" "$PUSH_REMOTE"; then
        return 0
    fi

    local repo
    repo=$(repo_slug "$PUSH_REMOTE")
    repo=${repo:-$PUSH_REMOTE}

    log "${YELLOW}⚠ GitHub CLI is unavailable, so forks and their pull requests can't be detected"
    echo "  Base branches would be read from '$repo', and '$CURRENT_BRANCH' pushed back to it."
    # Answering 'yes' aborts, so dry-run keeps previewing the rest of the run instead
    if ! confirm "Is '$repo' a fork, or does '$CURRENT_BRANCH' come from one?" "n"; then
        echo
        return 0
    fi

    echo
    log "${RED}✗ Rebasing a branch of a fork requires the GitHub CLI"
    echo "  Without it the branch is rebased onto the base branches of the fork instead of"
    echo "  the ones of the canonical repository, pushed to the wrong repository, and the PR"
    echo "  keeps targeting its previous base branch."
    echo
    echo "  Authenticate the GitHub CLI and run the script again:"
    log "    ${YELLOW}gh auth login"
    echo "  Or point the script at both repositories yourself, and switch the base branch of"
    echo "  the PR on GitHub manually:"
    log "    ${YELLOW}git remote add upstream <canonical-repository-url>"
    log "    ${YELLOW}git config branch.$CURRENT_BRANCH.pushRemote <fork-remote-or-url>"
    exit 1
}

detect_pull_request() {
    if [[ "$GH_AVAILABLE" = false ]] || [[ -z "$UPSTREAM_REPO" ]]; then
        return 0
    fi

    local prs
    prs=$(gh pr list --repo "$UPSTREAM_REPO" --head "$CURRENT_BRANCH" --state open --limit 10 \
        --json number,baseRefName,url,headRepository \
        --jq '.[] | [.number, .baseRefName, .url, .headRepository.nameWithOwner] | @tsv' 2> /dev/null || true)
    if [[ -z "$prs" ]]; then
        return 0
    fi

    # The same branch name may be used in several forks: pick the one we push to
    local selected="$prs" tab=$'\t'
    if [[ $(printf '%s\n' "$prs" | wc -l) -gt 1 ]]; then
        selected=$(printf '%s\n' "$prs" | grep -F -- "$tab$(repo_slug "$PUSH_REMOTE")" || true)
    fi

    if [[ -z "$selected" ]] || [[ $(printf '%s\n' "$selected" | wc -l) -gt 1 ]]; then
        log "${YELLOW}⚠ Several open PRs use the head branch '$CURRENT_BRANCH':"
        printf '%s\n' "$prs" | cut -f3 | sed 's/^/    • /'
        echo "  The PR base branch won't be switched automatically."
        echo
        return 0
    fi

    IFS=$'\t' read -r PR_NUMBER PR_BASE PR_URL PR_HEAD_REPO <<< "$selected"
}

# Point PUSH_REMOTE at the repository the PR branch actually lives in
resolve_push_target() {
    if [[ -n "$PR_HEAD_REPO" ]] && [[ "$(repo_slug "$PUSH_REMOTE")" != "$PR_HEAD_REPO" ]]; then
        local fork_remote
        fork_remote=$(remote_for_repo "$PR_HEAD_REPO")
        if [[ -n "$fork_remote" ]]; then
            PUSH_REMOTE="$fork_remote"
        else
            PUSH_REMOTE=$(clone_url "$PR_HEAD_REPO")
        fi
    fi

    # A URL works for push and pull, but a named remote gives more readable output
    local slug
    slug=$(repo_slug "$PUSH_REMOTE")
    if ! git remote get-url "$PUSH_REMOTE" > /dev/null 2>&1; then
        local named
        named=$(remote_for_repo "$slug")
        if [[ -n "$named" ]]; then
            PUSH_REMOTE="$named"
        fi
    fi

    if git remote get-url "$PUSH_REMOTE" > /dev/null 2>&1; then
        PUSH_LABEL="$PUSH_REMOTE${slug:+ ($slug)}"
    else
        PUSH_LABEL="${slug:-$PUSH_REMOTE}"
    fi
}

show_rebase_preview() {
    echo
    log "  ${BLUE}📊 Rebase plan:"
    log "    Branch: ${YELLOW}$CURRENT_BRANCH${NC}"
    log "    Base:   ${YELLOW}$CURRENT_BASE${NC} → ${YELLOW}$TARGET_BASE${NC} (from ${YELLOW}$BASE_LABEL${NC})"
    log "    Push:   ${YELLOW}$PUSH_LABEL"
    if [[ -n "$PR_NUMBER" ]]; then
        log "    PR:     ${YELLOW}#$PR_NUMBER${NC} $PR_URL"
    fi
    echo
    log "  ${BLUE}📝 $COMMITS_COUNT commit(s) will be moved:"
    git log "$MERGE_BASE..$CURRENT_BRANCH" --oneline --color | sed 's/^/    • /'
    echo

    if ! confirm "Continue?"; then
        echo "Cancelled."
        return 1
    fi
    echo
    return 0
}

create_backup() {
    BACKUP_BRANCH="backup/${CURRENT_BRANCH}"
    git_exec branch -f "$BACKUP_BRANCH" "$CURRENT_BRANCH"
    log "[2/4] ${GREEN}✓${NC} Created backup: ${YELLOW}$BACKUP_BRANCH"
}

sync_with_remote() {
    # Remember the remote state to push with a lease later on
    REMOTE_HEAD=$(git ls-remote "$PUSH_REMOTE" "refs/heads/$CURRENT_BRANCH" 2> /dev/null | cut -f1 || true)
    build_push_args

    if [[ -z "$REMOTE_HEAD" ]]; then
        log "[3/4] ${GREEN}✓${NC} Sync skipped (no remote branch)"
        return 0
    fi

    if git_exec pull --rebase "$PUSH_REMOTE" "$CURRENT_BRANCH" --quiet; then
        log "[3/4] ${GREEN}✓${NC} Synced with ${YELLOW}$PUSH_LABEL"
    else
        echo
        log "${RED}✗ Failed to sync with $PUSH_LABEL"
        echo "  Resolve conflicts and run the script again."
        log "💡 Restore: ${YELLOW}git reset --hard $BACKUP_BRANCH"
        exit 1
    fi
}

# An explicit lease keeps the check working for remotes given as a URL,
# which have no remote-tracking refs to compare against
build_push_args() {
    PUSH_ARGS=(push "$PUSH_REMOTE" "$CURRENT_BRANCH")
    if [[ -n "$REMOTE_HEAD" ]]; then
        PUSH_ARGS+=("--force-with-lease=refs/heads/$CURRENT_BRANCH:$REMOTE_HEAD")
    fi
}

rebase_to_target() {
    log "[4/4] ${BLUE}➜${NC} Rebasing onto ${YELLOW}$TARGET_BASE${NC} of ${YELLOW}$BASE_LABEL${NC}..."
    git_exec rebase --quiet --onto "$TARGET_REF" "$MERGE_BASE" "$CURRENT_BRANCH"
    return $?
}

wait_for_conflict_resolution() {
    # Wait for user to resolve conflicts
    read -p "Press Enter after completing the rebase (or Ctrl+C to exit)..."

    # Check if rebase was successful
    if git rev-parse --git-dir > /dev/null 2>&1 && ! git rev-parse --verify REBASE_HEAD > /dev/null 2>&1; then
        echo
        log "${GREEN}✓ Rebase completed"
        post_rebase_actions
    else
        echo
        log "${RED}✗ Rebase still in progress or failed"
        echo "  Complete or abort manually."
        exit 1
    fi
}

push_branch() {
    if git_exec "${PUSH_ARGS[@]}" --quiet; then
        PUSHED=true
        return 0
    fi
    return 1
}

# Retarget the PR on GitHub, so it doesn't include commits of the previous base
switch_pr_base() {
    if [[ -z "$PR_NUMBER" ]]; then
        if [[ "$GH_AVAILABLE" = false ]]; then
            log "💡 Switch the PR base branch to ${YELLOW}$TARGET_BASE${NC} on GitHub"
        fi
        return 0
    fi

    if [[ "$PR_BASE" = "$TARGET_BASE" ]]; then
        log "${GRAY}PR #$PR_NUMBER already targets '$TARGET_BASE'"
        return 0
    fi

    local command="gh pr edit $PR_NUMBER --repo $UPSTREAM_REPO --base $TARGET_BASE"
    if [[ "$PUSHED" = false ]]; then
        log "💡 Switch PR base after pushing: ${YELLOW}$command"
        return 0
    fi

    if ! confirm "Switch base of PR #$PR_NUMBER to '$TARGET_BASE' on GitHub?"; then
        log "💡 Switch PR base: ${YELLOW}$command"
        return 0
    fi

    if gh_exec pr edit "$PR_NUMBER" --repo "$UPSTREAM_REPO" --base "$TARGET_BASE"; then
        log "${GREEN}✓ PR #$PR_NUMBER now targets '$TARGET_BASE'"
    else
        log "${RED}✗ Failed to switch the PR base branch"
        log "💡 Retry: ${YELLOW}$command"
    fi
}

post_rebase_actions() {
    log "${GREEN}✓ Rebased successfully"
    echo

    if confirm "Force-push to '$PUSH_LABEL' and delete backup?"; then
        if push_branch; then
            git_exec branch --quiet -D "$BACKUP_BRANCH"
            log "${GREEN}✓ Pushed and cleaned up"
        else
            echo
            log "${RED}✗ Failed to push to $PUSH_LABEL"
            echo "  Pushing to someone else's fork requires 'Allow edits by maintainers' on the PR."
            log "💡 Retry: ${YELLOW}git ${PUSH_ARGS[*]}"
            log "💡 Restore: ${YELLOW}git reset --hard $BACKUP_BRANCH"
        fi
    else
        log "💡 Push: ${YELLOW}git ${PUSH_ARGS[*]}"
        log "💡 Restore: ${YELLOW}git reset --hard $BACKUP_BRANCH"
    fi

    echo
    switch_pr_base

    echo
    log "${GREEN}✨ Done!"
}

print_help() {
    echo "Usage: $0 [OPTIONS]"
    echo
    echo "Options:"
    echo "  --dry-run    Show what would be done without making changes"
    echo "  -h, --help   Show this help message"
    echo
    echo "This script switches your branch base between 'main' and 'release/*'."
    echo "It will automatically detect the current base and offer to switch to the other."
    echo
    echo "Base branches are always read from the canonical repository: 'upstream' if it is"
    echo "configured, otherwise 'origin', or the parent repository when 'origin' is a fork."
    echo "The branch itself is pushed back to the repository it belongs to, so branches of"
    echo "PRs made from a fork are pushed to that fork instead of the canonical repository."
    echo
    echo "If the GitHub CLI ('gh') is installed and authenticated, the script also offers"
    echo "to switch the base branch of the open PR for the current branch. Forks can only be"
    echo "resolved through the CLI, so without it the script asks whether a fork is involved"
    echo "and stops when it is."
}

# ============================================================================
# Main Script
# ============================================================================

main() {
    # Parse arguments
    for arg in "$@"; do
        case $arg in
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            -h|--help)
                print_help
                exit 0
                ;;
            *)
                log "${RED}✗ Unknown option: $arg"
                echo "  Use --help for usage information."
                exit 1
                ;;
        esac
    done

    log "${BGREEN}🔄 Ktor Branch Base Switcher"
    if [ "$DRY_RUN" = true ]; then
        log "${GRAY}You're running in dry-run mode, git commands won't be executed."
        log "${GRAY}Commands that would be executed are shown with ${NC}\$${NC} ${GRAY}prefix."
    fi
    echo

    check_preconditions
    detect_gh
    detect_base_source
    detect_push_remote
    check_fork_support
    detect_current_and_target_base
    detect_pull_request
    resolve_push_target
    show_rebase_preview || exit 0
    create_backup
    sync_with_remote

    if rebase_to_target; then
        post_rebase_actions
    else
        wait_for_conflict_resolution
    fi
}

main "$@"
