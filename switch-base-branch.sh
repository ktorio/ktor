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
# Constants
# ============================================================================
MAIN_BRANCH="main"
CANONICAL_REPO="ktorio/ktor"
ORIGIN_REMOTE="origin"
UPSTREAM_REMOTE="upstream"

# ============================================================================
# Global Variables
# ============================================================================
DRY_RUN=false
RELEASE_BRANCH=""
CURRENT_BRANCH=""
BASE_REMOTE=""       # Remote pointing to the canonical repository
PUSH_REMOTE=""       # Remote name or URL the feature branch belongs to
PUSH_LABEL=""        # Human-readable form of PUSH_REMOTE
PUSH_ARGS=()         # 'git push' arguments, built once the remote state is known
CURRENT_BASE=""
TARGET_BASE=""
MERGE_BASE=""
COMMITS_COUNT=0
BACKUP_BRANCH=""
GH_AVAILABLE=false
PR_NUMBER=""
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
    fi
    git "$@"
}

# Print and execute gh if not in dry-run mode
# Only for state-changing gh commands
gh_exec() {
    log "${GRAY}\$ gh $*"
    if [[ "$DRY_RUN" = true ]]; then
        return 0 # Always succeed in dry-run mode
    fi
    gh "$@"
}

# Prompt for confirmation, auto-accepting in dry-run mode
confirm() {
    local prompt="$1 (y/N):"
    if [[ "$DRY_RUN" = true ]]; then
        log "$prompt ${GRAY}y (dry-run)"
        return 0
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
        echo "$url" | tr '[:upper:]' '[:lower:]'
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

# Build a clone URL for 'owner/repo', matching the protocol used by 'origin'
clone_url() {
    local origin_url
    origin_url=$(git remote get-url "$ORIGIN_REMOTE" 2> /dev/null || true)

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

# Base branches must always be read from the canonical Ktor repository.
resolve_base_remote() {
    BASE_REMOTE=$(remote_for_repo "$CANONICAL_REPO")
    if [[ -z "$BASE_REMOTE" ]]; then
        local canonical_url
        canonical_url=$(clone_url "$CANONICAL_REPO")

        if [[ "$DRY_RUN" = true ]]; then
            log "${RED}✗ Dry-run requires a remote pointing to '$CANONICAL_REPO'"
            echo "  Configure it and rerun:"
            log "    ${YELLOW}git remote add $UPSTREAM_REMOTE $canonical_url"
            log "    ${YELLOW}$0 --dry-run"
            exit 1
        fi

        if ! confirm "Add '$CANONICAL_REPO' as the '$UPSTREAM_REMOTE' remote?"; then
            log "${RED}✗ A remote for '$CANONICAL_REPO' is required"
            log "💡 Configure it: ${YELLOW}git remote add $UPSTREAM_REMOTE $canonical_url"
            exit 1
        fi

        git_exec remote add "$UPSTREAM_REMOTE" "$canonical_url"
        BASE_REMOTE="$UPSTREAM_REMOTE"
    fi

    git fetch "$BASE_REMOTE" --quiet
}

detect_pull_request() {
    if [[ "$GH_AVAILABLE" = false ]]; then
        return 0
    fi

    local pr
    if ! pr=$(gh pr list \
        --repo "$CANONICAL_REPO" \
        --head "$CURRENT_BRANCH" \
        --state open \
        --limit 1 \
        --json 'number,url,headRepository' \
        --jq '.[] | [.number, .url, (.headRepository.nameWithOwner // "" | ascii_downcase)] | @tsv' \
        2> /dev/null)
    then
        log "${YELLOW}⚠ Failed to look up the pull request"
        return 0
    fi

    if [[ -z "$pr" ]]; then
        return 0
    fi

    IFS=$'\t' read -r PR_NUMBER PR_URL PR_HEAD_REPO <<< "$pr"
}

# PR metadata identifies the repository of a PR branch. Without a PR, use the push
# remote configured for the branch and keep branches without one local.
resolve_push_remote() {
    if [[ -n "$PR_HEAD_REPO" ]]; then
        PUSH_REMOTE=$(remote_for_repo "$PR_HEAD_REPO")
        PUSH_REMOTE=${PUSH_REMOTE:-$(clone_url "$PR_HEAD_REPO")}
    else
        PUSH_REMOTE=$(git for-each-ref \
            --format='%(push:remotename)' \
            "refs/heads/$CURRENT_BRANCH")
    fi

    if [[ -z "$PUSH_REMOTE" ]]; then
        PUSH_LABEL="none (local branch)"
        return 0
    fi

    local push_repo
    push_repo=$(repo_slug "$PUSH_REMOTE")
    if git remote get-url "$PUSH_REMOTE" > /dev/null 2>&1; then
        PUSH_LABEL="$PUSH_REMOTE${push_repo:+ ($push_repo)}"
    else
        PUSH_LABEL="${push_repo:-$PUSH_REMOTE}"
    fi
}

detect_current_and_target_base() {
    local main_ref="$BASE_REMOTE/$MAIN_BRANCH"
    local release_ref="$BASE_REMOTE/$RELEASE_BRANCH"

    # Find merge-base with both main and release
    local main_merge_base
    main_merge_base=$(git merge-base "$CURRENT_BRANCH" "$main_ref")
    local release_merge_base
    release_merge_base=$(git merge-base "$CURRENT_BRANCH" "$release_ref")

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
        MERGE_BASE=$main_merge_base
        COMMITS_COUNT=$main_commits
    else
        CURRENT_BASE=$RELEASE_BRANCH
        TARGET_BASE=$MAIN_BRANCH
        MERGE_BASE=$release_merge_base
        COMMITS_COUNT=$release_commits
    fi
}

create_backup() {
    if [[ -n "$BACKUP_BRANCH" ]]; then
        return 0
    fi

    BACKUP_BRANCH="backup/${CURRENT_BRANCH}"
    git_exec branch -f "$BACKUP_BRANCH" "$CURRENT_BRANCH"
}

sync_with_remote_if_required() {
    if [[ -z "$PUSH_REMOTE" ]]; then
        PUSH_ARGS=()
        log "[1/4] ${GREEN}✓${NC} Remote synchronization not required"
        return 0
    fi

    local remote_head
    if ! remote_head=$(git ls-remote "$PUSH_REMOTE" "refs/heads/$CURRENT_BRANCH"); then
        log "${RED}✗ Failed to read '$CURRENT_BRANCH' from $PUSH_LABEL"
        exit 1
    fi
    remote_head=${remote_head%%$'\t'*}

    PUSH_ARGS=(push "$PUSH_REMOTE" "$CURRENT_BRANCH")
    if [[ -n "$remote_head" ]]; then
        PUSH_ARGS+=("--force-with-lease=refs/heads/$CURRENT_BRANCH:$remote_head")
    fi

    if [[ -z "$remote_head" ]] ||
        git merge-base --is-ancestor "$remote_head" "$CURRENT_BRANCH" > /dev/null 2>&1
    then
        log "[1/4] ${GREEN}✓${NC} Remote synchronization not required"
        return 0
    fi

    if ! confirm "Synchronize '$CURRENT_BRANCH' with '$PUSH_LABEL' and analyze the base switch?"; then
        return 1
    fi
    echo

    create_backup
    if git_exec -c rebase.updateRefs=false pull --rebase "$PUSH_REMOTE" "$CURRENT_BRANCH" --quiet; then
        log "[1/4] ${GREEN}✓${NC} Synced with ${YELLOW}$PUSH_LABEL"
    else
        echo
        log "${RED}✗ Failed to sync with $PUSH_LABEL"
        echo "  Resolve conflicts and run the script again."
        log "💡 Restore: ${YELLOW}git reset --hard $BACKUP_BRANCH"
        exit 1
    fi
}

analyze_rebase_plan() {
    detect_current_and_target_base
    log "[2/4] ${GREEN}✓${NC} Analyzed rebase plan"
}

show_rebase_preview() {
    echo
    log "  ${BLUE}📊 Rebase plan:"
    log "    Branch: ${YELLOW}$CURRENT_BRANCH${NC}"
    log "    Rebase: ${YELLOW}$BASE_REMOTE/$CURRENT_BASE${NC} → ${YELLOW}$BASE_REMOTE/$TARGET_BASE${NC}"
    log "    Push:   ${YELLOW}$PUSH_LABEL"
    if [[ -n "$PR_NUMBER" ]]; then
        log "    PR:     ${YELLOW}#$PR_NUMBER${NC} $PR_URL"
    fi
    echo
    log "  ${BLUE}📝 $COMMITS_COUNT commit(s) will be moved:"
    git log "$MERGE_BASE..$CURRENT_BRANCH" --oneline --color | sed 's/^/    • /'
    echo
}

confirm_rebase_plan() {
    local prompt="$1"

    show_rebase_preview
    if ! confirm "$prompt"; then
        echo "Cancelled."
        if [[ -n "$BACKUP_BRANCH" ]]; then
            log "💡 Restore: ${YELLOW}git reset --hard $BACKUP_BRANCH"
        fi
        exit 0
    fi
    echo
}

rebase_to_target() {
    log "[4/4] ${BLUE}➜${NC} Rebasing onto ${YELLOW}$TARGET_BASE${NC} of ${YELLOW}$BASE_REMOTE${NC}..."
    git_exec -c rebase.updateRefs=false rebase --quiet --onto "$BASE_REMOTE/$TARGET_BASE" "$MERGE_BASE" "$CURRENT_BRANCH"
}

wait_for_conflict_resolution() {
    local previous_head="$1" current_head
    read -p "Press Enter after completing the rebase (or Ctrl+C to exit)..."
    current_head=$(git rev-parse "$CURRENT_BRANCH")

    if git rev-parse --verify REBASE_HEAD > /dev/null 2>&1 ||
        [[ "$current_head" = "$previous_head" ]] ||
        ! git merge-base --is-ancestor "$BASE_REMOTE/$TARGET_BASE" "$CURRENT_BRANCH"
    then
        echo
        log "${RED}✗ Rebase was aborted or is still incomplete"
        echo "  Complete the rebase and run the script again."
        exit 1
    fi

    echo
    log "${GREEN}✓ Rebase completed"
}

# Retarget the PR on GitHub, so it doesn't include commits of the previous base
switch_pr_base() {
    local pushed="$1"

    if [[ -z "$PR_NUMBER" ]]; then
        if [[ "$GH_AVAILABLE" = false ]] && [[ -n "$PUSH_REMOTE" ]]; then
            log "💡 Switch the PR base branch to ${YELLOW}$TARGET_BASE${NC} on GitHub"
        fi
        return 0
    fi

    local gh_arguments=(
        pr edit "$PR_NUMBER"
        --repo "$CANONICAL_REPO"
        --base "$TARGET_BASE"
    )

    if [[ "$pushed" = false ]] ||
        ! confirm "Switch base of PR #$PR_NUMBER to '$TARGET_BASE' on GitHub?"
    then
        log "💡 Switch PR base manually: ${YELLOW}gh ${gh_arguments[*]}"
        return 0
    fi

    if gh_exec "${gh_arguments[@]}"; then
        log "${GREEN}✓ PR #$PR_NUMBER now targets '$TARGET_BASE'"
        return 0
    fi

    log "${RED}✗ Failed to switch the PR base branch"
    log "💡 Retry: ${YELLOW}gh ${gh_arguments[*]}"
    return 1
}

post_rebase_actions() {
    local pushed=false failed=false

    log "${GREEN}✓ Rebased successfully"
    echo

    if [[ ${#PUSH_ARGS[@]} -eq 0 ]]; then
        log "Push skipped: no push remote is configured for '$CURRENT_BRANCH'"
        log "💡 Publish it: ${YELLOW}git push --set-upstream <remote> $CURRENT_BRANCH"
        log "💡 Restore: ${YELLOW}git reset --hard $BACKUP_BRANCH"
    elif confirm "Force-push to '$PUSH_LABEL' and delete backup?"; then
        if git_exec "${PUSH_ARGS[@]}" --quiet; then
            pushed=true
            git_exec branch --quiet -D "$BACKUP_BRANCH"
            log "${GREEN}✓ Pushed and cleaned up"
        else
            failed=true
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
    if ! switch_pr_base "$pushed"; then
        failed=true
    fi

    echo
    if [[ "$failed" = true ]]; then
        log "${RED}✗ Done with errors"
        return 1
    fi
    log "${GREEN}✨ Done!"
}

print_help() {
    echo "Usage: $0 [OPTIONS]"
    echo
    echo "Options:"
    echo "  --dry-run    Show an up-to-date plan without rewriting or pushing the branch"
    echo "  -h, --help   Show this help message"
    echo
    echo "This script switches your branch base between 'main' and 'release/*'."
    echo "It will automatically detect the current base and offer to switch to the other."
    echo
    echo "Base branches are always read from the canonical Ktor repository. If no remote"
    echo "points to it, the script offers to add it as '$UPSTREAM_REMOTE'."
    echo "The branch itself is pushed back to the repository configured for it."
    echo
    echo "If the GitHub CLI ('gh') is installed and authenticated, the script also resolves"
    echo "the repository of an open PR branch and offers to switch the PR's base branch."
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
        log "${GRAY}You're running in dry-run mode. Base branches will still be fetched."
        log "${GRAY}Rebase, push, and PR update commands won't be executed."
        log "${GRAY}Commands that would be executed are shown with ${NC}\$${NC} ${GRAY}prefix."
    fi
    echo

    check_preconditions
    detect_gh
    resolve_base_remote
    detect_pull_request
    resolve_push_remote
    if ! sync_with_remote_if_required; then
        echo "Cancelled."
        exit 0
    fi
    analyze_rebase_plan
    confirm_rebase_plan "Continue?"
    create_backup
    log "[3/4] ${GREEN}✓${NC} Backup ready: ${YELLOW}$BACKUP_BRANCH"

    local pre_rebase_head
    pre_rebase_head=$(git rev-parse "$CURRENT_BRANCH")
    if ! rebase_to_target; then
        wait_for_conflict_resolution "$pre_rebase_head"
    fi
    post_rebase_actions
}

main "$@"
