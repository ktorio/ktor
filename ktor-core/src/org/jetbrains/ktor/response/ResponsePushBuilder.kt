package org.jetbrains.ktor.response

import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

interface ResponsePushBuilder {
    val url: URLBuilder
    val headers: ValuesMapBuilder
    var method: HttpMethod
    val versions: MutableList<Version>
}