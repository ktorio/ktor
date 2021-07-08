/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

import io.ktor.http.*

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("MutableOriginConnectionPoint", "io.ktor.server.plugins.*")
)
public class MutableOriginConnectionPoint : RequestConnectionPoint {

    override var version: String = error("Moved to io.ktor.server.plugins")
    override var uri: String = error("Moved to io.ktor.server.plugins")
    override var method: HttpMethod = error("Moved to io.ktor.server.plugins")
    override var scheme: String = error("Moved to io.ktor.server.plugins")
    override var host: String = error("Moved to io.ktor.server.plugins")
    override var port: Int = error("Moved to io.ktor.server.plugins")
    override var remoteHost: String = error("Moved to io.ktor.server.plugins")
}
