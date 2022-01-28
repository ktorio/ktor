/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.webjars

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.cio.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import org.webjars.*
import java.io.*
import java.time.*

internal fun extractWebJar(path: String, knownWebJars: Set<String>, locator: WebJarAssetLocator): String {
    val firstDelimiter = if (path.startsWith("/")) 1 else 0
    val nextDelimiter = path.indexOf("/", 1)
    val webjar = if (nextDelimiter > -1) path.substring(firstDelimiter, nextDelimiter) else ""
    val partialPath = path.substring(nextDelimiter + 1)
    if (webjar !in knownWebJars) {
        throw IllegalArgumentException("jar $webjar not found")
    }
    return locator.getFullPath(webjar, partialPath)
}

internal class InputStreamContent(
    val input: InputStream,
    override val contentType: ContentType,
    lastModified: GMTDate
) : OutgoingContent.ReadChannelContent() {
    init {
        versions += LastModifiedVersion(lastModified)
    }

    override fun readFrom(): ByteReadChannel = input.toByteReadChannel(pool = KtorDefaultPool)
}
