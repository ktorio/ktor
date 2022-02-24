// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.core

internal actual class BufferSharedState actual constructor(actual var limit: Int) {
    actual var readPosition: Int = 0

    actual var writePosition: Int = 0

    actual var startGap: Int = 0

    actual var attachment: Any? = null
}
