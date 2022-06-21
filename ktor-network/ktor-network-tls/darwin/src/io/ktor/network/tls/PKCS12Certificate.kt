/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

public class PKCS12Certificate(
    public val path: String,
    public val password: (() -> CharArray)?
)
