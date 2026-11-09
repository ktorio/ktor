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

check_issue_limit() {
    local issue_count
    issue_count=$(awk 'NF { count++ } END { print count+0 }' <<<"$1")
    if [ "$issue_count" -gt 15 ]; then
        echo "::error::PR selects $issue_count unique YouTrack issues. The limit is 15." \
            "Remove extra references or links."
        exit 1
    fi
}

ISSUE_CACHE='{}'
fetch_issue() {
    local issue_id="$1" response http_code
    BODY=$(jq -c --arg id "$issue_id" 'if has($id) then .[$id] else empty end' <<<"$ISSUE_CACHE")
    if [ -z "$BODY" ]; then
        response=$(curl -sS --get -w "\n%{http_code}" \
            -H "Accept: application/json" \
            -H "Authorization: Bearer $YOUTRACK_TOKEN" \
            --data-urlencode 'fields=customFields(name,value(name)),links(id,direction,linkType(name))' \
            "$YOUTRACK_URL/api/issues/$issue_id") || exit 1
        http_code=$(tail -1 <<<"$response")
        BODY=$(sed '$d' <<<"$response")

        if [ "$http_code" = "404" ]; then
            echo "::warning title=Unknown YouTrack issue::Issue $issue_id does not exist in YouTrack, skipping"
            BODY=null
        elif [ "$http_code" -lt 200 ] || [ "$http_code" -ge 300 ]; then
            echo "::error::Failed to get issue $issue_id (HTTP $http_code): $BODY"
            exit 1
        fi

        ISSUE_CACHE=$(jq -c --arg id "$issue_id" --argjson issue "$BODY" '.[$id] = $issue' <<<"$ISSUE_CACHE") \
            || exit 1
    fi
    [ "$BODY" != null ]
}

IDS=$(extract_ids "$PR_TITLE" "$PR_BODY")
check_issue_limit "$IDS"

# An edit must not undo state changes made during review.
if [ "$EVENT_ACTION" = "edited" ] && [ "$TARGET_STATE" != "Fixed" ]; then
    OLD_TITLE=$(jq -r --arg current "$PR_TITLE" 'if has("title") then .title.from else $current end' <<<"$CHANGES")
    OLD_BODY=$(jq -r --arg current "$PR_BODY" 'if has("body") then .body.from // "" else $current end' <<<"$CHANGES")
    OLD_IDS=$(extract_ids "$OLD_TITLE" "$OLD_BODY")
    IDS=$(grep -vxFf <(printf '%s\n' "$OLD_IDS") <<<"$IDS" || true)
fi

NEEDS_RELEASE=false
if [ "$TARGET_STATE" = "Fixed" ]; then
    ROOT_IDS="$IDS"
    for ISSUE_ID in $ROOT_IDS; do
        if ! fetch_issue "$ISSUE_ID"; then
            continue
        fi

        # Cause's INWARD direction means "also fixes". OUTWARD means "fixed by".
        LINK_IDS=$(jq -r '
            .links[]? | select(.linkType.name == "Cause" and .direction == "INWARD") | .id
        ' <<<"$BODY")
        for LINK_ID in $LINK_IDS; do
            SKIP=0
            while true; do
                # shellcheck disable=SC2016
                RESPONSE=$(curl -sS --get -w "\n%{http_code}" \
                    -H "Accept: application/json" \
                    -H "Authorization: Bearer $YOUTRACK_TOKEN" \
                    --data-urlencode 'fields=idReadable,project(shortName)' \
                    --data-urlencode '$top=100' \
                    --data-urlencode "\$skip=$SKIP" \
                    "$YOUTRACK_URL/api/issues/$ISSUE_ID/links/$LINK_ID/issues")
                HTTP_CODE=$(tail -1 <<<"$RESPONSE")
                BODY=$(sed '$d' <<<"$RESPONSE")
                if [ "$HTTP_CODE" -lt 200 ] || [ "$HTTP_CODE" -ge 300 ]; then
                    echo "::error::Failed to get 'also fixes' links for $ISSUE_ID (HTTP $HTTP_CODE): $BODY"
                    exit 1
                fi

                LINKED_IDS=$(jq -r '
                    .[] | select(.project.shortName == "KTOR") | .idReadable | select(test("^KTOR-[0-9]+$"))
                ' <<<"$BODY")
                IDS=$(printf '%s\n' "$IDS" "$LINKED_IDS" | awk 'NF && !seen[$0]++')
                check_issue_limit "$IDS"

                if [ "$(jq length <<<"$BODY")" -lt 100 ]; then
                    break
                fi
                SKIP=$((SKIP + 100))
            done
        done
    done

    IDS_TO_FIX=""
    for ISSUE_ID in $IDS; do
        if ! fetch_issue "$ISSUE_ID"; then
            continue
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

IDS=$(xargs <<<"$IDS")
echo "ids=$IDS" >>"$GITHUB_OUTPUT"
echo "needs_release=$NEEDS_RELEASE" >>"$GITHUB_OUTPUT"
echo "Issues to move to $TARGET_STATE: ${IDS:-none}"
