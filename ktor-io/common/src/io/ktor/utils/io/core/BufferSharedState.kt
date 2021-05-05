/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.core

internal expect class BufferSharedState(limit: Int) {
    var readPosition: Int
    var writePosition: Int
    var startGap: Int
    var limit: Int
    var attachment: Any?
}
