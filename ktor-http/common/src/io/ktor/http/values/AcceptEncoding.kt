/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.values

public object AcceptEncoding {

    public const val GZIP: String = "gzip"
    public const val COMPRESS: String = "compress"
    public const val DEFLATE: String = "deflate"
    public const val BR: String = "br"
    public const val ZSTD: String = "zstd"
    public const val IDENTITY: String = "identity"
    public const val ALL: String = "*"

}
