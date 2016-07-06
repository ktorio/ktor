package org.jetbrains.ktor.features.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

object HttpsRedirectFeature : ApplicationFeature<ApplicationCallPipeline, Unit> {
    override val key = AttributeKey<Unit>("https-redirect-feature")

    override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit) {
        pipeline.intercept(ApplicationCallPipeline.Infrastructure) { call ->

        }
    }
}