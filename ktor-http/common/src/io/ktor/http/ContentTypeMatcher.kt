/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Interface for any objects that can match a [ContentType].
 */
public interface ContentTypeMatcher {
    /**
     * Checks if `this` type matches a [contentType] type.
     */
    public fun contains(contentType: ContentType): Boolean
}
