/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import org.khronos.webgl.Int8Array

public expect fun ByteArray.toJsArray(): Int8Array
public expect fun Int8Array.toByteArray(): ByteArray
