#!/usr/bin/env bash
# Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.

set -euo pipefail

# A leading title ID or an explicit closing reference selects an issue.
extract_ids() {
    local keyword='(^|[^[:alnum:]_])([Cc]lose[sd]?|[Ff]ix(e[sd])?|[Rr]esolve[sd]?)[*_]*:?[*_]*[[:space:]]+'
    local ref='[<[]?(https?://youtrack\.jetbrains\.com/issue/)?KTOR-[0-9]+(>|\]\([^)[:space:]]*\))?'
    local separator='(,[[:space:]]*(and[[:space:]]+)?|[[:space:]]+and[[:space:]]+)'
    {
        if [[ "$1" =~ ^(KTOR-[0-9]+):?[[:space:]]+[^[:space:]] ]]; then
            printf '%s\n' "${BASH_REMATCH[1]}"
        fi
        printf '%s\n' "$1" "$2" | grep -oiE "$keyword$ref($separator$ref)*" | grep -oE 'KTOR-[0-9]+' || true
    } | awk '!seen[$0]++'
}

IDS=$(extract_ids "$PR_TITLE" "$PR_BODY")

# The limit applies before edit and state filtering.
ISSUE_COUNT=$(awk 'NF { count++ } END { print count+0 }' <<<"$IDS")
if [ "$ISSUE_COUNT" -gt 15 ]; then
    echo "::error::PR references $ISSUE_COUNT unique YouTrack issues. The limit is 15. Remove extra references."
    exit 1
fi

# An edit must not undo state changes made during review.
if [ "$EVENT_ACTION" = "edited" ] && [ "$TARGET_STATE" != "Fixed" ]; then
    OLD_TITLE=$(jq -r --arg current "$PR_TITLE" 'if has("title") then .title.from else $current end' <<<"$CHANGES")
    OLD_BODY=$(jq -r --arg current "$PR_BODY" 'if has("body") then .body.from // "" else $current end' <<<"$CHANGES")
    OLD_IDS=$(extract_ids "$OLD_TITLE" "$OLD_BODY")
    IDS=$(grep -vxFf <(printf '%s\n' "$OLD_IDS") <<<"$IDS" || true)
fi

IDS=$(xargs <<<"$IDS")
NEEDS_RELEASE=false
if [ "$TARGET_STATE" = "Fixed" ]; then
    IDS_TO_FIX=""
    for ISSUE_ID in $IDS; do
        RESPONSE=$(curl -sS -w "\n%{http_code}" \
            -H "Accept: application/json" \
            -H "Authorization: Bearer $YOUTRACK_TOKEN" \
            "$YOUTRACK_URL/api/issues/$ISSUE_ID?fields=customFields(name,value(name))")
        HTTP_CODE=$(tail -1 <<<"$RESPONSE")
        BODY=$(sed '$d' <<<"$RESPONSE")

        if [ "$HTTP_CODE" = "404" ]; then
            echo "::warning title=Unknown YouTrack issue::Issue $ISSUE_ID does not exist in YouTrack, skipping"
            continue
        fi

        if [ "$HTTP_CODE" -lt 200 ] || [ "$HTTP_CODE" -ge 300 ]; then
            echo "::error::Failed to get issue $ISSUE_ID (HTTP $HTTP_CODE): $BODY"
            exit 1
        fi

        STATE=$(jq -r '.customFields[] | select(.name == "State") | .value.name // empty' <<<"$BODY")
        if [ "$STATE" = "Fixed" ]; then
            echo "$ISSUE_ID is already in state 'Fixed', skipping"
            continue
        fi
        IDS_TO_FIX="${IDS_TO_FIX:+$IDS_TO_FIX }$ISSUE_ID"

        ISSUE_TYPE=$(jq -r '.customFields[] | select(.name == "Type") | .value.name // empty' <<<"$BODY")
        case "$ISSUE_TYPE" in
            Bug|Feature|Task|"Security Problem"|Improvement)
                if jq -e 'any(.customFields[]; .name == "Target release" and .value == null)' \
                    <<<"$BODY" > /dev/null; then
                    NEEDS_RELEASE=true
                fi
                ;;
        esac
    done
    IDS="$IDS_TO_FIX"
fi

echo "ids=$IDS" >>"$GITHUB_OUTPUT"
echo "needs_release=$NEEDS_RELEASE" >>"$GITHUB_OUTPUT"
echo "Issues to move to $TARGET_STATE: ${IDS:-none}"
