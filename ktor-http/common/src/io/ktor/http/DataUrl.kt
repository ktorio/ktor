/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

public class DataUrl(
    public val data: ByteArray,
    public val contentType: ContentType
)
