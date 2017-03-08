package org.jetbrains.ktor.content

import org.jetbrains.ktor.util.*

fun ByteArrayContent(bytes: ByteArray) = object : FinalContent.ByteArrayContent() {
    override val headers: ValuesMap
        get() = ValuesMap.Empty

    override fun bytes(): ByteArray = bytes
}
