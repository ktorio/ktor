package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.net.*

public fun ApplicationRequest.parseUrlEncodedParameters(): ValuesMap =
        URLDecoder.decode(body, this.contentCharset?.name() ?: "UTF-8")
                .split("&")
                .map { it.substringBefore("=") to it.substringAfter("=", "") }
                .fold(ValuesMap.Builder()) { builder, pair ->
                    builder.append(pair.first, pair.second)
                    builder
                }
                .build()

