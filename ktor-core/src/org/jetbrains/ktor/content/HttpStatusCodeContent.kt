package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

class HttpStatusCodeContent(private val value: HttpStatusCode) : FinalContent.NoContent() {
    override val status: HttpStatusCode
        get() = value

    override val headers: ValuesMap
        get() = ValuesMap.Empty
}