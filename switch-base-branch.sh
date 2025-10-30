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
REMOTE=""
PUSH_REMOTE="origin"
CURRENT_BASE=""
TARGET_BASE=""
MERGE_BASE=""
COMMITS_COUNT=0
BACKUP_BRANCH=""

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

# Prompt for confirmation, auto-accept in dry-run mode
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

# Determine remote name (upstream or origin)
detect_remotes() {
    if git remote | grep -q "^upstream$"; then
        REMOTE="upstream"
    else
        REMOTE="origin"
    fi
}

detect_current_and_target_base() {
    detect_remotes
    git fetch "$REMOTE" --quiet
    log "[1/4] ${GREEN}✓${NC} Analyzed branches"

    # Find merge-base with both main and release
    local main_merge_base
    main_merge_base=$(git merge-base "$CURRENT_BRANCH" "$REMOTE/$MAIN_BRANCH")
    local release_merge_base
    release_merge_base=$(git merge-base "$CURRENT_BRANCH" "$REMOTE/$RELEASE_BRANCH")

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

show_rebase_preview() {
    echo
    log "  ${BLUE}📊 Rebase plan:"
    log "    Branch: ${YELLOW}$CURRENT_BRANCH${NC}"
    log "    Base: ${YELLOW}$CURRENT_BASE${NC} → ${YELLOW}$TARGET_BASE"
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
    # Update current branch from remote if it exists
    if git show-ref --verify --quiet "refs/remotes/$PUSH_REMOTE/$CURRENT_BRANCH"; then
        if git_exec pull --rebase "$PUSH_REMOTE" "$CURRENT_BRANCH" --quiet; then
            log "[3/4] ${GREEN}✓${NC} Synced with remote"
        else
            echo
            log "${RED}✗ Failed to sync with remote"
            echo "  Resolve conflicts and run the script again."
            log "💡 Restore: ${YELLOW}git reset --hard $BACKUP_BRANCH"
            exit 1
        fi
    else
        log "[3/4] ${GREEN}✓${NC} Sync skipped (no remote branch)"
    fi
}

rebase_to_target() {
    log "[4/4] ${BLUE}➜${NC} Rebasing onto ${YELLOW}$REMOTE/$TARGET_BASE${NC}..."
    git_exec rebase --quiet --onto "$REMOTE/$TARGET_BASE" "$MERGE_BASE" "$CURRENT_BRANCH"
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

post_rebase_actions() {
    log "${GREEN}✓ Rebased successfully"
    echo

    if confirm "Force-push and delete backup?"; then
        git_exec push --quiet "$PUSH_REMOTE" "$CURRENT_BRANCH" --force-with-lease
        git_exec branch --quiet -D "$BACKUP_BRANCH"
        log "${GREEN}✓ Pushed and cleaned up"
    else
        log "💡 Push: ${YELLOW}git push $PUSH_REMOTE $CURRENT_BRANCH --force-with-lease"
        log "💡 Restore: ${YELLOW}git reset --hard $BACKUP_BRANCH"
    fi

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
    detect_current_and_target_base
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
