/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.content.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlin.native.concurrent.*

@SharedImmutable
private val ProgressListenerAttributeKey = AttributeKey<ProgressListener>("ProgressListenerAttributeKey")

/**
 * Feature that provides observable progress for uploads and downloads
 */
public class BodyProgress internal constructor() {

    private fun handle(scope: HttpClient) {
        scope.requestPipeline.intercept(HttpRequestPipeline.State) { body ->
            if (body !is ObservableBody) {
                return@intercept
            }

            val originalBody = body.body
            context.body = originalBody
            context.attributes.put(ProgressListenerAttributeKey, body.listener)

            proceedWith(originalBody)
        }

        val observableContentPhase = PipelinePhase("ObservableContent")
        scope.requestPipeline.insertPhaseAfter(reference = HttpRequestPipeline.Render, phase = observableContentPhase)
        scope.requestPipeline.intercept(observableContentPhase) { content ->
            val listener = context.attributes.getOrNull(ProgressListenerAttributeKey) ?: return@intercept

            val observableContent = ObservableContent(content as OutgoingContent, context.executionContext, listener)
            proceedWith(observableContent)
        }

        scope.receivePipeline.intercept(HttpReceivePipeline.After) { response ->
            val listener = context.request.attributes.getOrNull(DownloadProgressListenerAttributeKey) ?: return@intercept
            val observableCall = context.withObservableDownload(listener)

            context.response = observableCall.response
            context.request = observableCall.request

            proceedWith(context.response)
        }
    }

    public companion object Feature : HttpClientFeature<Unit, BodyProgress> {
        override val key: AttributeKey<BodyProgress> = AttributeKey("BodyProgress")

        override fun prepare(block: Unit.() -> Unit): BodyProgress {
            return BodyProgress()
        }

        override fun install(feature: BodyProgress, scope: HttpClient) {
            feature.handle(scope)
        }
    }
}

@SharedImmutable
private val DownloadProgressListenerAttributeKey = AttributeKey<ProgressListener>("UploadProgressListenerAttributeKey")

/**
 * Registers listener to observe upload progress.
 */
public fun HttpRequestBuilder.onDownload(listener: ProgressListener?) {
    if (listener == null) {
        attributes.remove(DownloadProgressListenerAttributeKey)
    } else {
        attributes.put(DownloadProgressListenerAttributeKey, listener)
    }
}
