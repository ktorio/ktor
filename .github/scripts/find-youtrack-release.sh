#!/usr/bin/env bash
# Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.

set -euo pipefail

TARGET_RELEASE=$(jq -rn --arg name "$TARGET_RELEASE" '$name | gsub("^\\s+|\\s+$"; "")')
if [ -z "$TARGET_RELEASE" ]; then
    echo "::error title=Missing PR milestone::This PR has no milestone." \
        "Select a PR milestone or set the missing Target release values in YouTrack."
    exit 1
fi

# shellcheck disable=SC2016
FIELDS=$(curl -fsS --get \
    -H "Authorization: Bearer $YOUTRACK_TOKEN" \
    --data-urlencode 'fields=field(name),bundle(id)' \
    --data-urlencode '$top=100' \
    "$YOUTRACK_URL/api/admin/projects/KTOR/customFields")
BUNDLE_ID=$(jq -er '
    map(select(.field.name == "Target release"))[0].bundle.id
    // error("KTOR has no Target release enum bundle")
' <<<"$FIELDS")
VALUES_URL="$YOUTRACK_URL/api/admin/customFieldSettings/bundles/enum/$BUNDLE_ID/values"

SKIP=0
while true; do
    # shellcheck disable=SC2016
    VALUES=$(curl -fsS --get \
        -H "Authorization: Bearer $YOUTRACK_TOKEN" \
        --data-urlencode 'fields=id,name' \
        --data-urlencode '$top=100' \
        --data-urlencode "\$skip=$SKIP" \
        "$VALUES_URL")
    RELEASE_ID=$(jq -r --arg name "$TARGET_RELEASE" '
        def normalize: gsub("^\\s+|\\s+$"; "") | ascii_downcase;
        map(select((.name | normalize) == ($name | normalize)))[0].id // empty
    ' <<<"$VALUES")
    if [ -n "$RELEASE_ID" ]; then
        echo "id=$RELEASE_ID" >>"$GITHUB_OUTPUT"
        echo "Target release: $TARGET_RELEASE"
        exit 0
    fi
    VALUE_COUNT=$(jq length <<<"$VALUES")
    if [ "$VALUE_COUNT" -lt 100 ]; then
        echo "::error title=Unknown target release::Milestone '$TARGET_RELEASE' has no matching YouTrack Target release." \
            "Select a matching milestone or set the missing Target release values in YouTrack."
        exit 1
    fi
    SKIP=$((SKIP + 100))
done
