package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.util.*

object ConditionalHeadersInterceptor : ApplicationFeature<Unit> {
    override val name = "ConditionalHeaders"
    override val key = AttributeKey<Unit>(name)

    override fun install(application: Application, configure: Unit.() -> Unit) {
        configure(Unit)

        application.intercept { requestNext ->
            if (listOf(HttpHeaders.IfModifiedSince,
                    HttpHeaders.IfUnmodifiedSince,
                    HttpHeaders.IfMatch,
                    HttpHeaders.IfNoneMatch).any { it in call.request.headers }) {

                call.interceptRespond { obj ->
                    if (obj is HasVersions) {
                        for (version in obj.versions) {
                            val result = when (version) {
                                is EntityTagVersion -> call.checkEtag(version.etag)
                                is LastModifiedVersion -> call.checkLastModified(version.lastModified)
                                else -> ConditionalHeaderCheckResult.OK
                            }

                            if (result != ConditionalHeaderCheckResult.OK) {
                                call.respondStatus(result.statusCode)
                            }
                        }
                    }
                }
            }
        }
    }
}
