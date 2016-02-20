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
                    HttpHeaders.IfNoneMatch).any { it in request.headers }) {

                response.interceptSend { obj, next ->
                    if (obj is HasVersions) {
                        for (version in obj.versions) {
                            val result = when (version) {
                                is EntityTagVersion -> checkEtag(version.etag)
                                is LastModifiedVersion -> checkLastModified(version.lastModified)
                                else -> ConditionalHeaderCheckResult.OK
                            }

                            if (result != ConditionalHeaderCheckResult.OK) {
                                response.status(result.statusCode)
                                return@interceptSend ApplicationCallResult.Handled
                            }
                        }
                    }

                    next(obj)
                }
            }

            requestNext()
        }
    }
}
