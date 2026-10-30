/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import kotlin.concurrent.Volatile

/**
 * Base class for implementing [PipelineRequest]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.BaseApplicationRequest)
 */
public abstract class BaseApplicationRequest(final override val call: PipelineCall) : PipelineRequest {

    protected abstract val engineHeaders: Headers
    protected abstract val engineReceiveChannel: ByteReadChannel

    // Volatile rather than an atomicfu box, which on Native is two objects for every request
    @Volatile
    private var receiveChannel: ByteReadChannel? = null

    // Made when first read, as engineHeaders is set by the engine's constructor, after this one
    private var delegateHeaders: DelegateHeaders? = null

    final override val headers: Headers
        get() = delegateHeaders ?: DelegateHeaders(engineHeaders).also { delegateHeaders = it }

    private var callPipeline: ApplicationReceivePipeline? = null

    // This call's own copy of the application's receive pipeline, to intercept this call alone: made the first time
    // someone asks for it, as most calls never do. Until then a receive runs the application's, which is the same.
    final override val pipeline: ApplicationReceivePipeline
        get() = callPipeline ?: ApplicationReceivePipeline(call.application.developmentMode).apply {
            resetFrom(call.application.receivePipeline)
        }.also { callPipeline = it }

    /** The pipeline a receive runs: this call's own, once it was made, or else the application's. */
    internal val pipelineToRun: ApplicationReceivePipeline
        get() = callPipeline ?: call.application.receivePipeline

    final override fun receiveChannel(): ByteReadChannel {
        return receiveChannel ?: engineReceiveChannel
    }

    @InternalAPI
    final override fun setHeader(name: String, values: List<String>?) {
        (headers as DelegateHeaders).setHeader(name, values)
    }

    @InternalAPI
    final override fun setReceiveChannel(channel: ByteReadChannel) {
        receiveChannel = channel
    }
}

private class DelegateHeaders(private val original: Headers) : Headers {
    // Made by the first setHeader: most requests never change their headers, but every request reads them
    private var overridden: HeadersBuilder? = null
    private var removed: MutableSet<String>? = null

    override val caseInsensitiveName: Boolean = original.caseInsensitiveName

    fun setHeader(name: String, values: List<String>?) {
        if (values == null) {
            (removed ?: mutableSetOf<String>().also { removed = it }).add(name)
            overridden?.remove(name)
            return
        }

        (overridden ?: HeadersBuilder().also { overridden = it }).appendAll(name, values)
        removed?.remove(name)
    }

    override fun get(name: String): String? {
        if (removed?.contains(name) == true) {
            return null
        }

        val overridden = overridden
        if (overridden != null && overridden.contains(name)) {
            return overridden[name]
        }

        return original[name]
    }

    override fun contains(name: String): Boolean {
        if (removed?.contains(name) == true) {
            return false
        }

        return overridden?.contains(name) == true || original.contains(name)
    }

    override fun getAll(name: String): List<String>? {
        if (removed?.contains(name) == true) {
            return null
        }

        val overridden = overridden
        if (overridden != null && overridden.contains(name)) {
            return overridden.getAll(name)
        }

        return original.getAll(name)
    }

    override fun names(): Set<String> {
        val overridden = overridden
        val removed = removed
        if (overridden == null && removed == null) {
            return original.names()
        }

        return original.names() + (overridden?.names() ?: emptySet()) - (removed ?: emptySet())
    }

    override fun entries(): Set<Map.Entry<String, List<String>>> {
        val overridden = overridden
        val removed = removed
        if (overridden == null && removed == null) {
            return original.entries()
        }

        val entries = original.entries() + (overridden?.build()?.entries() ?: emptySet())
        return entries.filterNot { removed != null && it.key in removed }.toSet()
    }

    override fun isEmpty(): Boolean {
        return names().isEmpty()
    }
}
